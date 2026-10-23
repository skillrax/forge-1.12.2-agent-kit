# Forge 1.12.2 Agent Kit (maintainer workspace)

A Windows kit for AI-assisted Minecraft 1.12.2 / Forge mod development. **Recipients start with [kit/README.md](kit/README.md). Maintainers start with [TASK.md](TASK.md) and [AGENTS.md](AGENTS.md).** Version: [kit/kit.json](kit/kit.json).

| Location | Role |
|---|---|
| `kit/` | the recipient kit: agent.ps1, generators, check harness, docs, examples |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | maintenance loop, doc rules, tool contracts |
| [docs/REFERENCES.md](docs/REFERENCES.md) | where things are |
| [docs/CAPABILITIES.md](docs/CAPABILITIES.md) | mechanisms with accepted proofs |
| [qa/README.md](qa/README.md) | tests and fixtures (guide samples, generator tests, proof projects) |
| `packaging/` | recipient ZIP allowlist and packager |

`releases/`, `evidence/` and `.work/` hold local generated output. Original exports, historical evidence and archives remain in the original workspace: [docs/FORK.md](docs/FORK.md).

---
# Release notes
While this kit is in full release, it is still in active development. Not every tool is flawless. Not every generator is truly comprehensive. I thuroughly recommend Reading the code yourself before releasing any projects to make sure its not going to create a runaway memory leak or compromise anyones data. 

# Quick setup guide

1. Build a packaged kit with the packaging script `Build-KitZip.ps1` OR use a prepackaged zip from releases
2. extract to a folder as your root project
3. run setup.cmd as admin to install required JDKs and correctly set java home to jdk25 (uses adoptium. You can use whatever you want and skip this step so long as it works)
4. open the root folder as a project in any agentic frontend (opencode, claudecode, codex, vscode, etc.)
5. prompt however best works for your model of choice. 

A minimum of 65k context is required for local inference. 131k is considerably better if your model retains context well enough. 
I recommend starting with a planning phase in 3 steps. 
1. explore the sources and kit
2. create and answer decisions for content, compromises, and compatibility
3. A full autonomous implementation plan
Then move onto actually building and live testing as needed. 
This gave me the best results with most models. Complex projects handled no problem. 
