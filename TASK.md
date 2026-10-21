# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (main, kit 0.5.7).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result:
- Series 1-3 and the Claude 0.5.4 baseline: qa/local-model/results and git history. 0.5.6: server-side items from the Sophisticated Backpacks port (feedback/backport-sophisticated-backpacks-20261021.md).
- 0.5.7: `check client` tests GUI container sync (the port's worst bug class: client slot layout out of step). Every project block/item overriding the right-click hook is used by the real player. Each opened container is compared client vs server after opening, after a server fill and after 3 real clicks. Verified: a client-only layout in RubyBoxContainer FAILs (Test-KitSamples -Client); guide samples and examples machine, configurable-machine, rf-network PASS (5 GUIs).
- Item-opened GUI checked by hand (bag item in a scratch copy): PASS in sync, FAIL with a client-only slot. Evidence: qa/Run-All.ps1 all 6 suites PASS 2026-10-21 (Test-AgentInterface needs kit/.kit/api-index.tsv: run kit/agent.ps1 check environment once in a fresh worktree).

Next action: fixtures for an item-opened GUI and for a layout that changes after a click (the port's exact bug); both are unautomated. Then configurable client scenes (open screen X, hover item), optional mods at runtime in dev; share 0.5.7 with friends; local-model reruns when the PC is free.

Blocker: None. Not verified: graphical client by a person: manual runClient check per release.
