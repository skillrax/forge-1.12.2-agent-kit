package com.example.rubymod;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootTableList;

/** Tougher zombie with its own loot table: assets/rubymod/loot_tables/entities/ruby_zombie.json */
public class RubyZombie extends EntityZombie {
    public static final ResourceLocation LOOT = LootTableList.register(new ResourceLocation(RubymodMod.MODID, "entities/ruby_zombie"));

    public RubyZombie(World world) { super(world); }   // entities need this (World) constructor

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40.0D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6.0D);
    }

    @Override
    protected ResourceLocation getLootTable() { return LOOT; }
}
