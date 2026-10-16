# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.15).

Direction (user, 2026-10-16): the kit must let an AI build mods it has never seen. Turn each field test into general facts, checks or hints where an AI gets stuck, never into a feature or guide for that one mod. Generators stay small, for standard content only.

Result:
- beta.10-11: snippet check, -Only required; generator-first wording, compile-to-check names, ore/comparator/sneak facts.
- p1-local-b: stuck 40 min on its own test (comparator powered a hopper); beta.12 test-world facts.
- beta.13 (systemic): failed game tests print `area` (placed blocks with state, redstone received, inventory; entities); STUCK after 3 identical failures; AGENTS.md step 7: a partial report is a valid finish. Probe: the p1-local-b trap shows the whole power chain in one failure.
- p1-local-c (20 min) and p2-local (33 min) pass on beta.13, runClient verified. beta.14 from P2 feedback: failing test scaffold; client-only NoSuchMethodError hint; t.messages(player); held-item onUpdate note; api covers any class.
- p3-local passes on beta.14 in 9 min. beta.15 from its feedback: "has private access" hint; api reads to the closing brace, shows overloads, finds interface methods; insertItem-not-isItemValid filter fact with fixture test; random-tick test note.
- Evidence: Test-GuideSnippets, Test-AgentInterface, Test-TestingTools, Test-MappedSource (now UTF-8 safe), Test-Packaging, Test-KitSamples PASS.

Next action: P4-P7 on beta.15, same model and limits; record STUCK/area use. Deferred: static client-only member lint.

Blocker: None. Not verified: graphical client: manual runClient check per release.
