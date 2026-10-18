# GUIs: containers and screens

A block GUI has three parts: a **Container** (slots, both sides), a **GuiContainer** screen (client only) and an **IGuiHandler** that creates them. Larger working machine GUIs: examples/machine, examples/configurable-machine.

```java
public class RubyBoxContainer extends Container {
    private final RubyBoxTile tile;
    public RubyBoxContainer(InventoryPlayer playerInventory, RubyBoxTile tile) {
        this.tile = tile;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++)        // 3x3 box: slots 0-8
            addSlotToContainer(new SlotItemHandler(tile.inventory, col + row * 3, 62 + col * 18, 17 + row * 18));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)        // player inventory: 9-35
            addSlotToContainer(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)                                          // hotbar: 36-44
            addSlotToContainer(new Slot(playerInventory, col, 8 + col * 18, 142));
    }
    @Override public boolean canInteractWith(EntityPlayer player) { return !tile.isInvalid() && player.getDistanceSq(tile.getPos()) <= 64; }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {           // shift-click
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index < 9) { if (!mergeItemStack(stack, 9, 45, true)) return ItemStack.EMPTY; }
        else if (!mergeItemStack(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        return original;
    }
}

@SideOnly(Side.CLIENT)
public class RubyBoxScreen extends GuiContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/gui/container/dispenser.png");   // or mymod:textures/gui/box.png (256x256 canvas)
    public RubyBoxScreen(InventoryPlayer playerInventory, RubyBoxTile tile) { super(new RubyBoxContainer(playerInventory, tile)); xSize = 176; ySize = 166; }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground(); super.drawScreen(mouseX, mouseY, partialTicks); renderHoveredToolTip(mouseX, mouseY);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("tile.mymod.ruby_box.name"), 8, 6, 0x404040);
        fontRenderer.drawString(I18n.format("container.inventory"), 8, ySize - 94, 0x404040);
    }
}

public class ModGuiHandler implements IGuiHandler {           // common class; getClientGuiElement only runs on the client
    public static final int RUBY_BOX = 0;
    @Override public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        if (id == RUBY_BOX && tile instanceof RubyBoxTile) return new RubyBoxContainer(player.inventory, (RubyBoxTile) tile);
        return null;
    }
    @Override public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        if (id == RUBY_BOX && tile instanceof RubyBoxTile) return new RubyBoxScreen(player.inventory, (RubyBoxTile) tile);
        return null;
    }
}
```

Wire it: in preInit `NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE, new ModGuiHandler());`; in the block's `onBlockActivated`, on the server: `player.openGui(MyMod.INSTANCE, ModGuiHandler.RUBY_BOX, world, pos.getX(), pos.getY(), pos.getZ());` and return true.

## Progress bars and numbers

Sync small integers with container listeners: override `addListener`/`detectAndSendChanges` and call `listener.sendWindowProperty(this, id, value)`; receive them in `@SideOnly(Side.CLIENT) updateProgressBar(int id, int data)`. Values travel as signed 16-bit: split larger numbers (energy) into two fields. examples/machine/.../MachineContainer.java does this.

## Buttons

Screen buttons (`GuiButton` added in `initGui`, handled in `actionPerformed`) send `mc.playerController.sendEnchantPacket(inventorySlots.windowId, buttonId)`; the server gets `Container.enchantItem(EntityPlayer, int id)`: validate the id and change state there. Richer data needs a packet (guides/networking.md).

## Screens without a container

A settings or info screen extends `GuiScreen` (client only), opened from client code with `Minecraft.getMinecraft().displayGuiScreen(new MyScreen())`, e.g. from a keybind (guides/players.md). Override `doesGuiPauseGame()` to return false for a non-pausing screen.

## Test

Construct the container in a game test (`new RubyBoxContainer(player.inventory, tile)`, or `new ModGuiHandler().getServerGuiElement(id, player, world, x, y, z)` to test the handler, vanilla containers included) and call `transferStackInSlot`; `player.openGui` does nothing for a FakePlayer. Layout and textures need the client.
