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
      return { content: [{ type: 'text', text: JSON.stringify(data) }], structuredContent: data, isError: exitCode !== 0 };
    } catch (error) {
      const data = { schemaVersion: 1, status: 'FAIL', message: String(error.message).slice(0, 1000) };
      return { content: [{ type: 'text', text: JSON.stringify(data) }], structuredContent: data, isError: true };
    }
  };
  server.registerTool('project_status', {
    description: 'Read compact current task, canonical projectRoot, capability freshness and last validation. Confirm root before checks. lastRunMatchesCurrentInputs shows whether the saved result is stale. Does not run tests.',
    inputSchema: z.object({}).strict(), outputSchema,
    annotations: { readOnlyHint: true, openWorldHint: false },
  }, (_, context) => respond({ action: 'status' }, context));
  server.registerTool('validate', {
    description: 'Run one validation profile. all permits an empty template; mod requires @Mod; unit requires tests; server checks development-server readiness/shutdown. PASS is limited to the reported scope.',
    inputSchema: z.object({
      profile: z.enum(['all', 'environment', 'build', 'mod', 'unit', 'server']),
      timeoutSeconds: z.number().int().min(10).max(7200).optional().describe('Optional runner timeout; default 900 seconds.'),
      clean: z.boolean().optional().describe('Force a clean recovery build; normally reuse unchanged Gradle work.'),
    }).strict(), outputSchema,
    annotations: { readOnlyHint: false, destructiveHint: false, openWorldHint: true },
  }, (input, context) => respond({ action: 'check', ...input }, context));
  server.registerTool('diagnostics', {
    description: 'Read at most ten diagnostic lines for a validation run. Omit runId for the latest. validationStatus describes the check; successful retrieval is not a passed check.',
    inputSchema: z.object({ runId: z.string().regex(/^[A-Za-z0-9-]+$/).max(100).optional() }).strict(), outputSchema,
    annotations: { readOnlyHint: true, openWorldHint: false },
  }, (input, context) => respond({ action: 'errors', ...input }, context));
  return server;
}
