# Local-model trial series 1 (2026-10-16 to 10-18)

Held-out prompts P0-P7 (PROMPTS.md), OpenCode CLI, 64k context. One prompt per fresh kit copy; results verified by the user in `runClient`. Feedback claims were checked against the 1.12.2 source before the kit changed; several were wrong and are marked in each result.

| Trial | Kit | Model | Outcome | Time | Interventions | Main general kit change it caused |
|---|---|---|---|---|---|---|
| p0 copper | beta.10 | Qwen 27B IQ3 | works (ore slips) | n/a | 0 | vanilla ore drop/XP facts and generation table |
| p1 coin jar | beta.10 | Qwen 27B IQ3 (7 tok/s) | compiled, untested after 2 h 22 min | 142 min | stopped | generator-first wording; compile to check names; test early |
| p1-b | beta.11 | flash-next IQ3 (70 tok/s) | mod correct, stuck on own test | 60+ min | 1 (output limit) | test-world facts; then beta.13 area snapshot, STUCK, partial finish |
| p1-c | beta.13 | flash-next | works | 20 min | 0 | none needed |
| p2 magnet ring | beta.13 | flash-next | works | 33 min | 0 | failing test scaffold; t.messages; held-item ticking note |
| p3 growth totem | beta.14 | flash-next | works | 9 min | 0 | insertItem filter fact; api reads whole methods; private-access hint |
| p4 ember wisp | beta.15 | flash-next | works | 65 min | 0 | t.targetPlayer, t.loot; plan-time warning; later `new mob` generator |
| p5 teleport pads | beta.17 | flash-next | partly (needed loaded chunks) | 29 min | 0 (+ fix on request) | far-partner rule; empty-hand useOnBlock; api TestContext |
| p6 stamina | beta.18 | flash-next | works | 22 min | 0 | long api reads show start, end and Forge lines; look up behavior first |
| p7 vein miner | beta.20 | flash-next | works | 45 min | 0 (frontend stall) | net.minecraft.init fact; enchantment restriction guide; lang lint |

What changed most: from "a 27B model cannot finish a tile entity in 2 hours" to "a quantized local model finishes every remaining held-out mod, tested, mostly in 10-45 minutes". The biggest single levers were systemic, not facts: generator-first wording (stopped hand-writing boilerplate), the failure `area` snapshot plus STUCK (P4 used both to escape a dead end), and making the harness able to test what mods commonly need (messages, targeting, loot). Two weaker points remain: models still guess later-version names (cheap, caught by compiler hints) and trust memory over the source until told otherwise.

Not covered: graphical checks beyond the user's manual runClient passes; a frontier-model control run (unneeded once the local model passed); multiplayer behavior.

Next series: new held-out prompts (not seen by this kit's maintainer while fixing), ideally a second model family, run unattended and summarized with Read-Trial.ps1.
