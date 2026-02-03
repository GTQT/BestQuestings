package betterquesting.handlers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.List;

public class BQInventoryUpdateEvent extends Event {
    private final EntityPlayer player;
    private final List<ItemStack> changedItems;

    public BQInventoryUpdateEvent(EntityPlayer player, List<ItemStack> changedItems) {
        this.player = player;
        this.changedItems = changedItems;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    public List<ItemStack> getChangedItems() {
        return changedItems;
    }

}