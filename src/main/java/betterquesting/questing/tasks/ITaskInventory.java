package betterquesting.questing.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;

public interface ITaskInventory extends ITask {

    default void onInventoryChange(@Nonnull DBEntry<IQuest> quest, @Nonnull ParticipantInfo pInfo) {
        onInventoryChange(quest, pInfo,null);
    }

    void onInventoryChange(DBEntry<IQuest> quest, ParticipantInfo participant, List<ItemStack> changedItems);
}
