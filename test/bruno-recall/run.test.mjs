import test from 'node:test';
import assert from 'node:assert/strict';
import { buildObservationSql, buildPolicyContextSql, createObservationServer } from './run.mjs';

const id = '1311b303-344c-4cbd-85a2-0333e73b5569';

test('observation rejects SQL injection and incomplete identifiers', () => {
  for (const value of [null, '', id + "'; DELETE FROM member; --", 'not-a-uuid']) {
    assert.throws(() => buildObservationSql(value, id, id));
    assert.throws(() => buildObservationSql(id, value, id));
    assert.throws(() => buildObservationSql(id, id, value));
    assert.throws(() => buildPolicyContextSql(value, id, id));
    assert.throws(() => buildPolicyContextSql(id, value, id));
    assert.throws(() => buildPolicyContextSql(id, id, value));
  }
  const sql = buildObservationSql(id, id, id);
  assert.match(sql, /m\.auth_provider = 'GUEST'/);
  assert.match(sql, /m\.provider_user_id = '[0-9a-f]{64}'/);
  assert.match(sql, /t\.place_id =/);
  assert.match(sql, /r\.trigger_event_id =/);
  assert.doesNotMatch(sql, /\b(INSERT|UPDATE|DELETE|DROP|ALTER)\b/i);
  const policySql = buildPolicyContextSql(id, id, id);
  assert.match(policySql, /withdrawn_at IS NULL/);
  assert.match(policySql, /'triggerCount'/);
  assert.match(policySql, /'executionCount'/);
  assert.match(policySql, /'notificationCount'/);
  assert.match(policySql, /'eventExecutions'/);
  assert.match(policySql, /'latestExecutionAgeMs'/);
  assert.doesNotMatch(policySql, /\b(INSERT|UPDATE|DELETE|DROP|ALTER)\b/i);
});

test('policy-context route uses its scoped query and still requires authentication', async t => {
  const queries = [];
  const server = createObservationServer('test-observer-secret', async sql => {
    queries.push(sql);
    return { memberExists: true, executionCount: 0, notificationCount: 0, eventExecutions: [] };
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); return new Promise(resolve => server.close(resolve)); });
  const route = `http://127.0.0.1:${server.address().port}/policy-context/${id}?installationId=${id}&placeId=${id}`;
  assert.equal((await fetch(route)).status, 401);
  assert.equal(queries.length, 0);
  const response = await fetch(route, { headers: { Authorization: 'Bearer test-observer-secret' } });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).executionCount, 0);
  assert.equal(queries.length, 1);
  assert.match(queries[0], /WITH owner AS/);
});

test('loopback observer requires its secret and rejects invalid routes before DB access', async t => {
  let queries = 0;
  const server = createObservationServer('test-observer-secret', async () => { queries++; return { executions: [] }; });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); return new Promise(resolve => server.close(resolve)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const route = `/executions/${id}?installationId=${id}&placeId=${id}`;
  assert.equal((await fetch(base + route)).status, 401);
  const headers = { Authorization: 'Bearer test-observer-secret' };
  assert.equal((await fetch(base + '/executions/bad?installationId=' + id + '&placeId=' + id, { headers })).status, 400);
  assert.equal((await fetch(base + '/anything', { headers })).status, 404);
  assert.equal((await fetch(base + route, { method: 'POST', headers })).status, 404);
  assert.equal(queries, 0);
  const response = await fetch(base + route, { headers });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { executions: [] });
  assert.equal(queries, 1);
});

test('DB failures are explicit failures, not empty successful observations', async t => {
  const server = createObservationServer('test-observer-secret', async () => { throw new Error('Expected test DB failure'); });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => { server.closeAllConnections(); return new Promise(resolve => server.close(resolve)); });
  const response = await fetch(`http://127.0.0.1:${server.address().port}/executions/${id}?installationId=${id}&placeId=${id}`,
    { headers: { Authorization: 'Bearer test-observer-secret' } });
  assert.equal(response.status, 502);
});
