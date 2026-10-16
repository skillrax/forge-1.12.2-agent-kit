# Local-model trials

Measures whether a small local model can build mods it has never seen with the packaged kit, and where it gets stuck. Results drive general fixes (facts, checks, hints), never per-mod guides.

## Setup (once)

- An OpenAI-compatible server with tool calling: llama.cpp `llama-server --jinja -c 65536 ...` (without `--jinja` tool calls fail), LM Studio, vLLM or Ollama's `/v1`. Use a model trained for agentic tool use; note its exact quantization.
- OpenCode installed. It reads the kit's AGENTS.md on start.
- The kit ZIP for the current version: `packaging/Build-KitZip.ps1` (writes releases/<version>/).

## Per trial

1. `powershell -NoProfile -File qa/local-model/New-Trial.ps1 -Id p0-copper -Model <served id> [-BaseUrl http://localhost:8080/v1] [-AutoApprove]`
   It extracts the kit into `../kit-trials/<id>` (outside this repo, so the agent cannot read maintainer files or these prompts) and writes opencode.json. Without `-AutoApprove` OpenCode asks before each shell command; with it the agent runs commands unattended on this PC.
2. Start `opencode` in the printed folder. Before the prompt, note the context already used (frontend prompt + tools + AGENTS.md): that is overhead the kit cannot shrink.
3. Paste the prompt from PROMPTS.md verbatim. Do not help. If it asks a question, answer as a non-expert user would, in one line. If it is stuck (same error 3 times, or looping), give one nudge and record it as an intervention.
4. When it says it is done: run `./agent.ps1 status` and `./agent.ps1 check game` yourself, then `gradlew.bat runClient` and try the feature once.
5. Last message to the agent: "Write kit-feedback.md: where the kit's docs or tools slowed you down or misled you, and what you guessed." Copy it into the result.
6. Write `qa/local-model/results/<id>.md` (template below). Keep the trial folder until the cause is fixed.

## Result template

```markdown
# <id> - <model, quant>, kit <version>, <date>
Outcome: works | partly (what) | fails      Checked by: check game / runClient
Time: <min>   Context peak: <tokens> of <limit>   Overhead before prompt: <tokens>   Interventions: <n, what>
Edited pinned build files: yes/no   Wrote game tests: yes/no   Report honest: yes/no

## Where it got stuck
- <symptom> -> <cause> (category: setup | shell/tool use | wrong-version API | sides/sync | docs not found | docs wrong | generator | context overflow | model reasoning)

## General fix (would it help a different mod?)
- <fact/check/hint/generator change, or "model limit, no kit fix">
```

Category `model reasoning` is the honest bucket for failures no doc could prevent; do not bloat the kit for those. Two or more trials hitting the same category is a signal; one is an anecdote.
