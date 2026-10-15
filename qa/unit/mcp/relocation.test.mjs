import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { Client } from '@modelcontextprotocol/client';
import { StdioClientTransport } from '@modelcontextprotocol/client/stdio';
import { projectRoot } from '../../../kit/mcp/bridge.mjs';

const invoke = (args, cwd, env = process.env) => new Promise((resolve, reject) => {
  const child = spawn('powershell.exe', args, { cwd, env, windowsHide: true });
  let output = '';
  child.stdout.on('data', c => output += c);
  child.stderr.on('data', c => output += c);
  child.on('error', reject);
  child.on('close', code => resolve({ code, output }));
});

test('copied relative config serves and validates a space-containing checkout; wrong roots fail', { timeout: 180000 }, async () => {
  const root = path.join(projectRoot, '..', '.work', 'relocation space', String(Date.now()));
  const copy = await invoke(['-NoProfile', '-File', './tools/New-Checkout.ps1', '-Destination', root], projectRoot);
  assert.equal(copy.code, 0, copy.output);
  assert.equal(await fs.stat(path.join(root, 'mcp/node_modules')).then(() => true, () => false), false);
  assert.equal(await fs.stat(path.join(root, 'reports')).then(() => true, () => false), false);
  for (const entry of ['Setup.cmd', 'THIRD-PARTY-NOTICES.md', 'licenses/gradle-LICENSE.txt', 'tools/JavaEnvironment.ps1']) {
    assert.equal(await fs.stat(path.join(root, entry)).then(() => true, () => false), true, entry);
  }
  const duplicate = await invoke(['-NoProfile', '-File', './tools/New-Checkout.ps1', '-Destination', root], projectRoot);
  assert.equal(duplicate.code, 1);
  assert.match(duplicate.output, /already exists/);
  const config = await fs.readFile(path.join(root, 'mcp/client.example.json'), 'utf8');
  assert.ok(!config.includes(projectRoot) && !config.includes('fullAgentic-GPT-v1'));
  const args = JSON.parse(config).args;
  const missingJdk = await invoke(['-NoProfile', '-File', './tools/Setup.ps1', '-SkipDependencies'], root, { ...process.env, JAVA_HOME: path.join(root, 'missing JDK') });
  assert.equal(missingJdk.code, 0, missingJdk.output);
  assert.match(missingJdk.output, /"status":"PASS"/);
  const explicitMissingJdk = await invoke(['-NoProfile', '-File', './tools/Setup.ps1', '-SkipDependencies', '-GradleJavaHome', path.join(root, 'missing JDK')], root);
  assert.equal(explicitMissingJdk.code, 1);
  assert.match(explicitMissingJdk.output, /Explicit GradleJavaHome/);
  const setup = await invoke(['-NoProfile', '-File', './tools/Setup.ps1', '-WithMcp'], root);
  assert.equal(setup.code, 0, setup.output);
  const client = new Client({ name: 'relocation-test', version: '2' });
  try {
    await client.connect(new StdioClientTransport({ command: 'powershell.exe', args, cwd: root }));
    const status = await client.callTool({ name: 'project_status', arguments: {} });
    assert.equal(status.structuredContent.projectRoot.toLowerCase(), root.toLowerCase());
    assert.equal(status.structuredContent.lastRun, null);
    const check = await client.callTool({ name: 'validate', arguments: { profile: 'all', timeoutSeconds: 120 } });
    assert.ok(!check.isError, JSON.stringify(check.structuredContent));
    assert.equal(check.structuredContent.projectRoot.toLowerCase(), root.toLowerCase());
    assert.equal(check.structuredContent.status, 'PASS');
    const fresh = await client.callTool({ name: 'project_status', arguments: {} });
    assert.equal(fresh.structuredContent.lastRunMatchesCurrentInputs, true);
    await fs.writeFile(path.join(root, 'src', 'changed.txt'), 'relocation freshness test');
    const stale = await client.callTool({ name: 'project_status', arguments: {} });
    assert.equal(stale.structuredContent.lastRunMatchesCurrentInputs, false);
  } finally { await client.close(); }
  const wrong = await invoke(['-NoProfile', '-File', path.join(projectRoot, 'tools', 'Start-Mcp.ps1')], root);
  assert.equal(wrong.code, 1);
  assert.match(wrong.output, /working directory differs/);
  await fs.writeFile(path.join(root, 'relocation-result.json'), JSON.stringify({ status: 'PASS', projectRoot: root, scope: 'real MCP setup/all validation, missing JDK recovery, stale input and wrong launcher rejection', v1Preserved: true }));
});
