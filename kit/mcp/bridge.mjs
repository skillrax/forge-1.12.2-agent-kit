import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

export const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

// Array arguments preserve paths with spaces. No shell command is assembled here.
export async function runAgent(input, options = {}) {
  const root = options.projectRoot ?? projectRoot;
  if (process.env.MINECRAFT_KIT_ROOT && path.resolve(process.env.MINECRAFT_KIT_ROOT).toLowerCase() !== path.resolve(root).toLowerCase()) {
    throw new Error('MCP project root mismatch. Run setup in the intended checkout.');
  }
  const action = input.action;
  if (!['status', 'check', 'errors'].includes(action)) throw new Error('Unknown agent action.');
  if (input.profile && !['all', 'environment', 'build', 'mod', 'unit', 'server'].includes(input.profile)) {
    throw new Error('Unknown validation profile.');
  }
  if (input.runId && !/^[A-Za-z0-9-]+$/.test(input.runId)) throw new Error('Invalid run ID.');
  const seconds = input.timeoutSeconds ?? 900;
  if (!Number.isInteger(seconds) || seconds < 10 || seconds > 7200) throw new Error('Invalid timeout.');
  const args = [...(options.prefixArgs ?? []), '-NoProfile', '-File', path.join(root, 'agent.ps1'), '-Action', action];
  if (action === 'check') args.push('-Profile', input.profile ?? 'all', '-TimeoutSeconds', String(seconds));
  if (input.clean) args.push('-Clean');
  if (input.runId) args.push('-RunId', input.runId);
  const signal = options.signal;
  if (signal?.aborted) throw new Error('Tool call cancelled.');
  return new Promise((resolve, reject) => {
    const child = spawn(options.command ?? 'powershell.exe', args, {
      cwd: root, windowsHide: true, shell: false, stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stdout = '';
    let stderr = '';
    let failure;
    let stopping = false;
    const stop = (message) => {
      if (stopping) return;
      stopping = true;
      failure = new Error(message);
      if (child.pid && child.exitCode === null) {
        if (process.platform === 'win32') {
          const killer = spawn(path.join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'taskkill.exe'),
            ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' });
          killer.on('error', () => child.kill());
        } else child.kill('SIGTERM');
      }
    };
    const abort = () => stop('Tool call cancelled; its runner process tree was stopped.');
    signal?.addEventListener('abort', abort, { once: true });
    const timer = setTimeout(() => stop('Runner exceeded its time limit; its process tree was stopped.'),
      action === 'check' ? (seconds + 30) * 1000 : 30000);
    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
      if (stdout.length > 1024 * 1024) stop('Runner output exceeded the 1 MB limit.');
    });
    child.stderr.on('data', (chunk) => { stderr = (stderr + chunk).slice(-2000); });
    const cleanup = () => { clearTimeout(timer); signal?.removeEventListener('abort', abort); };
    child.on('error', (error) => { cleanup(); reject(new Error(`Cannot start the agent runner: ${error.message}`)); });
    child.on('close', (exitCode) => {
      cleanup();
      if (failure) return reject(failure);
      try {
        const data = JSON.parse(stdout.replace(/^\uFEFF/, '').trim());
        if (!data || Array.isArray(data) || data.schemaVersion !== 1) throw new Error('Expected schemaVersion 1 JSON.');
        if (data.projectRoot && path.resolve(data.projectRoot).toLowerCase() !== path.resolve(root).toLowerCase()) throw new Error('Runner project root disagrees with requested checkout.');
        if (!options.command && !data.projectRoot) throw new Error('Runner project root missing.');
        if (action === 'check' && (data.exitCode !== exitCode || (exitCode === 0 && data.status !== 'PASS'))) {
          throw new Error('Runner exit code and validation result disagree.');
        }
        resolve({ data, exitCode });
      } catch (error) {
        reject(new Error(`Invalid runner response: ${error.message}${stderr ? `; ${stderr.slice(-500)}` : ''}`));
      }
    });
  });
}
