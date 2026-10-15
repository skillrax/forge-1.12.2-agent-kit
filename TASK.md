# Current maintainer task

Objective: Make the kit efficient and reliable for AI mod authoring, down to small local models (branch claude/kit-refactor, kit 0.5.0-beta.7).

Result: Field-test feedback (feedback/KIT-FEEDBACK-0543-16-10-26.txt, "Gron" mod) addressed and verified on this host.
- Bugs: unquoted comma lists work (A1); axe damage/speed follow vanilla per material (A2); generators reject unknown item references (A3); broken recipe/loot JSON is a check server ERROR (A4); check strips UTF-8 BOMs and says so (A5).
- api: class lookup shows declaration + private constants; `Class.new` shows constructors; private members fall back to source; `-Find` greps the source jar; output names the source (B1-B4).
- Generators: `-Like <vanilla material>` presets, `-Durability diamond`, `-Recipes` for tool/armor sets, vanilla reference tables, shaped placeholder textures (`-Look`), one-line output (`-List`), checks appended to GeneratedContentTest.java; `batch plan.txt` stops at first failure; TestContext craft/smelt/drops/count; status flags unfilled test scaffolds.
- Evidence: qa/unit Test-AgentInterface, Test-TestingTools (+broken recipe case), Test-MappedSource (+grep, nested head), Test-Packaging PASS; qa/integration/Test-KitSamples PASS (generators incl. tool/armor recipe sets and generated checks; guide samples).
- Cold laptop trial fixes: network failures named + one retry (beta.4); WinGet "already installed" rechecked, 60 s JDK probe, rejected JDKs explained (beta.5); first build 3600 s timeout + step-level progress (beta.6); Maven Central before Forge maven for libraries, cold build 631 s PASS (beta.7). ZIPs pass Test-KitZip.

Not done: full declarative mod.json + apply (C4; batch covers the minimum).

Next action: Finish the cold laptop trial (rerun check environment, or the beta.4 ZIP), then a 32k local-model trial; record tokens, interventions, check results.

Blocker: None. Not verified: graphical client, real local-model behavior.
