import { serveStdio } from '@modelcontextprotocol/server/stdio';
import { createServer } from '../../../kit/mcp/tools.mjs';
serveStdio(() => createServer(async (input) => {
  if (input.action === 'errors') return { data: { schemaVersion: 1, validationStatus: 'FAIL', diagnostics: ['fixture error'] }, exitCode: 0 };
  if (input.action === 'check') return { data: { schemaVersion: 1, status: 'FAIL', exitCode: 1, profile: input.profile }, exitCode: 1 };
  return { data: { schemaVersion: 1, project: 'fixture' }, exitCode: 0 };
}));
