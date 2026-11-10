import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { createServer as createTcpServer } from 'node:net';
import { randomBytes } from 'node:crypto';
import { mkdtemp, mkdir, writeFile, rm, open } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildObservationSql } from '../bruno-recall/run.mjs';

const directory = path.dirname(fileURLToPath(import.meta.url));
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const requiredKeys = ['YOUTUBE', 'GEMINI', 'LOCATIONIQ', 'KAKAO', 'DATAPORTAL', 'VWORLD'];

export function validateReceipt(receipt, sha) {
  if (!/^[0-9a-f]{40}$/.test(sha || '') || receipt.sha !== sha || receipt.status !== 'passed' || receipt.stage !== 'complete'
      || receipt.embeddingDimensions !== 768 || !(receipt.successfulModelCalls >= 1)
      || !['PROPOSE', 'NO_ACTION'].includes(receipt.llmDecision) || !/^[A-Za-z0-9._/-]{1,100}$/.test(receipt.model || '')) {
    throw new Error('External gate receipt is missing or does not match this artifact revision');
  }
  return receipt.model;
}

export function configuration(environment) {
  const keys = Object.fromEntries(requiredKeys.map(name => {
    const value = environment[`EXTERNAL_${name}_API_KEY`];
    if (!value || /[\r\n]/.test(value)) throw new Error(`Missing or invalid EXTERNAL_${name}_API_KEY`);
    return [name.toLowerCase(), value];
  }));
  const model = environment.EXTERNAL_RECALL_MODEL;
  if (!model || !/^[A-Za-z0-9._/-]{1,100}$/.test(model)) throw new Error('Set EXTERNAL_RECALL_MODEL to a team-verified model');
  const url = environment.EXTERNAL_SHORTS_URL?.replace(/^https:\/\/youtube\.com\//, 'https://www.youtube.com/');
  if (!/^https:\/\/www\.youtube\.com\/shorts\/[A-Za-z0-9_-]{11}$/.test(url || '')) throw new Error('Set a canonical EXTERNAL_SHORTS_URL');
  return { keys, model, url };
}

export function contentSql(contentId, installationId) {
  if (![contentId, installationId].every(value => uuid.test(value || ''))) throw new Error('Invalid UUID');
  return `SELECT json_build_object('contentId',c.id,'dimensions',vector_dims(c.embedding),
    'hasEmbedding',c.embedding IS NOT NULL,'model',c.embedding_model,
    'places',(SELECT count(*) FROM content_place cp WHERE cp.content_id=c.id),
    'verifiedPlaces',(SELECT count(*) FROM content_place cp JOIN place p ON p.id=cp.place_id
      WHERE cp.content_id=c.id AND p.verification_provider='KAKAO' AND p.verification_place_id IS NOT NULL),
    'geocodingProviders',(SELECT json_agg(DISTINCT p.geocoding_provider) FROM content_place cp JOIN place p ON p.id=cp.place_id WHERE cp.content_id=c.id))
    FROM content c JOIN personal_save s ON s.content_id=c.id JOIN member m ON m.id=s.member_id
    WHERE c.id='${contentId}'::uuid AND m.auth_provider='GUEST'
      AND m.provider_user_id=encode(sha256(convert_to('${installationId.toLowerCase()}','UTF8')),'hex');`;
}

export function observationServer(secret, query, onObservation = () => {}) {
  return createServer(async (request, response) => {
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('Cache-Control', 'no-store');
    if (request.headers.authorization !== `Bearer ${secret}`) { response.writeHead(401).end('{}'); return; }
    const url = new URL(request.url, 'http://localhost');
    const match = /^\/(content|executions)\/([^/]+)$/.exec(url.pathname);
    if (request.method !== 'GET' || !match) { response.writeHead(404).end('{}'); return; }
    try {
      const installation = url.searchParams.get('installationId');
      if (match[1] === 'content') {
        const content = await query(contentSql(match[2], installation));
        onObservation('content', content);
        response.end(JSON.stringify(content));
      } else {
        const place = url.searchParams.get('placeId');
        const observed = await query(buildObservationSql(match[2], installation, place));
        for (const execution of observed.executions) {
          if (!uuid.test(execution.executionId)) throw new Error('Invalid execution ID');
          execution.successfulModelCalls = (await query(`SELECT json_build_object('count',count(*)) FROM recall_execution_trace
            WHERE execution_id='${execution.executionId}'::uuid AND step='MODEL_CALL' AND success=true;`)).count;
        }
        onObservation('execution', observed);
        response.end(JSON.stringify(observed));
      }
    } catch { response.writeHead(502).end(JSON.stringify({ error: 'Scoped read-only observation failed' })); }
  });
}

function command(executable, args, { input, env = process.env, timeout = 30000 } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, args, { env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let output = '';
    const timer = setTimeout(() => child.kill('SIGKILL'), timeout);
    child.stdout.on('data', value => { output += value; if (output.length > 2 ** 20) child.kill('SIGKILL'); });
    child.stderr.resume(); // External errors may contain credentials. Never forward raw output.
    child.on('error', error => { clearTimeout(timer); reject(new Error(`Could not execute ${path.basename(executable)}`)); });
    child.on('close', code => { clearTimeout(timer); code === 0 ? resolve(output.trim()) : reject(new Error(`Command failed: ${path.basename(executable)}`)); });
    child.stdin.on('error', () => {}); child.stdin.end(input);
  });
}

