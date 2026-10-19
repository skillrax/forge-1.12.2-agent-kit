# Blocks with behavior

Plain blocks and ores: generators. For custom behavior extend `BasicBlock` (or `Block`) and register with `block("name", new MyBlock())` in ModBlocks.

## Block states (facing, age, lit ...)

```java
public class FacingBlock extends BasicBlock {
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0, 0, 0, 1, 0.5, 1);       // half height

    public FacingBlock() {
        super(Material.WOOD, SoundType.WOOD, 2.0F, 2.0F);
        setDefaultState(blockState.getBaseState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, BlockHorizontal.FACING); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(BlockHorizontal.FACING, placer.getHorizontalFacing().getOpposite());   // front faces the player
    }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(BlockHorizontal.FACING).getHorizontalIndex(); }

    // Not a full cube: shape, light/culling and side solidity must all say so.
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return SHAPE; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }
    @SideOnly(Side.CLIENT) @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT; }   // transparent pixels
}
```

`blockstates/facing_block.json` needs every state (the server lint lists missing ones):

```json
{ "variants": {
  "facing=north": { "model": "mymod:facing_block" },
  "facing=east":  { "model": "mymod:facing_block", "y": 90 },
  "facing=south": { "model": "mymod:facing_block", "y": 180 },
  "facing=west":  { "model": "mymod:facing_block", "y": 270 } } }
```

Several properties: keys like `"facing=north,lit=true"` with property names in alphabetical order. Max 16 metadata values; properties derived from neighbors go in `getActualState` instead of meta. Other properties: `PropertyBool.create("lit")`, `PropertyInteger.create("level", 0, 7)`, `PropertyEnum.create(...)`.

## Interaction, ticks, redstone

- Right-click: `onBlockActivated(...)` returns true on both sides; change things only when `!world.isRemote`. While sneaking it is only called if both hands are empty (or both held items return true from `doesSneakBypassUse`); otherwise the held item is used instead (a block item gets placed). So "sneak + right-click" interactions need an empty hand.
- Timed behavior: `world.scheduleUpdate(pos, this, delayTicks)` then `updateTick`; random ticks: `setTickRandomly(true)` (crop-like).
- Redstone input: `world.isBlockPowered(pos)` in `neighborChanged` (any side); one side only: `world.getRedstonePower(pos.offset(side), side)`. Output: `canProvidePower` true + `getWeakPower(state, world, pos, side)`, where `side` points from the block reading the power toward yours (it powers `pos.offset(side.getOpposite())`); after the output changes call `world.notifyNeighborsOfStateChange(pos, this, false)`. Vanilla diodes (repeater, comparator) name their input side `FACING`, output on `FACING.getOpposite()`, placed as `placer.getHorizontalFacing().getOpposite()` so the signal runs the way the player looked. Comparator output is on the Block: `hasComparatorInputOverride(state)` true + `getComparatorInputOverride(state, world, pos)` returning 0-15 (`ItemHandlerHelper.calcRedstoneFromInventory(handler)` for an inventory); the tile's `markDirty()` tells comparators to re-read it.
- Light: `.light(1.0F)` on BasicBlock; for a lit/unlit state override `getLightValue(IBlockState, IBlockAccess, BlockPos)`.
- Change state: `world.setBlockState(pos, state.withProperty(LIT, true), 3)` (3 = notify + send to clients).

## Drops

Default drop is the block's item. Override `getItemDropped`, `quantityDropped`, `damageDropped` (metadata), or `getDrops(NonNullList<ItemStack>, IBlockAccess, BlockPos, IBlockState, int fortune)`. Mining requirements: `.harvest("pickaxe", level)`; without the right tool there are no drops. To change vanilla blocks' drops use `BlockEvent.HarvestDropsEvent` (guides/events.md).

## Crops

```java
public class RubyCrop extends BlockCrops implements ModBlocks.NoItem {   // the seeds item places it
    @Override protected Item getSeed() { return ModItems.RUBY_SEEDS; }
    @Override protected Item getCrop() { return ModItems.RUBY; }
    @Override public EnumPlantType getPlantType(IBlockAccess world, BlockPos pos) { return EnumPlantType.Crop; }   // default Plains pops off farmland
}
// ModItems: item("ruby_seeds", new ItemSeeds(ModBlocks.RUBY_CROP, Blocks.FARMLAND))
```

Blockstate needs `age=0` ... `age=7`. Another stage count: a `static final PropertyInteger AGE = PropertyInteger.create("age", 0, 3)` (static, because the BlockCrops constructor already reads it) returned by overrides of `getAgeProperty()`, `getMaxAge()` (3) and `createBlockState()` (`new BlockStateContainer(this, AGE)`); more than 8 stages also needs `getBoundingBox` (vanilla indexes an 8-entry table). Growth needs light 9+ above the crop (`getLightFromNeighbors(pos.up())`); with light under 8 and no sky the crop pops off (`canBlockStay`). Models can use parent `block/crop` with texture key `crop`. Mature harvest drops the crop item plus 0-3 seeds.

## Test

Place with `useOnBlock(player, ground, EnumFacing.UP, hand)` holding the block item (runs real placement), then check `state(pos)`. Break with `breakBlock` (needs the right tool) or `world.destroyBlock(pos, true)`, then look for `EntityItem` drops.
