# Human extension prompt (verbatim)

"Lets add functionality. I want the bottle to be able to be shift right clicked on a furnace to accelerate by 2x for 15 seconds, costing 30 seconds of charge. Further shift right clicks on the block should accelerate twice speed that for twice cost refreshing the duration to 15 seconds but at the new tick speed multiplier. Lets also change the recipe to include 6 diamonds filling the rest of the slots. You can decide the pattern."

Clarified: 15 normal game seconds, fuel/cooking accelerated together, escalation resets after expiry. First2x/30-second cost, then4x/60,8x/120 etc. Successful activations alone refresh/advance. Sequence is per furnace; any subsequent player pays the next full cost. Agent safeguards/choices are recorded in the trial report, not inserted into the user prompt.

Starting point: accepted Clockwork Bottle1.0.1 source ZIP; new toolkitalpha3 extraction; extension output mod1.1.0. No competitor source or acceleration solution supplied. Warm host/existing authoring chat, not a fresh-model autonomy claim.