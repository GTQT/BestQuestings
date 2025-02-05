package betterquesting.api.questing.tasks;

import betterquesting.api.questing.IQuest;
import net.minecraftforge.fluids.FluidStack;

import java.util.Map;
import java.util.UUID;

public interface IFluidTask extends ITask {
    boolean canAcceptFluid(UUID owner, Map.Entry<UUID, IQuest> quest, FluidStack fluid);

    FluidStack submitFluid(UUID owner, Map.Entry<UUID, IQuest> quest, FluidStack fluid);
}
