import test from 'node:test';
import assert from 'node:assert/strict';
import { configuration, contentSql, observationServer, requiredKeys, validateReceipt } from './run.mjs';

function environment() {
  return { ...Object.fromEntries(requiredKeys.map(key => [`EXTERNAL_${key}_API_KEY`, 'fake-key'])),
    EXTERNAL_RECALL_MODEL: 'team-verified-model', EXTERNAL_SHORTS_URL: 'https://www.youtube.com/shorts/FyXT_7huLds' };
}
test('deployment receipt must prove the same revision, model calls and embedding checks', () => {
  const sha = 'a'.repeat(40);
  const receipt = { sha, status: 'passed', stage: 'complete', model: 'verified-model', embeddingDimensions: 768,
    successfulModelCalls: 1, llmDecision: 'NO_ACTION' };
  assert.equal(validateReceipt(receipt, sha), 'verified-model');
  assert.throws(() => validateReceipt(receipt, 'b'.repeat(40)));
  assert.throws(() => validateReceipt({ ...receipt, successfulModelCalls: 0 }, sha));
  assert.throws(() => validateReceipt({ ...receipt, status: 'failed' }, sha));
});
test('missing live keys fail preflight, never fall back to dummy values', () => {
  const env = environment(); delete env.EXTERNAL_GEMINI_API_KEY;
  assert.throws(() => configuration(env), /EXTERNAL_GEMINI_API_KEY/);
});
test('canonical YouTube URLs and model identifiers are required', () => {
  assert.throws(() => configuration({ ...environment(), EXTERNAL_SHORTS_URL: 'http://127.0.0.1/' }));
  assert.throws(() => configuration({ ...environment(), EXTERNAL_RECALL_MODEL: 'bad\nvalue' }));
  assert.equal(configuration(environment()).model, 'team-verified-model');
});
test('property injection in credentials is rejected without exposing the credential', () => {
  assert.throws(() => configuration({ ...environment(), EXTERNAL_KAKAO_API_KEY: 'SECRET\nproperty=value' }), error =>
    !error.message.includes('SECRET') && error.message.includes('EXTERNAL_KAKAO_API_KEY'));
});
test('embedding observation is restricted to owned content and UUID inputs', () => {
  assert.throws(() => contentSql("';DROP TABLE content;--", 'x'));
  const sql = contentSql('11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222');
  assert.match(sql, /personal_save/); assert.match(sql, /provider_user_id/); assert.match(sql, /vector_dims/);
});
test('observer denies unauthenticated requests and has no mutation route', async () => {
  let calls = 0;
  const server = observationServer('private-observer-token', async () => { calls++; return {}; });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.equal((await fetch(`${url}/content/x`)).status, 401);
    assert.equal((await fetch(`${url}/reset`, { method: 'POST', headers: { Authorization: 'Bearer private-observer-token' } })).status, 404);
    assert.equal((await fetch(`${url}/content/invalid?installationId=invalid`, { headers: { Authorization: 'Bearer private-observer-token' } })).status, 502);
    assert.equal(calls, 0);
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});
test('actual LLM observation includes successful model-call evidence', async () => {
  const executionId = '11111111-1111-4111-8111-111111111111';
  const queries = [];
  const server = observationServer('secret', async sql => {
    queries.push(sql);
    return sql.includes('recall_execution_trace') ? { count: 1 } : { executions: [{ executionId }] };
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/executions/${executionId}?installationId=${executionId}&placeId=${executionId}`,
      { headers: { Authorization: 'Bearer secret' } });
    assert.equal(response.status, 200); assert.equal((await response.json()).executions[0].successfulModelCalls, 1);
    assert.equal(queries.length, 2); assert.match(queries[0], /GUEST/);
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});
