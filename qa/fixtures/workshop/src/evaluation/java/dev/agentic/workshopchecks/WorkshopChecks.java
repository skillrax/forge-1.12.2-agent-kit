package dev.agentic.workshopchecks;

import java.io.*;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.agentic.workshop.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid="workshopchecks",name="Workshop QA",version="1",dependencies="required-after:kitworkshop")
public final class WorkshopChecks {
    private static final BlockPos BOX=new BlockPos(0,80,0),PROC=new BlockPos(2,80,0);
    private WorldServer world;
    private ProcessorTile running;
    private StorageTile savedBox;
    private int assertions,ticks;
    private boolean live;
    private NpcChecks npc;
    private ProjectileChecks projectile;
    private WorldContentChecks worldContent;
    private final File expected=new File("expected-workshop.nbt");
    private void require(boolean ok,String message) {
        assertions++;if(!ok)throw new IllegalStateException("WORKSHOP_ASSERTIONS_FAIL: "+message);
    }
    private InventoryTile place(BlockPos pos,WorkshopBlock block) {
        world.setBlockToAir(pos);world.setBlockState(pos,block.getDefaultState(),3);
        return (InventoryTile)world.getTileEntity(pos);
    }
    private int count(IInventory inventory,Item item) {
        int count=0;for(int i=0;i<inventory.getSizeInventory();i++) {
            ItemStack stack=inventory.getStackInSlot(i);if(stack.getItem()==item)count+=stack.getCount();
        }return count;
    }
    private int drops(BlockPos pos,Item item) {
        int count=0;for(EntityItem entity:world.getEntitiesWithinAABB(EntityItem.class,new AxisAlignedBB(pos).grow(3)))
            if(entity.getItem().getItem()==item)count+=entity.getItem().getCount();return count;
    }
    private void staticChecks() {
        FakePlayer player=new FakePlayer(world,new GameProfile(UUID.nameUUIDFromBytes(new byte[]{1}),"WorkshopQA"));
        BlockPos pos=new BlockPos(8,80,0);player.setPosition(8.5,80,0.5);
        StorageTile box=(StorageTile)place(pos,Content.BOX);
        WorkshopContainer menu=new WorkshopContainer(player.inventory,box);
        player.inventory.setInventorySlotContents(9,new ItemStack(Items.DIAMOND,12));
        menu.slotClick(9,0,ClickType.QUICK_MOVE,player);
        require(count(box,Items.DIAMOND)==12 && count(player.inventory,Items.DIAMOND)==0,"Box shift-in accounting");
        menu.slotClick(0,0,ClickType.QUICK_MOVE,player);
        require(box.isEmpty() && count(player.inventory,Items.DIAMOND)==12,"Box shift-out accounting");
        player.inventory.clear();player.inventory.setItemStack(new ItemStack(Items.GOLD_INGOT,7));
        menu.slotClick(0,0,ClickType.PICKUP,player);menu.slotClick(0,0,ClickType.PICKUP,player);
        require(box.isEmpty() && player.inventory.getItemStack().getCount()==7,"Normal pickup conservation");
        player.inventory.setItemStack(ItemStack.EMPTY);
        require(menu.canInteractWith(player),"Nearby box rejected");
        player.setPosition(100,80,100);require(!menu.canInteractWith(player),"Distant menu allowed");
        player.setPosition(8.5,80,0.5);
        MenuHandler handler=new MenuHandler();
        require(handler.getServerGuiElement(0,player,world,8,80,0) instanceof WorkshopContainer,"Server GUI routing");
        require(handler.getServerGuiElement(1,player,world,8,80,0)==null,"Unknown GUI ID allowed");
        box.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,13));
        int before=drops(pos,Items.DIAMOND);world.setBlockToAir(pos);
        require(drops(pos,Items.DIAMOND)-before==13,"Broken box drops incorrect");
        require(!menu.canInteractWith(player),"Removed target menu allowed");
        world.setBlockToAir(pos);require(drops(pos,Items.DIAMOND)-before==13,"Duplicate break drops");
        place(pos,Content.BOX);require(!menu.canInteractWith(player),"Replaced target menu allowed");
        ProcessorTile machine=(ProcessorTile)place(pos,Content.PROCESSOR);
        WorkshopContainer processor=new WorkshopContainer(player.inventory,machine);
        player.inventory.clear();player.inventory.setInventorySlotContents(9,new ItemStack(Blocks.COBBLESTONE,8));
        processor.slotClick(2,0,ClickType.QUICK_MOVE,player);
        require(machine.getStackInSlot(0).getCount()==8 && player.inventory.getStackInSlot(9).isEmpty(),"Processor shift-in");
        player.inventory.setInventorySlotContents(9,new ItemStack(Items.DIAMOND,4));
        processor.slotClick(2,0,ClickType.QUICK_MOVE,player);
        require(player.inventory.getStackInSlot(9).getCount()==4 && machine.getStackInSlot(0).getCount()==8,"Invalid input moved");
        player.inventory.setItemStack(new ItemStack(Blocks.STONE,2));
        processor.slotClick(1,0,ClickType.PICKUP,player);
        require(machine.getStackInSlot(1).isEmpty() && player.inventory.getItemStack().getCount()==2,"Output accepts pickup insertion");
        processor.slotClick(1,0,ClickType.SWAP,player);
        require(machine.getStackInSlot(1).isEmpty(),"Output accepts hotbar insertion");
        player.inventory.setItemStack(ItemStack.EMPTY);
        machine.setInventorySlotContents(1,new ItemStack(Blocks.STONE,5));
        processor.slotClick(1,0,ClickType.QUICK_MOVE,player);
        require(machine.getStackInSlot(1).isEmpty() && count(player.inventory,Item.getItemFromBlock(Blocks.STONE))==5,"Output shift-out");
        machine.setField(0,37);machine.setInventorySlotContents(1,new ItemStack(Blocks.STONE,64));
        for(int i=0;i<120;i++)machine.update();
        require(machine.getField(0)==37 && machine.getStackInSlot(0).getCount()==8 && machine.getStackInSlot(1).getCount()==64,"Full output consumed work/items");
        machine.setInventorySlotContents(1,new ItemStack(Blocks.DIRT));machine.update();
        require(machine.getField(0)==37,"Wrong output did not pause");
        machine.setInventorySlotContents(1,ItemStack.EMPTY);
        for(int i=0;i<63;i++)machine.update();
        require(machine.getStackInSlot(0).getCount()==7 && machine.getStackInSlot(1).getCount()==1 && machine.getField(0)==0,"Resume or conversion accounting");
        machine.setField(0,37);machine.setInventorySlotContents(0,ItemStack.EMPTY);machine.update();
        require(machine.getField(0)==0,"Missing input retained progress");
        require(machine.canInsertItem(0,new ItemStack(Blocks.COBBLESTONE),EnumFacing.UP)
            && !machine.canInsertItem(1,new ItemStack(Blocks.STONE),EnumFacing.DOWN)
            && machine.canExtractItem(1,new ItemStack(Blocks.STONE),EnumFacing.DOWN),"Sided inventory restrictions");
        ProcessorTile restored=new ProcessorTile();machine.setField(0,45);restored.readFromNBT(machine.writeToNBT(new NBTTagCompound()));
        require(restored.getField(0)==45 && restored.getStackInSlot(1).getCount()==1,"NBT serialization");
        final int[] property={-1};
        processor.addListener(new IContainerListener() {
            public void sendAllContents(Container c,NonNullList<ItemStack> stacks) {}
            public void sendSlotContents(Container c,int slot,ItemStack stack) {}
            public void sendWindowProperty(Container c,int id,int value) {if(id==0)property[0]=value;}
            public void sendAllWindowProperties(Container c,IInventory inv) {}
        });
        require(property[0]==45,"Initial progress property missing");
        machine.setField(0,46);processor.detectAndSendChanges();require(property[0]==46,"Progress update property missing");
        int stoneBefore=drops(pos,Item.getItemFromBlock(Blocks.STONE));world.setBlockToAir(pos);
        require(drops(pos,Item.getItemFromBlock(Blocks.STONE))-stoneBefore==1,"Broken processor output drops");
    }
    @Mod.EventHandler public void start(FMLServerStartingEvent event) throws IOException {
        world=event.getServer().getWorld(0);world.getChunk(BOX);
        if("restore".equals(System.getProperty("workshop.phase"))) {
            NBTTagCompound snapshot;
            try(DataInputStream in=new DataInputStream(new FileInputStream(expected))) {snapshot=CompressedStreamTools.read(in);}
            require(world.getTileEntity(BOX) instanceof StorageTile && world.getTileEntity(PROC) instanceof ProcessorTile,"Tiles not restored from disk");
            savedBox=(StorageTile)world.getTileEntity(BOX);running=(ProcessorTile)world.getTileEntity(PROC);
            require(savedBox.writeToNBT(new NBTTagCompound()).equals(snapshot.getCompoundTag("Box")),"Box disk state differs");
            require(running.writeToNBT(new NBTTagCompound()).equals(snapshot.getCompoundTag("Processor")),"Processor disk state differs");
            require(running.getField(0)>0 && running.getField(0)<100,"No partial progress restored");
            int remainder=100-running.getField(0),oldInput=running.getStackInSlot(0).getCount();
            require(running.decrStackSize(1,64).getCount()==64,"Full saved output not restored");
            for(int i=0;i<remainder;i++)running.update();
            require(running.getStackInSlot(1).getCount()==1 && running.getStackInSlot(0).getCount()==oldInput-1,"Restored progress failed to resume");
            assertions+=new PouchChecks(world).restore();
            assertions+=new NpcChecks(world).restore(snapshot.getCompoundTag("NPC"));
            assertions+=new QuestChecks(world).restore();
            assertions+=new QuestChecks(world).resetRestore();
            assertions+=new WorldContentChecks(world).restore(snapshot.getCompoundTag("WorldContent"));
            assertions+=new DimensionChecks(world).restore();
            System.out.println("WORKSHOP_RESTART_PASS assertions="+assertions);return;
        }
        staticChecks();assertions+=new PouchChecks(world).live();npc=new NpcChecks(world);npc.liveStart();assertions+=new QuestChecks(world).live();savedBox=(StorageTile)place(BOX,Content.BOX);
        assertions+=new QuestChecks(world).resetChecks();projectile=new ProjectileChecks(world);projectile.liveStart();
        worldContent=new WorldContentChecks(world);assertions+=worldContent.live();
        savedBox.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,3));
        running=(ProcessorTile)place(PROC,Content.PROCESSOR);running.setInventorySlotContents(0,new ItemStack(Blocks.COBBLESTONE,2));
        MinecraftForge.EVENT_BUS.register(this);live=true;
    }
    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if(!live || event.world!=world || event.phase!=TickEvent.Phase.END)return;
        ticks++;
        npc.tick();
        projectile.tick();
        require(running.getField(0)==ticks%100,"Actual server progress mismatch at "+ticks);
        require(running.getStackInSlot(0).getCount()==2-ticks/100 && running.getStackInSlot(1).getCount()==ticks/100,"Actual server conversion accounting at "+ticks);
        if(ticks==135){
            assertions+=npc.finish();
            assertions+=projectile.finish();
            // Freeze partial progress through ordinary full-output behavior while shutdown output drains.
            running.setInventorySlotContents(1,new ItemStack(Blocks.STONE,64));
            assertions+=new DimensionChecks(world).live();
            live=false;System.out.println("WORKSHOP_LIVE_PASS ticks="+ticks+" assertions="+assertions);
        }
    }
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) throws IOException {
        if("restore".equals(System.getProperty("workshop.phase")) || running==null || ticks<135)return;
        NBTTagCompound snapshot=new NBTTagCompound();snapshot.setTag("Box",savedBox.writeToNBT(new NBTTagCompound()));
        snapshot.setTag("Processor",running.writeToNBT(new NBTTagCompound()));
        snapshot.setTag("NPC",npc.snapshot());
        snapshot.setTag("WorldContent",worldContent.snapshot());
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(expected))) {CompressedStreamTools.write(snapshot,out);}
    }
}