async function freePort() {
  const server = createTcpServer();
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve)); return port;
}

export async function main(environment = process.env, args = process.argv.slice(2)) {
  const started = Date.now();
  const reports = path.join(directory, 'reports'); await mkdir(reports, { recursive: true });
  await rm(path.join(reports, 'external.xml'), { force: true });
  const summary = { sha: environment.RELEASE_SHA || 'local', status: 'failed', stage: 'configuration', realDevicePush: 'not-run' };
  let temporary, container, java, observer, cliProcess, log;
  let aborted = false;
  let exitCode = 1;
  const stop = () => { aborted = true; cliProcess?.kill('SIGTERM'); java?.kill('SIGTERM'); };
  process.once('SIGTERM', stop); process.once('SIGINT', stop);
  try {
    const config = configuration(environment);
    if (args.length !== 2 || args[0] !== '--jar' || !existsSync(args[1])) throw new Error('Use --jar with an existing validated artifact');
    const jar = path.resolve(args[1]);
    const cli = environment.BRUNO_CLI_JS || path.resolve(path.dirname(process.execPath), '../lib/node_modules/@usebruno/cli/bin/bru.js');
    if (!existsSync(cli)) throw new Error('Install Bruno CLI 4.3.0 or set BRUNO_CLI_JS');
    if ((await command(process.execPath, [cli, '--version'])) !== '4.3.0') throw new Error('Bruno CLI 4.3.0 required');
    temporary = await mkdtemp(path.join(tmpdir(), 'remembrall-external-'));
    container = `remembrall-external-${randomBytes(8).toString('hex')}`;
    const password = randomBytes(24).toString('hex');
    summary.stage = 'database';
    await command('docker', ['run', '-d', '--name', container, '--label', 'purpose=external-smoke',
      '-e', 'POSTGRES_DB=external_smoke', '-e', 'POSTGRES_USER=external_smoke', '-e', 'POSTGRES_PASSWORD',
      '-p', '127.0.0.1::5432', 'pgvector/pgvector:pg16'], { env: { ...environment, POSTGRES_PASSWORD: password }, timeout: 90000 });
    const dbPort = Number((await command('docker', ['port', container, '5432/tcp'])).match(/127\.0\.0\.1:(\d+)/)?.[1]);
    if (!dbPort) throw new Error('No private DB port');
    let dbReady = false;
    for (let i = 0; i < 30 && !aborted; i++) {
      try { await command('docker', ['exec', container, 'pg_isready', '-U', 'external_smoke']); dbReady = true; break; }
      catch { await new Promise(resolve => setTimeout(resolve, 1000)); }
    }
    if (!dbReady) throw new Error('DB did not start');
    const properties = {
      'spring.config.import': '', 'spring.datasource.url': `jdbc:postgresql://127.0.0.1:${dbPort}/external_smoke`,
      'spring.datasource.username': 'external_smoke', 'spring.datasource.password': password,
      'spring.ai.google.genai.api-key': config.keys.gemini, 'spring.ai.google.genai.embedding.api-key': config.keys.gemini,
      'spring.ai.google.genai.vertex-ai': 'false', 'spring.ai.google.genai.project-id': '',
      'spring.ai.google.genai.embedding.project-id': '', 'kakao.app-id': '1',
      'recall.agent.mode': 'llm', 'recall.agent.provider': 'google-genai', 'recall.agent.model': config.model,
      'recall.member-cooldown': '0s', 'recall.agent.max-tool-calls': '3', 'recall.agent.execution-timeout': '90s',
      'notification.fcm-credentials-path': '', 'kma.api.key': environment.EXTERNAL_KMA_API_KEY || '',
      'spring.ai.vectorstore.pgvector.initialize-schema': 'false',
    };
    for (const [name, key] of Object.entries(config.keys)) properties[`${name}.api.key`] = key;
    const configPath = path.join(temporary, 'external.properties');
    const escape = value => String(value).replaceAll('\\', '\\\\').replaceAll('\r', '\\r').replaceAll('\n', '\\n');
    await writeFile(configPath, Object.entries(properties).map(([key, value]) => `${key}=${escape(value)}`).join('\n'), { mode: 0o600 });
    const port = await freePort(); summary.stage = 'startup';
    log = await open(path.join(temporary, 'backend.log'), 'w', 0o600);
    java = spawn('java', ['-Xms128m', '-Xmx1024m', '-jar', jar, `--spring.config.additional-location=file:${configPath}`,
      '--server.address=127.0.0.1', `--server.port=${port}`], {
      cwd: temporary, env: { PATH: environment.PATH, HOME: environment.HOME, LANG: 'C.UTF-8', SPRING_CONFIG_IMPORT: '' },
      stdio: ['ignore', log.fd, log.fd], windowsHide: true,
    });
    java.on('error', () => { aborted = true; });
    let healthy = false;
    for (let i = 0; i < 90 && !aborted; i++) {
      if (java.exitCode !== null) break;
      try { const response = await fetch(`http://127.0.0.1:${port}/actuator/health`, { signal: AbortSignal.timeout(2000) });
        if (response.ok && (await response.json()).status === 'UP') { healthy = true; break; } } catch {}
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    if (!healthy) throw new Error('Backend did not become healthy');
    const query = async sql => JSON.parse(await command('docker', ['exec', '-i', container, 'psql', '-X', '-qAt',
      '-v', 'ON_ERROR_STOP=1', '-U', 'external_smoke', '-d', 'external_smoke'], {
      input: `BEGIN READ ONLY; SET LOCAL statement_timeout='10s'; SET LOCAL TIME ZONE 'UTC';\n${sql}\nCOMMIT;`,
    }));
    if ((await query("SELECT json_build_object('count',count(*)) FROM content;")).count !== 0) throw new Error('External DB must start without content');
    const secret = randomBytes(32).toString('hex');
    observer = observationServer(secret, query, (kind, observed) => {
      if (kind === 'content') {
        summary.embeddingDimensions = observed.dimensions;
        summary.embeddingModel = observed.model;
        summary.geocodingProviders = observed.geocodingProviders;
      } else if (observed.executions.length === 1) {
        const row = observed.executions[0];
        summary.llmDecision = ['PROPOSE', 'NO_ACTION'].includes(row.result) ? row.result : 'invalid';
        summary.successfulModelCalls = row.successfulModelCalls;
      }
    });
    await new Promise(resolve => observer.listen(0, '127.0.0.1', resolve));
    const envFile = path.join(temporary, 'external.bru');
    const variables = { baseUrl: `http://127.0.0.1:${port}`, observerUrl: `http://127.0.0.1:${observer.address().port}`,
      observerSecret: secret, shortsUrl: config.url };
    await writeFile(envFile, `vars {\n${Object.entries(variables).map(([key, value]) => `  ${key}: ${value}`).join('\n')}\n}`, { mode: 0o600 });
    summary.stage = 'bruno';
    const cliLog = await open(path.join(temporary, 'bruno.log'), 'w', 0o600);
    try {
      const code = await new Promise((resolve, reject) => {
        cliProcess = spawn(process.execPath, [cli, 'run', '--env-file', envFile, '--noproxy',
          '--reporter-junit', path.join(reports, 'external.xml'), '--reporter-skip-all-headers', '--reporter-skip-body'],
        { cwd: path.join(directory, '../bruno-external'), stdio: ['ignore', cliLog.fd, cliLog.fd], windowsHide: true });
        const timer = setTimeout(() => cliProcess.kill('SIGKILL'), 600000);
        cliProcess.once('error', () => { clearTimeout(timer); reject(new Error('Could not start Bruno')); });
        cliProcess.once('close', value => { clearTimeout(timer); resolve(value); });
      });
      if (code !== 0 || aborted) throw new Error('External integration assertions failed');
    } finally { await cliLog.close(); }
    summary.status = 'passed'; summary.stage = 'complete'; summary.model = config.model;
    summary.videoId = config.url.split('/').at(-1); exitCode = 0;
  } catch (error) {
    // Never print raw backend/provider errors: URLs may contain API credentials.
    console.error(`External smoke failed at stage: ${summary.stage}. See sanitized test report.`);
    if (summary.stage === 'configuration') console.error(error.message);
  } finally {
    process.removeListener('SIGINT', stop); process.removeListener('SIGTERM', stop);
    cliProcess?.kill('SIGTERM');
    observer?.closeAllConnections(); if (observer) await new Promise(resolve => observer.close(resolve));
    if (java && java.exitCode === null) {
      java.kill('SIGTERM');
      await Promise.race([new Promise(resolve => java.once('close', resolve)), new Promise(resolve => setTimeout(resolve, 10000))]);
      if (java.exitCode === null) java.kill('SIGKILL');
    }
    await log?.close();
    if (container) await command('docker', ['rm', '-fv', container]).catch(() => {
      summary.cleanupVerified = false;
      if (summary.status === 'passed') { summary.status = 'failed'; summary.stage = 'cleanup'; }
      exitCode = 1;
    });
    if (temporary) await rm(temporary, { recursive: true, force: true });
    summary.durationMs = Date.now() - started;
    await writeFile(path.join(reports, 'result.json'), JSON.stringify(summary, null, 2));
    if (environment.GITHUB_STEP_SUMMARY) await writeFile(environment.GITHUB_STEP_SUMMARY,
      `External API gate: **${summary.status}**\n\nStage: ${summary.stage}\n\nCommit: ${summary.sha}\n\nLLM: ${summary.llmDecision || 'not run'}\n\nActual device push: not run\n`, { flag: 'a' });
  }
  return exitCode;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().then(code => { process.exitCode = code; }).catch(() => { console.error('External runner failed'); process.exitCode = 1; });
}
