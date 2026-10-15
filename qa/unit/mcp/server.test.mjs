import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { Client } from '@modelcontextprotocol/client';
import { StdioClientTransport } from '@modelcontextprotocol/client/stdio';
import { runAgent, projectRoot } from '../../../kit/mcp/bridge.mjs';

const runner = fileURLToPath(new URL('./fixture-runner.mjs', import.meta.url));
const fixtureServer = fileURLToPath(new URL('./fixture-server.mjs', import.meta.url));
const options = (mode) => ({ command: process.execPath, prefixArgs: [runner, mode], projectRoot });

test('bridge preserves pass/failure and rejects malformed or inconsistent JSON', async () => {
  assert.equal((await runAgent({ action: 'check' }, options('pass'))).exitCode, 0);
  assert.equal((await runAgent({ action: 'check' }, options('fail'))).data.status, 'FAIL');
  await assert.rejects(runAgent({ action: 'check' }, options('malformed')), /Invalid runner response/);
  await assert.rejects(runAgent({ action: 'check' }, options('mismatch')), /disagree/);
  await assert.rejects(runAgent({ action: 'check' }, options('wrongroot')), /project root disagrees/);
  await assert.rejects(runAgent({ action: 'check', profile: 'invented' }), /Unknown validation profile/);
  await assert.rejects(runAgent({ action: 'errors', runId: '../outside' }), /Invalid run ID/);
});

test('cancellation stops the runner', { timeout: 10000 }, async () => {
  const controller = new AbortController();
  const request = runAgent({ action: 'check' }, { ...options('hang'), signal: controller.signal });
  const timer = setTimeout(() => controller.abort(), 200);
  try { await assert.rejects(request, /cancelled/); } finally { clearTimeout(timer); }
});

test('MCP tools expose errors honestly and validate inputs', { timeout: 15000 }, async () => {
  const client = new Client({ name: 'test', version: '1' });
  const transport = new StdioClientTransport({ command: process.execPath, args: [fixtureServer] });
  try {
    await client.connect(transport);
    const listing = await client.listTools();
    assert.equal(listing.tools.length, 3);
    const status = await client.callTool({ name: 'project_status', arguments: {} });
    assert.ok(!status.isError);
    const validation = await client.callTool({ name: 'validate', arguments: { profile: 'mod' } });
    assert.equal(validation.isError, true);
    assert.equal(validation.structuredContent.profile, 'mod');
    const diagnostics = await client.callTool({ name: 'diagnostics', arguments: {} });
    assert.ok(!diagnostics.isError, 'Reading a failed check is a successful query.');
    assert.equal(diagnostics.structuredContent.validationStatus, 'FAIL');
    const invalid = await client.callTool({ name: 'validate', arguments: { profile: 'invented' } });
    assert.equal(invalid.isError, true);
    const extra = await client.callTool({ name: 'project_status', arguments: { command: 'anything' } });
    assert.equal(extra.isError, true);
  } finally { await client.close(); }
});

test('legacy 2025 MCP clients can initialize, discover, and call tools', { timeout: 15000 }, async () => {
  const child = spawn(process.execPath, [fixtureServer], { stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
  const pending = new Map();
  const lines = createInterface({ input: child.stdout });
  lines.on('line', (line) => {
    const message = JSON.parse(line);
    if (pending.has(message.id)) { pending.get(message.id)(message); pending.delete(message.id); }
  });
  let id = 0;
  const request = (method, params = {}) => new Promise((resolve) => {
    const requestId = ++id;
    pending.set(requestId, resolve);
    child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id: requestId, method, params }) + '\n');
  });
  try {
    const initialized = await request('initialize', { protocolVersion: '2025-06-18', capabilities: {}, clientInfo: { name: 'legacy-test', version: '1' } });
    assert.equal(initialized.result.protocolVersion, '2025-06-18');
    child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');
    assert.equal((await request('tools/list')).result.tools.length, 3);
    const called = await request('tools/call', { name: 'project_status', arguments: {} });
    assert.equal(JSON.parse(called.result.content[0].text).project, 'fixture');
  } finally {
    lines.close();
    child.stdin.end();
    child.kill();
  }
});
