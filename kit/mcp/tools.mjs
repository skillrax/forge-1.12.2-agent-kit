import { McpServer } from '@modelcontextprotocol/server';
import { z } from 'zod';
import { runAgent } from './bridge.mjs';
import { readFileSync } from 'node:fs';
const kit = JSON.parse(readFileSync(new URL('../kit.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''));

export function createServer(runner = runAgent) {
  const server = new McpServer({ name: kit.id, version: kit.version });
  const outputSchema = z.object({ schemaVersion: z.literal(1) }).passthrough();
  const respond = async (input, context) => {
    try {
      const { data, exitCode } = await runner(input, { signal: context.signal });
      // Text content mirrors the shell's compact output; the full result stays in structuredContent.
      const text = data.problems !== undefined
        ? [`${data.status} ${data.profile} (${Math.round(data.elapsedSeconds ?? 0)}s): ${data.message}`, ...data.problems, data.status === 'PASS' ? '' : `run ${data.runId}`].filter(Boolean).join('
')
        : JSON.stringify(data);
      return { content: [{ type: 'text', text }], structuredContent: data, isError: exitCode !== 0 };
    } catch (error) {
      const data = { schemaVersion: 1, status: 'FAIL', message: String(error.message).slice(0, 1000) };
      return { content: [{ type: 'text', text: JSON.stringify(data) }], structuredContent: data, isError: true };
    }
  };
  server.registerTool('project_status', {
    description: 'Project root, detected mod, Java status, last check (stale if source changed since) and next action. Does not run anything.',
    inputSchema: z.object({}).strict(), outputSchema,
    annotations: { readOnlyHint: true, openWorldHint: false },
  }, (_, context) => respond({ action: 'status' }, context));
  server.registerTool('validate', {
    description: 'Run a check. build: compile + jar inspection. server: build + dedicated server + registry/asset lint. game: server + game tests in src/gametest/java. unit: JUnit. environment: toolchain.',
    inputSchema: z.object({
      profile: z.enum(['build', 'server', 'game', 'unit', 'environment']),
      test: z.string().regex(/^[a-z0-9*,-]+$/).optional().describe('game profile only: comma-separated @GameTest ids; default all.'),
      timeoutSeconds: z.number().int().min(10).max(7200).optional().describe('Optional runner timeout; default 900 seconds.'),
      clean: z.boolean().optional().describe('Force a clean recovery build; normally reuse unchanged Gradle work.'),
    }).strict(), outputSchema,
    annotations: { readOnlyHint: false, destructiveHint: false, openWorldHint: true },
  }, (input, context) => respond({ action: 'check', ...input }, context));
  server.registerTool('diagnostics', {
    description: 'Re-read the result and problem lines of a previous check (latest if runId is omitted).',
    inputSchema: z.object({ runId: z.string().regex(/^[A-Za-z0-9-]+$/).max(100).optional() }).strict(), outputSchema,
    annotations: { readOnlyHint: true, openWorldHint: false },
  }, (input, context) => respond({ action: 'errors', ...input }, context));
  return server;
}
