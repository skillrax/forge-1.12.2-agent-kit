# Entity ticks, merchants and player state

For 1.12.2/stable39 entity, trading or per-player features. Scoped accepted patterns, not mandatory runtime libraries. Verify exact members with tools/Read-MappedSource.ps1.

## Actual lifecycle

World.updateEntityWithOptionalForce requires the surrounding 32-block area loaded for ordinary entity updates. In QA, prove ticks using ticksExisted and a bounded loaded fixture area before diagnosing AI; never force production chunks to satisfy a test. Include age, position, grounded/NoAI and task/navigation state in failures. Player dimension transfers can unload chunks used by another fixture: finish unrelated tick scenarios first.

FakePlayer.onUpdate is empty. It cannot prove ordinary player ticking, menu closure, logout/login or networking. Use the lifecycle path relevant to the assertion and keep real client checks separate. PlayerList.playerLoggedOut supplies logout ordering; do not assume EntityPlayerMP.onDisconnect exists. Iterate a snapshot when menu closure may spawn dropped items into the entity collection.

Pinned lookups: net.minecraft.world.World/updateEntityWithOptionalForce; net.minecraftforge.common.util.FakePlayer/onUpdate; net.minecraft.server.management.PlayerList/playerLoggedOut. For absent members, use bounded declaration discovery instead of repeated class reads.

## Vanilla trade accounting

Prefer ContainerMerchant/GuiMerchant when they fit. Inspect net.minecraft.inventory.SlotMerchantResult/onTake, net.minecraft.inventory.ContainerMerchant/onContainerClosed and net.minecraft.village.MerchantRecipe/incrementToolUses. Vanilla result pickup consumes payment and calls IMerchant.useRecipe; the merchant implementation must update persisted uses exactly once. Test payment, result, uses, exhaustion, close/payment recovery and reload together. Release the previous customer's menu before assigning another: closing the old menu after assignment can clear the new customer. Include competing players, distance/world change, death and logout where relevant.

## Repeatable per-player state

For a key-opened personal menu and sender-owned packet actions, use [personal-menus.md](personal-menus.md). The accepted pattern stores a namespaced compound under EntityPlayer.PERSISTED_NBT_TAG, attaches missing compounds explicitly and uses normal player saves. Pinned EntityPlayerMP.copyFrom already transfers that section on replacement; do not add a duplicate clone handler for it. Ownership follows the server UUID.

Store progress under persisted player NBT and handle clone/replacement deliberately. For repeatable state trials, include an operator/cheats reset, self-reset and optionally a named online target. Reset only selected feature progress with explicit feedback, preserving other players, unrelated data and inventory. Reset enables another reward cycle; it does not revoke previous rewards. Offline editing is outside this minimal pattern. Verify isolation, fresh accept/complete replay and disk persistence after reset.

CommandBase defaults to permission level 4. Override getRequiredPermissionLevel explicitly (2 only if intended) and exercise the registered dispatcher with granted and denied senders. Include required level, result and target in failures. Direct command-method calls do not prove dispatcher permission enforcement.
