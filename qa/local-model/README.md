# Local-model trials

Measures whether a small local model can build mods it has never seen with the packaged kit, and where it gets stuck. Results drive general fixes (facts, checks, hints), never per-mod guides. The kit is the same ZIP a recipient gets; nothing here is trial-specific.

## Setup (once)

- An OpenAI-compatible server with tool calling: llama.cpp `llama-server --jinja -c 65536 ...` (without `--jinja` tool calls fail), LM Studio, vLLM or Ollama's `/v1`. Use a model trained for agentic tool use; note its exact quantization.
- OpenCode with your endpoint in its global config, `~/.config/opencode/opencode.json` (`model` is `<provider>/<model id as served>`; set `limit.context` to the server's real context so OpenCode compacts in time, and `limit.output` above the model's thinking length: at 8192 a thinking model can spend its whole answer budget thinking and loop):

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
      "models": { "your-model-id": { "name": "your-model-id", "limit": { "context": 65536, "output": 16384 } } }
    }
  }
}
```

- The kit ZIP for the current version: `packaging/Build-KitZip.ps1` (writes releases/<version>/).

## Unattended series (Run-Series.ps1)

`powershell -NoProfile -File qa/local-model/Run-Series.ps1 -Prompts qa/local-model/PROMPTS-3.md [-Ids S0,S1] [-Runs 2]` runs prompts without anyone watching: for each trial it extracts the current kit ZIP into `..\kit-trials\<series>-<agent>-<version>\<id>-r<n>` (outside the repo), sends the prompt with `opencode run --standalone --auto` (model `local/qwen3.8-flash-next-iq3_xxs#high`, context from `-Context`, default 131072, set per run through `OPENCODE_CONFIG_CONTENT`; the global config is untouched), answers a question with "decide, note it in TASK.md" (at most 2), nudges once if `check game` does not pass (limits: `-TrialMinutes` 90 for all work turns of a trial, `-IdleMinutes` 20 without an agent.ps1 call or changed file, then the agent is stopped and still asked for feedback within `-FeedbackMinutes` 15; the summary's Stopped column says why), asks the feedback prompt into `feedback.md`, runs its own `check game` and Read-Trial (`<id>-r<n>.trial.txt`), and adds a row to `summary.md`. The agent's raw output is in `<id>-r<n>.agent.jsonl`. Existing trial folders are skipped, so a stopped series resumes.
- 64k regression pass: `-Context 65536 -Buffer 20000`.
- The runner also sets `compaction.buffer` (`-Buffer`, default 32768) and text-only model input. A compaction summarizes the whole context in what is left of it; with a 10000 buffer a thinking model ran out mid-summary ("Compaction summary reached the output token limit", 4 of 16 series 3 local trials), after which every turn of that session failed the same way. The runner now starts one fresh session in the same folder when that happens (Stopped column: "1 new session"). Without text-only input, reading a texture PNG failed the whole request ("started without the vision encoder"); now the read tool returns an error to the model instead.
- Control run with Claude Code: `-Agent claude -Model sonnet` (runs `claude -p --permission-mode bypassPermissions` in the trial folder; uses the Claude plan).
- What stays manual: the runClient look for trials that pass, and grading with the prompt file's Grade lines.
- OpenCode takes its project folder from the `PWD` environment variable (Git Bash exports it), not from the process's working directory; an agent started from a shell in the repo once worked in the repo itself, read maintainer files and edited TASK.md. The runner sets `PWD` per trial, runs a pre-flight turn in a scratch folder and stops if OpenCode records any other folder, and checks every trial's session folder.

## Per trial (manual)

1. Extract the ZIP into a new folder **outside this repository** (e.g. `Documents\kit-trials\p0-local`), so the agent cannot find maintainer files, fixtures or these prompts. Every trial starts from a fresh copy; keep the path short (Windows 260-character limit).
2. Open that folder in the agent app (`opencode` there). It reads the kit's AGENTS.md. Before the prompt, note the context already used: frontend prompt, tools and AGENTS.md are overhead the kit can only partly shrink.
3. Paste the prompt from PROMPTS.md verbatim. You do not need to watch: every `agent.ps1` call is logged in the trial folder (`reports/agent-log.jsonl`, kit 0.5.0-beta.19+). If it asks a question, answer as a non-expert user would, in one line. If it is stuck (no progress for a long time), give one nudge and record it as an intervention.
4. When it says it is done: run `./agent.ps1 status` and `./agent.ps1 check game` yourself, then `gradlew.bat runClient` and try the feature once.
5. Ask for feedback (the neutral prompt below) and save the answer next to the trial folder.
6. `powershell -NoProfile -File qa/local-model/Read-Trial.ps1 -Path <trial folder>` summarizes the run without anyone having watched it: time, every check in order (PASS/FAIL), STUCK, failed tests and whether `area` lines came with them, compile errors, every `api` lookup, generators, kit files the agent changed (pinned build files flagged; init/version edits recognized), the mod's size, export and TASK.md. Older kits: check reports only. It ends with the model side from OpenCode's session store (`Read-OpenCode.py`, needs Python; also runs alone): minutes spent generating, output tokens and their mix (thinking, file writes, text, tool calls), compactions, answers cut at the output limit, tool errors (failed edits), repeated identical reads, user messages.
7. Write `qa/local-model/results/<id>.md` (template below) from the summary, the feedback and the client check. Keep the trial folder until the cause is fixed. Which docs it read and its direct edits are in the frontend's session store, not in the trial folder.

