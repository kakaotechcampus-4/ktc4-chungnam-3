import { createHash, randomBytes } from 'node:crypto';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { existsSync } from 'node:fs';
import { mkdir, mkdtemp, writeFile, unlink, rmdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function buildObservationSql(eventId, installationId, placeId) {
  if (![eventId, installationId, placeId].every(value => uuid.test(value))) {
    throw new Error('Observation requires valid event, installation and place UUIDs.');
  }
  // Guest provider_user_id is SHA-256(installationId), not the raw installation UUID.
  const guestId = createHash('sha256').update(installationId.toLowerCase()).digest('hex');
  return `WITH matched AS (
    SELECT r.* FROM recall_execution r
    JOIN trigger t ON t.id = r.trigger_id
    JOIN member m ON m.id = t.member_id
    WHERE r.trigger_event_id = '${eventId}'::uuid
      AND t.place_id = '${placeId}'::uuid
      AND m.auth_provider = 'GUEST' AND m.provider_user_id = '${guestId}'
  ) SELECT json_build_object('executions', COALESCE((
    SELECT json_agg(json_build_object(
      'executionId', r.id, 'status', r.status, 'result', r.result,
      'agentVersion', r.agent_version, 'failureCode', r.failure_code,
      'proposalType', r.proposal_type, 'proposalPayload', r.proposal_payload,
      'notifications', COALESCE((SELECT json_agg(json_build_object(
        'notificationId', n.id, 'status', n.status, 'failureCode', n.failure_code,
        'sentAt', n.sent_at, 'openedAt', n.opened_at
      )) FROM notification n WHERE n.execution_id = r.id), '[]'::json)
    )) FROM matched r
  ), '[]'::json));`;
}

export function buildPolicyContextSql(eventId, installationId, placeId) {
  if (![eventId, installationId, placeId].every(value => uuid.test(value))) {
    throw new Error('Policy observation requires valid event, installation and place UUIDs.');
  }
  const guestId = createHash('sha256').update(installationId.toLowerCase()).digest('hex');
  return `WITH owner AS (
    SELECT id FROM member WHERE auth_provider = 'GUEST' AND provider_user_id = '${guestId}'
  ), owned_executions AS (
    SELECT r.* FROM recall_execution r JOIN trigger t ON t.id = r.trigger_id
    WHERE t.member_id IN (SELECT id FROM owner)
  ) SELECT json_build_object(
    'serverNow', CURRENT_TIMESTAMP,
    'memberExists', EXISTS (SELECT 1 FROM owner),
    'locationConsented', EXISTS (SELECT 1 FROM member_consent
      WHERE member_id IN (SELECT id FROM owner)
        AND consent_type = 'LOCATION_BASED_SERVICE' AND withdrawn_at IS NULL),
    'savedForPlace', (SELECT count(*) FROM personal_save s JOIN content_place cp ON cp.content_id = s.content_id
      WHERE s.member_id IN (SELECT id FROM owner) AND cp.place_id = '${placeId}'::uuid),
    'triggerCount', (SELECT count(*) FROM trigger
      WHERE member_id IN (SELECT id FROM owner) AND place_id = '${placeId}'::uuid),
    'executionCount', (SELECT count(*) FROM owned_executions),
    'notificationCount', (SELECT count(*) FROM notification WHERE member_id IN (SELECT id FROM owner)),
    'latestExecutionAgeMs', (SELECT EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - max(created_at))) * 1000 FROM owned_executions),
    'eventExecutions', COALESCE((SELECT json_agg(json_build_object(
      'executionId', r.id, 'status', r.status, 'result', r.result,
      'failureCode', r.failure_code, 'agentVersion', r.agent_version
    )) FROM owned_executions r JOIN trigger t ON t.id = r.trigger_id
      WHERE r.trigger_event_id = '${eventId}'::uuid AND t.place_id = '${placeId}'::uuid), '[]'::json)
  );`;
}

function commandResult(command, args, input, timeout = 20000) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let output = '', error = '', settled = false;
    const timer = setTimeout(() => { child.kill(); finish(new Error('DB/SSH command timed out.')); }, timeout);
    function finish(reason) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      if (reason) reject(reason); else resolve(output);
    }
    child.on('error', finish);
    child.stdout.on('data', data => {
      output += data;
      if (output.length > 1024 * 1024) { child.kill(); finish(new Error('Observation exceeded size limit.')); }
    });
    child.stderr.on('data', data => { error = (error + data).slice(-2000); });
    child.on('close', code => finish(code === 0 ? null : new Error(`DB/SSH command failed (${code}): ${error}`)));
    child.stdin.on('error', () => {});
    child.stdin.end(input);
  });
}

