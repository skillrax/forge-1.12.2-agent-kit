import { serveStdio } from '@modelcontextprotocol/server/stdio';
import { projectRoot } from './bridge.mjs';
import path from 'node:path';
import { createServer } from './tools.mjs';

// stdout belongs exclusively to MCP. Any diagnostic logging must use stderr.
if (path.resolve(process.cwd()).toLowerCase() !== projectRoot.toLowerCase()) throw new Error('MCP cwd differs from server root; use tools/Start-Mcp.ps1 from the intended checkout.');
serveStdio(() => createServer());
