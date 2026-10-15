// Actual pinned stdio launcher + real shell runner; no mocked Gradle result.
import { Client } from '@modelcontextprotocol/client';
import { StdioClientTransport } from '@modelcontextprotocol/client/stdio';
import fs from 'node:fs/promises';
import path from 'node:path';
const root=path.resolve(process.argv[2]);
const client=new Client({name:'kit-candidate-sanity',version:'1'});
const transport=new StdioClientTransport({command:'powershell.exe',args:['-NoProfile','-File','./tools/Start-Mcp.ps1'],cwd:root,env:process.env});
const require=(ok,message)=>{if(!ok)throw new Error(message);};
try{
  await client.connect(transport);
  const list=await client.listTools();require(list.tools.map(t=>t.name).sort().join(',')==='diagnostics,project_status,validate','Wrong MCP tool list');
  const before=await client.callTool({name:'project_status',arguments:{}});
  require(!before.isError && before.structuredContent.projectRoot.toLowerCase()===root.toLowerCase(),'Wrong MCP root');
  const mod=await client.callTool({name:'validate',arguments:{profile:'mod',timeoutSeconds:180}},undefined,{timeout:210000});
  require(!mod.isError && mod.structuredContent.status==='PASS','Actual MCP mod validation failed');
  const diagnostics=await client.callTool({name:'diagnostics',arguments:{runId:mod.structuredContent.runId}});
  require(!diagnostics.isError && diagnostics.structuredContent.validationStatus==='PASS','MCP diagnostics failed');
  const after=await client.callTool({name:'project_status',arguments:{}});
  require(after.structuredContent.lastRunMatchesCurrentInputs===true,'MCP freshness/root mismatch');
  const result={status:'PASS',root,tools:list.tools.map(t=>t.name),mod:mod.structuredContent,diagnostics:diagnostics.structuredContent,scope:'Real local stdio MCP initialize/discovery/status/Gradle mod validation/diagnostics/freshness. Does not prove this desktop chat attached to the kit MCP.'};
  await fs.writeFile(path.join(root,'mcp-sanity.json'),JSON.stringify(result,null,2));
  console.log(JSON.stringify({status:'PASS',root,runId:mod.structuredContent.runId,report:path.join(root,'mcp-sanity.json')}));
}finally{await client.close();}
