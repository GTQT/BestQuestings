package betterquesting.api.questing;

import betterquesting.api.properties.IPropertyContainer;
import betterquesting.api2.storage.INBTPartial;
import betterquesting.api2.storage.IUuidDatabase;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;
import java.util.UUID;

public interface IQuestLine extends IUuidDatabase<IQuestLineEntry>, INBTPartial<NBTTagCompound, UUID>, IPropertyContainer {
    IQuestLineEntry createNew(UUID id);

    String getUnlocalisedName();

    String getUnlocalisedDescription();

    Map.Entry<UUID, IQuestLineEntry> getEntryAt(int x, int y);
}
