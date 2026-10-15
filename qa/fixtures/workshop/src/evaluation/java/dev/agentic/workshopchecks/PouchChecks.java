package dev.agentic.workshopchecks;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.agentic.workshop.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerInteractionManager;

public final class PouchChecks {
    private final WorldServer world;
    private int assertions;
    public PouchChecks(WorldServer world) {this.world=world;}
    private void require(boolean ok,String message) {
        assertions++;if(!ok)throw new IllegalStateException("WORKSHOP_ASSERTIONS_FAIL: Pouch "+message);
    }
    private EntityPlayerMP player(String name) {
        EntityPlayerMP player=new EntityPlayerMP(world.getMinecraftServer(),world,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name),new PlayerInteractionManager(world));
        player.connection=new NetHandlerPlayServer(world.getMinecraftServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),player);
        player.setPosition(0,80,0);return player;
    }
    private int count(IInventory inv,Item item) {
        int n=0;for(int i=0;i<inv.getSizeInventory();i++)if(inv.getStackInSlot(i).getItem()==item)n+=inv.getStackInSlot(i).getCount();return n;
    }
    private PouchContainer open(EntityPlayerMP p,int slot) {
        p.inventory.currentItem=slot;return new PouchContainer(p,slot);
    }
    public int live() {
        EntityPlayerMP p=player("PouchLive");p.inventory.setInventorySlotContents(0,new ItemStack(Content.POUCH));
        p.inventory.setInventorySlotContents(1,new ItemStack(Content.POUCH));
        PouchContainer a=open(p,0);
        require(Content.POUCH.getItemStackLimit()==1,"stackable item");
        p.inventory.setInventorySlotContents(9,new ItemStack(Items.DIAMOND,12));
        a.slotClick(3,0,ClickType.QUICK_MOVE,p);
        require(count(a.pouch,Items.DIAMOND)==12 && count(p.inventory,Items.DIAMOND)==0,"shift-in conservation");
        PouchContainer b=open(p,1);require(b.pouch.isEmpty(),"second pouch shares first contents");
        b.pouch.setInventorySlotContents(0,new ItemStack(Items.GOLD_INGOT,7));
        a=open(p,0);require(count(a.pouch,Items.DIAMOND)==12 && count(a.pouch,Items.GOLD_INGOT)==0,"independent reopened contents");
        a.slotClick(0,0,ClickType.QUICK_MOVE,p);
        require(a.pouch.isEmpty() && count(p.inventory,Items.DIAMOND)==12,"shift-out conservation");
        p.inventory.setItemStack(new ItemStack(Items.EMERALD,6));
        a.slotClick(0,0,ClickType.PICKUP,p);require(p.inventory.getItemStack().isEmpty(),"pickup insertion");
        a.slotClick(0,0,ClickType.PICKUP,p);
        require(a.pouch.isEmpty() && p.inventory.getItemStack().getCount()==6,"pickup extraction");
        p.inventory.setItemStack(ItemStack.EMPTY);
        a.pouch.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,5));
        p.inventory.setInventorySlotContents(2,new ItemStack(Items.GOLD_INGOT,4));
        a.slotClick(0,2,ClickType.SWAP,p);
        require(a.pouch.getStackInSlot(0).getItem()==Items.GOLD_INGOT && a.pouch.getStackInSlot(0).getCount()==4
            && p.inventory.getStackInSlot(2).getItem()==Items.DIAMOND && p.inventory.getStackInSlot(2).getCount()==5,"hotbar swap conservation");
        ItemStack active=p.inventory.getStackInSlot(0);NBTTagCompound before=active.writeToNBT(new NBTTagCompound());
        for(ClickType type:new ClickType[]{ClickType.PICKUP,ClickType.QUICK_MOVE,ClickType.THROW,ClickType.SWAP,ClickType.CLONE}) {
            a.slotClick(30,0,type,p);
            require(p.inventory.getStackInSlot(0)==active && active.writeToNBT(new NBTTagCompound()).equals(before),"active slot changed via "+type);
        }
        a.slotClick(0,0,ClickType.SWAP,p);
        require(p.inventory.getStackInSlot(0)==active && a.pouch.getStackInSlot(0).getItem()==Items.GOLD_INGOT,"active hotbar swap allowed");
        a.slotClick(31,0,ClickType.QUICK_MOVE,p);require(p.inventory.getStackInSlot(1).getItem()==Content.POUCH,"nested pouch shift-in");
        a.pouch.clear();p.inventory.setItemStack(new ItemStack(Content.POUCH));
        a.slotClick(0,0,ClickType.PICKUP,p);require(a.pouch.isEmpty() && p.inventory.getItemStack().getItem()==Content.POUCH,"nested pouch pickup");
        p.inventory.setItemStack(new ItemStack(Blocks.WHITE_SHULKER_BOX));
        a.slotClick(0,0,ClickType.PICKUP,p);require(a.pouch.isEmpty(),"nested shulker pickup");
        p.inventory.setItemStack(new ItemStack(Items.EMERALD,6));
        // Vanilla QUICK_CRAFT mask: low bits are stage, upper bits drag mode; mode0 is equal distribution.
        a.slotClick(-999,0,ClickType.QUICK_CRAFT,p);a.slotClick(0,1,ClickType.QUICK_CRAFT,p);
        a.slotClick(1,1,ClickType.QUICK_CRAFT,p);a.slotClick(2,1,ClickType.QUICK_CRAFT,p);
        a.slotClick(-999,2,ClickType.QUICK_CRAFT,p);
        require(count(a.pouch,Items.EMERALD)==6 && p.inventory.getItemStack().isEmpty(),"drag conservation");
        require(count(open(p,0).pouch,Items.EMERALD)==6,"drag saved state");
        a.pouch.removeStackFromSlot(0);require(count(open(p,0).pouch,Items.EMERALD)==4,"removeStack persistence");
        a.pouch.clear();require(open(p,0).pouch.isEmpty(),"clear persistence");
        a=open(p,0);a.pouch.setInventorySlotContents(0,new ItemStack(Items.EMERALD,9));
        for(int i=1;i<36;i++)p.inventory.setInventorySlotContents(i,new ItemStack(Blocks.DIRT,64));
        a.slotClick(0,0,ClickType.QUICK_MOVE,p);require(count(a.pouch,Items.EMERALD)==9,"full inventory lost output");
        p.inventory.setItemStack(new ItemStack(Items.DIAMOND,3));
        p.inventory.currentItem=1;
        NBTTagCompound stale=active.writeToNBT(new NBTTagCompound());
        a.slotClick(1,0,ClickType.PICKUP,p);
        require(!a.canInteractWith(p) && p.inventory.getItemStack().getCount()==3 && active.writeToNBT(new NBTTagCompound()).equals(stale),"selected-slot invalidation");
        p.inventory.currentItem=0;p.inventory.setItemStack(ItemStack.EMPTY);
        a=open(p,0);p.inventory.setInventorySlotContents(0,active.copy());
        a.slotClick(0,0,ClickType.QUICK_MOVE,p);
        require(!a.canInteractWith(p) && count(a.pouch,Items.EMERALD)==9,"replacement stack invalidation");
        p.openContainer=a;p.onUpdate();require(p.openContainer==p.inventoryContainer,"vanilla server failed to close stale menu");
        p.inventory.clear();p.inventory.setInventorySlotContents(0,active);a=open(p,0);
        EntityItem dropped=p.dropItem(true);
        require(dropped!=null && p.inventory.getStackInSlot(0).isEmpty() && !a.canInteractWith(p),"drop invalidation");
        NBTTagCompound entityTag=new NBTTagCompound();dropped.writeToNBT(entityTag);
        EntityItem restoredEntity=new EntityItem(world);restoredEntity.readFromNBT(entityTag);
        p.inventory.setInventorySlotContents(2,restoredEntity.getItem());
        require(count(open(p,2).pouch,Items.EMERALD)==9,"drop/move NBT preservation");
        EntityPlayerMP other=player("PouchOther");other.inventory.setInventorySlotContents(0,new ItemStack(Content.POUCH));
        require(open(other,0).pouch.isEmpty(),"cross-player state leakage");
        // Save two distinct pouches through vanilla player .dat for a separate JVM restore.
        EntityPlayerMP saved=player("PouchPersist");saved.inventory.setInventorySlotContents(0,new ItemStack(Content.POUCH));
        saved.inventory.setInventorySlotContents(1,new ItemStack(Content.POUCH));
        open(saved,0).pouch.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,11));
        open(saved,1).pouch.setInventorySlotContents(2,new ItemStack(Items.GOLD_INGOT,7));
        world.getSaveHandler().getPlayerNBTManager().writePlayerData(saved);
        System.out.println("POUCH_LIVE_PASS assertions="+assertions);return assertions;
    }
    public int restore() {
        EntityPlayerMP p=player("PouchPersist");world.getSaveHandler().getPlayerNBTManager().readPlayerData(p);
        require(p.inventory.getStackInSlot(0).getItem()==Content.POUCH && p.inventory.getStackInSlot(1).getItem()==Content.POUCH,"player disk items missing");
        PouchContainer a=open(p,0);require(count(a.pouch,Items.DIAMOND)==11 && count(a.pouch,Items.GOLD_INGOT)==0,"first disk contents");
        PouchContainer b=open(p,1);require(count(b.pouch,Items.GOLD_INGOT)==7 && count(b.pouch,Items.DIAMOND)==0,"second disk contents");
        a=open(p,0);a.slotClick(0,0,ClickType.QUICK_MOVE,p);
        require(a.pouch.isEmpty() && count(p.inventory,Items.DIAMOND)==11,"restored pouch transfer");
        require(count(open(p,1).pouch,Items.GOLD_INGOT)==7,"restored pouches remain independent");
        System.out.println("POUCH_RESTART_PASS assertions="+assertions);return assertions;
    }
}
