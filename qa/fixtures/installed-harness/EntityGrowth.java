package example.installedtests;

import dev.agentic.installedtest.*;
import net.minecraft.entity.passive.EntityPig;

/** Harness generality check: vanilla entity ticking, not a custom-entity gameplay claim. */
@InstalledTest("entity-growth")
public final class EntityGrowth implements InstalledScenario {
    private EntityPig pig;
    @Override public void plan(TestContext t) {
        t.then("spawn",()->{
            t.place(t.origin().down(),"minecraft:stone");
            pig=new EntityPig(t.world());pig.setPosition(t.origin().getX()+0.5,100,t.origin().getZ()+0.5);
            pig.setGrowingAge(-6);
            if(!t.world().spawnEntity(pig))throw new AssertionError("Spawn rejected");
        });
        t.invariant("alive",()->!pig.isDead,()->"Pig must remain alive");
        t.waitTicks(2);
        t.waitUntil("grown",20,()->!pig.isChild(),()->"age="+pig.getGrowingAge());
        t.check("world-tracked",()->t.world().getEntityByID(pig.getEntityId())==pig,()->"Entity remains in the actual world");
    }
}