function databaseReader(environment) {
  const container = environment.RECALL_DB_CONTAINER || 'remembrall-db';
  const user = environment.RECALL_DB_USER || 'remembrall';
  const database = environment.RECALL_DB_NAME || 'remembrall';
  for (const value of [container, user, database]) {
    if (!/^[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(value)) throw new Error('Invalid DB/container identifier.');
  }
  const dockerArgs = ['exec', '-i', container, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', database];
  let executable = 'docker', args = dockerArgs;
  if (environment.RECALL_SSH_TARGET) {
    if (!/^(?:[A-Za-z0-9_][A-Za-z0-9_.-]*@)?[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(environment.RECALL_SSH_TARGET)) {
      throw new Error('Invalid SSH target. Use an existing SSH config alias or user@host.');
    }
    executable = 'ssh';
    args = ['-T', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=5'];
    if (environment.RECALL_SSH_IDENTITY) args.push('-i', environment.RECALL_SSH_IDENTITY);
    // All remote shell arguments have been restricted to safe identifier characters above.
    args.push(environment.RECALL_SSH_TARGET, ['docker', ...dockerArgs].join(' '));
  }
  return async sql => {
    const output = await commandResult(executable, args,
      `BEGIN READ ONLY;\nSET LOCAL statement_timeout = '10s';\nSET LOCAL TIME ZONE 'UTC';\n${sql}\nCOMMIT;\n`);
    return JSON.parse(output.trim());
  };
}

export function createObservationServer(secret, query) {
  return createServer(async (request, response) => {
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('Cache-Control', 'no-store');
    if (request.headers.authorization !== `Bearer ${secret}`) {
      response.writeHead(401).end(JSON.stringify({ error: 'Observer authentication required.' }));
      return;
    }
    const url = new URL(request.url, 'http://127.0.0.1');
    const match = /^\/(executions|policy-context)\/([^/]+)$/.exec(url.pathname);
    if (request.method !== 'GET' || !match) {
      response.writeHead(404).end(JSON.stringify({ error: 'Unknown observation route.' }));
      return;
    }
    let sql;
    try {
      const build = match[1] === 'policy-context' ? buildPolicyContextSql : buildObservationSql;
      sql = build(match[2], url.searchParams.get('installationId'), url.searchParams.get('placeId'));
    } catch {
      response.writeHead(400).end(JSON.stringify({ error: 'Invalid observation identifiers.' }));
      return;
    }
    try {
      response.end(JSON.stringify(await query(sql)));
    } catch (error) {
      console.error(error.message);
      response.writeHead(502).end(JSON.stringify({ error: 'Read-only DB observation failed; check runner output.' }));
    }
  });
}

function findBruno(environment) {
  const candidates = [environment.BRUNO_CLI_JS,
    environment.APPDATA && path.join(environment.APPDATA, 'npm/node_modules/@usebruno/cli/bin/bru.js'),
    path.resolve(path.dirname(process.execPath), '../lib/node_modules/@usebruno/cli/bin/bru.js'),
    path.resolve(path.dirname(process.execPath), 'node_modules/@usebruno/cli/bin/bru.js')];
  const cli = candidates.find(candidate => candidate && existsSync(candidate));
  if (!cli) throw new Error('Install @usebruno/cli@4.3.0 or set BRUNO_CLI_JS to its bin/bru.js.');
  return cli;
}

export async function main(environment = process.env) {
  const unexpectedArgs = process.argv.slice(2).filter(value => !['--expect-no-action', '--policy'].includes(value));
  if (unexpectedArgs.length) throw new Error('Use --policy or --expect-no-action; configure connection using environment variables.');
  const policy = process.argv.includes('--policy');
  if (policy && process.argv.includes('--expect-no-action')) throw new Error('--policy and --expect-no-action are separate scenarios.');
  const collectionDirectory = policy ? path.resolve(directory, '../bruno-recall-policy') : directory;
  const policyMaxEventAgeMs = Number(environment.RECALL_MAX_EVENT_AGE_MS || 1800000);
  const policyCooldownMs = Number(environment.RECALL_MEMBER_COOLDOWN_MS || 7200000);
  if (![policyMaxEventAgeMs, policyCooldownMs].every(value => Number.isSafeInteger(value) && value > 0 && value <= 2592000000)) {
    throw new Error('Policy durations must be positive integer milliseconds, at most 30 days.');
  }
  const expectedResult = process.argv.includes('--expect-no-action') ? 'NO_ACTION' : 'PROPOSE';
  const baseUrl = environment.RECALL_BASE_URL || 'http://127.0.0.1:18080';
  const shortsUrl = environment.RECALL_SHORTS_URL || 'https://www.youtube.com/shorts/FyXT_7huLds';
  if (!/^https?:\/\//.test(baseUrl) || /[\r\n{}]/.test(baseUrl)) throw new Error('Invalid RECALL_BASE_URL.');
  if (!/^https:\/\/www\.youtube\.com\/shorts\/[A-Za-z0-9_-]{11}$/.test(shortsUrl)) {
    throw new Error('RECALL_SHORTS_URL must be a canonical public YouTube Shorts URL.');
  }
  const cli = findBruno(environment);
  const query = databaseReader(environment);
  const check = await query("SELECT json_build_object('database', current_database(), 'readOnly', current_setting('transaction_read_only')); ");
  if (check.readOnly !== 'on') throw new Error('DB observer is not read-only.');
  const health = await fetch(`${baseUrl}/actuator/health`, { signal: AbortSignal.timeout(5000) });
  if (!health.ok || (await health.json()).status !== 'UP') throw new Error('Backend is not healthy.');

  const secret = randomBytes(32).toString('hex');
  const server = createObservationServer(secret, query);
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  const temp = await mkdtemp(path.join(tmpdir(), 'remembrall-recall-'));
  const envFile = path.join(temp, 'run.bru');
  const reports = path.join(collectionDirectory, 'reports');
  const reportName = policy ? 'policy.xml' : expectedResult === 'NO_ACTION' ? 'recall-no-action.xml' : 'recall.xml';
  let child;
  const stop = () => child?.kill();
  process.once('SIGINT', stop);
  process.once('SIGTERM', stop);
  try {
    const variables = { baseUrl, shortsUrl, expectedResult, policyMaxEventAgeMs, policyCooldownMs,
      observerUrl: `http://127.0.0.1:${server.address().port}`, observerSecret: secret,
      pollIntervalMs: 2000, analysisTimeoutMs: 300000, observationTimeoutMs: 30000 };
    await writeFile(envFile, `vars {\n${Object.entries(variables).map(([key, value]) => `  ${key}: ${value}`).join('\n')}\n}\n`, { mode: 0o600 });
    await mkdir(reports, { recursive: true });
    console.log(policy ? 'Recall policy scenarios: isolated members, read-only DB conditions and recovery controls; no FCM token.' :
      `Recall scenario expects ${expectedResult}. Observer uses read-only DB access; no FCM token is registered.`);
    const code = await new Promise((resolve, reject) => {
      child = spawn(process.execPath, [cli, 'run', '--env-file', envFile, '--noproxy',
        '--reporter-junit', path.join(reports, reportName), '--reporter-skip-all-headers', '--reporter-skip-body'],
      { cwd: collectionDirectory, windowsHide: true, stdio: 'inherit' });
      child.once('error', reject);
      child.once('close', value => resolve(value ?? 1));
    });
    return code;
  } finally {
    process.removeListener('SIGINT', stop);
    process.removeListener('SIGTERM', stop);
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
    await unlink(envFile).catch(() => {});
    await rmdir(temp).catch(() => {});
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().then(code => { process.exitCode = code; }).catch(error => { console.error(error.message); process.exitCode = 1; });
}
