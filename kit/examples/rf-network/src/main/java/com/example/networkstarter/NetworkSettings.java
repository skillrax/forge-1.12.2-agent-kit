package com.example.networkstarter;

/** Example tuning. Changing fuel-unit meaning requires an explicit saved-state migration. */
public final class NetworkSettings {
    public static final int CABLE_RATE=60000;
    public static final int STORAGE_CAPACITY=2000000, STORAGE_RECEIVE=60000, STORAGE_OUTPUT=20000;
    public static final int BASE_GENERATION=1000, UPGRADED_GENERATION=2000;
    public static final int BASE_FUEL_COST=4, UPGRADED_FUEL_COST=3;
    public static final int BASE_RF_PER_BURN_TICK=1000, BASE_RF_PER_LAVA_MB=1000;
    public static final int LAVA_FUEL_UNITS=BASE_RF_PER_LAVA_MB*BASE_FUEL_COST, TANK_CAPACITY=8000;
    public static final int SINK_DEFAULT=1000, SINK_LIMIT=100000;
    public static int[] defaultStorageFaces(){return new int[]{1,1,1,1,1,2};} // DOWN UP NORTH SOUTH WEST EAST: OFF=0 IN=1 OUT=2
    private NetworkSettings(){}
}
