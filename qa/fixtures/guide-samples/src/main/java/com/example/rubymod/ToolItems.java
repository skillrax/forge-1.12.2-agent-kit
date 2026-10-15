package com.example.rubymod;

import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPickaxe;

/** ItemPickaxe and ItemAxe constructors are protected in 1.12; these public subclasses expose them. */
public final class ToolItems {
    public static class Pickaxe extends ItemPickaxe {
        public Pickaxe(ToolMaterial material) { super(material); }
    }

    /** Axes need explicit damage/speed: the one-argument constructor only works for vanilla materials. */
    public static class Axe extends ItemAxe {
        public Axe(ToolMaterial material, float damage, float speed) { super(material, damage, speed); }
    }

    private ToolItems() {}
}