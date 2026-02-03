package betterquesting.handlers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.List;

public class BQFluidInventoryUpdateEvent extends Event {
    private final EntityPlayer player;
    private final List<FluidStack> changedFluids;

    public BQFluidInventoryUpdateEvent(EntityPlayer player, List<FluidStack> changedFluids) {
        this.player = player;
        this.changedFluids = changedFluids;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    public List<FluidStack> getChangedFluids() {
        return changedFluids;
    }

}