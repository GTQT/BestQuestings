package betterquesting.api.questing;

import betterquesting.api2.storage.INBTPartial;
import betterquesting.api2.storage.IUuidDatabase;
import net.minecraft.nbt.NBTTagList;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IQuestLineDatabase extends IUuidDatabase<IQuestLine>, INBTPartial<NBTTagList, UUID> {
    IQuestLine createNew(UUID id);

    /**
     * Deletes quest from all quest lines
     */
    void removeQuest(UUID questID);

    int getOrderIndex(UUID lineID);

    void setOrderIndex(UUID lineID, int index);

    List<Map.Entry<UUID, IQuestLine>> getOrderedEntries();

}