Feedback prompt:

```text
That works, thanks. This was a test of the kit itself, which I'm still developing. Please give honest feedback on the kit, not on the mod. Don't change any files.
1. Where did you spend the most time or tokens, and why? Point to specific moments: commands, errors, files you read.
2. Where did you guess or rely on memory instead of the kit? Which of those guesses turned out wrong?
3. Was anything in the kit's docs or tool output missing, unclear, or wrong for this task? Quote the line if you can.
4. Which of your problems came from the kit, and which from your own reasoning? Say which is which.
5. What worked well and should stay as it is?
Rank the problems by what they cost you. For each one, suggest the smallest change to the kit that would have prevented it.
```

## Control run

Run each prompt the local model fails once more with a frontier agent (Claude Code) in another fresh copy of the same ZIP, same procedure. Both fail at the same point: the kit is the cause, fix it. Only the local model fails: mostly a model limit; fix the kit only if a fact, check or hint would plausibly have prevented it. Suffix result ids with the agent: `p3-local`, `p3-claude`.

## Standard setup (from 2026-10-18, runs 6/7 of results/beta25-s4-setup-ab.md)

Use this for every trial unless a result file says otherwise:
- Strata `strata-iq3_xxs.json`: Qwen3.8-Flash-Next IQ3_XXS, `--max-context 65536 --kv int8 --vram-reserve-mib 1536`, MTP `--spec 4 --spec-min-p 0.70`; sampling temperature 0.6, top_p 0.95, top_k 20, min_p 0.05; no `reasoning_effort` in the config.
- OpenCode 2.0.32 `opencode.json`: `"model": "local/qwen3.8-flash-next-iq3_xxs"` (provider/model; a bare id is skipped with a warning in OpenCode's log); context 65536, output 32768; `compaction`: `auto` true, `keep.tokens` 12000, `buffer` 20000 at 64k (32768 at 131k; 10000 was too small, see results/series3-local.md); `"modalities": { "input": ["text"], "output": ["text"] }` on the model (the server has no vision). Reasoning effort `high` (the model variant chosen in OpenCode). OpenCode 2.x drops `compaction.prune` and `tail_turns` ("omitted unsupported legacy setting" in `~/.local/share/opencode/log/opencode.log`): check that log for config diagnostics after any config change.

(Runs 5-7 had `prune: true`, which this OpenCode ignored.) It was within the noise of the old baseline (identical settings vary about 2x in time), follows the model's own sampling guidance, and was the setup of the only run where the model asked about unspecified gameplay. Change it only with 3+ runs per arm.

## Setup or kit? (A/B on the model setup)

Measured on the series 2 trials and reruns (kit beta.23-24; Strata, Qwen3.8-Flash-Next IQ3_XXS, OpenCode with 64k context):
- The model writing is the time: 59-95% of each trial's wall time was generation, and the server spent 600 min decoding (~82 tok/s) against 84 min reading prompts. `agent.ps1` was under 10% (S3 rerun: 8 of 148 min).
- Thinking is 61-90% of the output tokens. File writes are most of the rest (whole-file rewrites after failed edits).
- Compactions: up to 19 in one trial (S3 rerun), each followed by re-reading files and API dumps (44 repeated identical reads there).
- Failed edits: 21 of 43 in S3 (beta.23) before OpenCode's cache was cleared; 8 of 60 and 1 of 58 after.
- The server log warned 43 times that less than 1 GiB of VRAM was free (driver paging can drop decode to a few tok/s).

So a trial's length is mostly output tokens / decode speed, and most output is thinking. The kit shortens trials by preventing dead ends (wrong facts, flaky tests, missing checks); the setup decides how many tokens each step costs.

Procedure: same kit ZIP, same prompt, fresh folder, change one setting per run, write the setup into the result file. Screen with S4 from PROMPTS-2.md (short, ~10-20 min), confirm a winner once on S5 (~40-60 min); run-to-run spread is not measured yet, so run the baseline twice first, and count only changes clearly larger than that spread that do not add errors or worsen the outcome. Compare Read-Trial's numbers: outcome, wall and generating minutes, output tokens, thinking share, compactions, failed edits, repeated reads, checks, STUCK/FLAKY, user messages.

In order (cheapest and most likely first):
0. No A/B needed: give the GPU headroom; clear OpenCode's cache when edits keep failing. Applied 2026-10-18: `--vram-reserve-mib 1536` in strata-iq3_xxs.json (the log had suggested 1344-1664 MiB; it only moves speed, not answers beyond a slightly smaller expert cache). Trials before and after this differ in decode speed: compare tokens and errors across it, not minutes.
1. Thinking effort: OpenCode sends the model variant's effort (`medium` so far); Strata turns `low` into a "keep thinking brief" turn. Try `low`.
2. Compaction: with context 65536, `keep` 32768 and `buffer` 10000, a compaction comes roughly every 20k new tokens. Try a smaller `keep` (e.g. 12000), or a larger context if VRAM allows.
3. Sampling: Qwen's guidance for thinking mode (temperature 0.6, top_p 0.95, top_k 20, min_p 0; presence_penalty up to 1.5 against loops). The loops at 0.6 happened with the experimental control vector, now removed; 0.35 is below the guidance.
4. Quantization: only if failed edits and malformed tool calls stay high after 1-3; compare a larger quant on S4.
5. Ceiling: a Control run (above) on S3 or S5 shows what the kit costs with a strong model; the gap to the local run is setup plus model.

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
