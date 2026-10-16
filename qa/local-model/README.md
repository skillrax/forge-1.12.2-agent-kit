# Local-model trials

Measures whether a small local model can build mods it has never seen with the packaged kit, and where it gets stuck. Results drive general fixes (facts, checks, hints), never per-mod guides. The kit is the same ZIP a recipient gets; nothing here is trial-specific.

## Setup (once)

- An OpenAI-compatible server with tool calling: llama.cpp `llama-server --jinja -c 65536 ...` (without `--jinja` tool calls fail), LM Studio, vLLM or Ollama's `/v1`. Use a model trained for agentic tool use; note its exact quantization.
- OpenCode with your endpoint in its global config, `~/.config/opencode/opencode.json` (`model` is `<provider>/<model id as served>`; set `limit.context` to the server's real context so OpenCode compacts in time):

```json
{
  "$schema": "https://opencode.ai/config.json",
  "model": "local/your-model-id",
  "share": "disabled",
  "provider": {
    "local": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "Local",
      "options": { "baseURL": "http://localhost:8080/v1" },
      "models": { "your-model-id": { "name": "your-model-id", "limit": { "context": 65536, "output": 8192 } } }
    }
  }
}
```

- The kit ZIP for the current version: `packaging/Build-KitZip.ps1` (writes releases/<version>/).

## Per trial

1. Extract the ZIP into a new folder **outside this repository** (e.g. `Documents\kit-trials\p0-local`), so the agent cannot find maintainer files, fixtures or these prompts. Every trial starts from a fresh copy; keep the path short (Windows 260-character limit).
2. Open that folder in the agent app (`opencode` there). It reads the kit's AGENTS.md. Before the prompt, note the context already used: frontend prompt, tools and AGENTS.md are overhead the kit can only partly shrink.
3. Paste the prompt from PROMPTS.md verbatim. Do not help. If it asks a question, answer as a non-expert user would, in one line. If it is stuck (same error 3 times, or looping), give one nudge and record it as an intervention.
4. When it says it is done: run `./agent.ps1 status` and `./agent.ps1 check game` yourself, then `gradlew.bat runClient` and try the feature once.
5. Last message to the agent: "Write kit-feedback.md: where the kit's docs or tools slowed you down or misled you, and what you guessed." Copy it into the result.
6. Write `qa/local-model/results/<id>.md` (template below). Keep the trial folder until the cause is fixed.

## Control run

Run each prompt the local model fails once more with a frontier agent (Claude Code) in another fresh copy of the same ZIP, same procedure. Both fail at the same point: the kit is the cause, fix it. Only the local model fails: mostly a model limit; fix the kit only if a fact, check or hint would plausibly have prevented it. Suffix result ids with the agent: `p3-local`, `p3-claude`.

## Result template

```markdown
# <id> - <agent app, model, quant>, kit <version>, <date>
Outcome: works | partly (what) | fails      Checked by: check game / runClient
Time: <min>   Context peak: <tokens> of <limit>   Overhead before prompt: <tokens>   Interventions: <n, what>
Edited pinned build files: yes/no   Wrote game tests: yes/no   Report honest: yes/no

## Where it got stuck
- <symptom> -> <cause> (category: setup | shell/tool use | wrong-version API | sides/sync | docs not found | docs wrong | generator | context overflow | model reasoning)

## General fix (would it help a different mod?)
- <fact/check/hint/generator change, or "model limit, no kit fix">
```

Category `model reasoning` is the honest bucket for failures no doc could prevent; do not bloat the kit for those. Two or more trials hitting the same category is a signal; one is an anecdote.
