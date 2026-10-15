// Real stdio launch with Node absent from the child's PATH; no build/gameplay check.
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import path from 'node:path';
import fs from 'node:fs';
const root = path.resolve(process.argv[2] || 'kit');
const child = spawn(path.join(process.env.SystemRoot, 'System32/WindowsPowerShell/v1.0/powershell.exe'),
  ['-NoProfile', '-File', './tools/Start-Mcp.ps1'], {
    cwd: root, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'],
    env: { ...process.env, PATH: [path.join(process.env.SystemRoot, 'System32'), path.join(process.env.SystemRoot, 'System32/WindowsPowerShell/v1.0')].join(path.delimiter) },
  });
const pending = new Map();
const lines = createInterface({ input: child.stdout });
let id = 0;
child.stderr.on('data', data => process.stderr.write(data));
lines.on('line', line => {
  const message = JSON.parse(line);
  if (pending.has(message.id)) {
    const { resolve, reject } = pending.get(message.id);
    pending.delete(message.id);
    if (message.error) reject(new Error(JSON.stringify(message.error))); else resolve(message.result);
  }
});
child.on('exit', code => { for (const p of pending.values()) p.reject(new Error(`MCP exited ${code}`)); });
child.on('error', error => { for (const p of pending.values()) p.reject(error); });
const request = (method, params) => new Promise((resolve, reject) => {
  const requestId = ++id;
  pending.set(requestId, { resolve, reject });
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id: requestId, method, params }) + '\n');
});
const timer = setTimeout(() => {
  for (const p of pending.values()) p.reject(new Error('MCP check timed out'));
  child.kill();
}, 20000);
try {
  const init=await request('initialize', { protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'node-setup-check', version: '1' } });
  const kit=JSON.parse(fs.readFileSync(path.join(root,'kit.json'),'utf8').replace(/^\uFEFF/,''));
  if(init.serverInfo.version!==kit.version || init.serverInfo.name!==kit.id) throw new Error('MCP identity differs from kit.json');
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }) + '\n');
  const listing = await request('tools/list', {});
  if (listing.tools.map(t => t.name).sort().join(',') !== 'diagnostics,project_status,validate') throw new Error('Unexpected tools');
  const status = await request('tools/call', { name: 'project_status', arguments: {} });
  const data = status.structuredContent || JSON.parse(status.content[0].text);
  if (status.isError || data.projectRoot?.toLowerCase() !== root.toLowerCase()) throw new Error('Status failed: '+JSON.stringify(data));
  console.log(JSON.stringify({ status: 'PASS', projectRoot: root, cases: ['real stdio initialization', 'three tools listed', 'matching status root', 'Node discovered without Node on PATH'], limits: 'Warm host; not Codex app connectivity or cold-PC installation' }));
} finally {
  clearTimeout(timer);
  lines.close();
  child.stdin.end();
  child.kill();
}
