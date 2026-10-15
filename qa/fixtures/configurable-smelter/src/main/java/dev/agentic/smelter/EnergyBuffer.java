package dev.agentic.smelter;

import net.minecraftforge.energy.EnergyStorage;

// Exposed transfer limits and internal generation/spending are separate operations.
public final class EnergyBuffer extends EnergyStorage {
    private final Runnable changed;
    public EnergyBuffer(int capacity,int receive,int extract,Runnable changed) {
        super(capacity,receive,extract);this.changed=changed;
    }
    @Override public int receiveEnergy(int amount,boolean simulate) {
        int accepted=super.receiveEnergy(Math.max(0,amount),simulate);
        if(!simulate && accepted>0)changed.run();return accepted;
    }
    @Override public int extractEnergy(int amount,boolean simulate) {
        int extracted=super.extractEnergy(Math.max(0,amount),simulate);
        if(!simulate && extracted>0)changed.run();return extracted;
    }
    public int generate(int amount) {
        int accepted=Math.min(Math.max(0,amount),capacity-energy);
        energy+=accepted;if(accepted>0)changed.run();return accepted;
    }
    public boolean spend(int amount) {
        if(amount<0 || energy<amount)return false;
        energy-=amount;if(amount>0)changed.run();return true;
    }
    public void restore(int amount) {energy=Math.max(0,Math.min(capacity,amount));}
}
