package com.example.configmachine;

// Example tuning and world-direction defaults. All synchronized fields fit signed shorts.
public final class MachineSettings {
    public static final int CAPACITY=16000,RECEIVE=256,TICKS=100,RF_PER_TICK=20;
    public static final int TRANSFER_AMOUNT=8,TRANSFER_INTERVAL=10;
    public static int[] defaultFaces(){return new int[]{3,1,0,0,2,4};} // DOWN UP NORTH SOUTH WEST EAST
    private MachineSettings(){}
}
