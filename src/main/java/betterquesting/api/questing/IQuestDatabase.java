package betterquesting.api.questing;

import betterquesting.api2.storage.INBTPartial;
import betterquesting.api2.storage.INBTProgress;
import betterquesting.api2.storage.IUuidDatabase;
import net.minecraft.nbt.NBTTagList;

import java.util.UUID;

public interface IQuestDatabase extends IUuidDatabase<IQuest>, INBTPartial<NBTTagList, UUID>, INBTProgress<NBTTagList> {
    IQuest createNew(UUID id);
}
