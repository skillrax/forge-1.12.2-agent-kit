# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.8).

Result: "Dense diamond" field test (feedback/feedback-20261016-102655.txt) addressed.
- Bug: tools/armor ignored -Speed -Damage -Enchant -AxeDamage -AxeSpeed -Toughness ($PSBoundParameters inside functions). Fixed with the script's bound set.
- Scope: `-Only` limits tools/armor to the pieces asked for; options that do not belong to the kind are rejected; tools/armor print a `stats:` line (tooltip damage, armor total, 20-point cap warning).
- Guidance: kit/AGENTS.md and GENERATORS.md say generators are for standard content only, custom behavior is hand-written from guides, "Nx vanilla" needs explicit numbers in TASK.md, and generated checks cannot catch wrong numbers.
- Evidence: qa/unit Test-AgentInterface (+field-test commands, -Only, wrong-kind option) and Test-Packaging PASS; qa/integration Test-KitSamples PASS (generators and guide samples, game tests).
- Earlier (beta.4-7): cold laptop trial fixes, Gron feedback A1-A5/B1-B4, presets, recipe sets, generated checks, batch.

Not done from the feedback: -Scale, `describe` (stats from the built jar), background/poll mode for long check game. Not done earlier: declarative mod.json + apply (C4).

Next action: A guide for complex custom items (region capture with tile entities/entities, removal, placement across dimensions, ghost preview), verified by a guide sample; then the 32k local-model trial.

Blocker: None. Not verified: graphical client, real local-model behavior.
