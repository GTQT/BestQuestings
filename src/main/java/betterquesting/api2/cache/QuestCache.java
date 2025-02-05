package betterquesting.api2.cache;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumQuestVisibility;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.storage.IUuidDatabase;
import betterquesting.network.handlers.NetCacheSync;
import betterquesting.questing.QuestDatabase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.*;
import java.util.function.BiConsumer;

public class QuestCache implements INBTSerializable<NBTTagCompound> {
    // Quests that are visible to the player
    private final HashSet<UUID> visibleQuests = new HashSet<>();

    // Quests that are currently being undertaken. NOTE: Quests can be locked but still processing data if configured to do so
    private final HashSet<UUID> activeQuests = new HashSet<>();

    // Quests and their scheduled time of being reset
    private final TreeSet<QResetTime> resetSchedule = new TreeSet<>((o1, o2) -> o1.questID.equals(o2.questID) ? 0 : Long.compare(o1.time, o2.time));

    // Quests with pending auto claims (usually should be empty unless a condition needs to be met)
    private final HashSet<UUID> autoClaims = new HashSet<>();

    // Quests that need to be sent to the client to update progression (NOT for edits. Handle that elsewhere)
    private final HashSet<UUID> markedDirty = new HashSet<>();

    // TODO: Make this based on a fixed state stored on the quest instead of calculated on demand
    // TODO: Also make this thread safe
    public static boolean isQuestShown(IQuest quest, UUID uuid, EntityPlayer player) {
        if (quest == null || uuid == null) {
            return false;
        }

        EnumQuestVisibility vis = quest.getProperty(NativeProps.VISIBILITY);

        if (QuestingAPI.getAPI(ApiReference.SETTINGS).canUserEdit(player) ||
                (BQ_Settings.viewMode && !quest.getProperty(NativeProps.IGNORES_VIEW_MODE) ||
                        vis == EnumQuestVisibility.ALWAYS)
        ) // Always shown or in edit mode, or in view mode if not overridden
        {
            return true;
        } else if (vis == EnumQuestVisibility.HIDDEN) {
            return false;
        } else if (vis == EnumQuestVisibility.UNLOCKED) {
            return quest.isComplete(uuid) || quest.isUnlocked(uuid);
        } else if (vis == EnumQuestVisibility.NORMAL) {
            if (quest.isComplete(uuid) || quest.isUnlocked(uuid)) // Complete or pending
            {
                return true;
            }

            // Previous quest is underway and this one is visible but still locked (foreshadowing)
            return QuestDatabase.INSTANCE.getAll(quest.getRequirements())
                    .allMatch(q -> q.isUnlocked(uuid));
        } else if (vis == EnumQuestVisibility.COMPLETED) {
            return quest.isComplete(uuid);
        } else if (vis == EnumQuestVisibility.CHAIN) {
            if (quest.getRequirements().isEmpty()) {
                return true;
            }

            return QuestDatabase.INSTANCE.getAll(quest.getRequirements())
                    .anyMatch(q -> isQuestShown(q, uuid, player));
        }

        return true;
    }

    public synchronized Set<UUID> getActiveQuests() {
        return activeQuests;
    }

    public synchronized Set<UUID> getVisibleQuests() {
        return visibleQuests;
    }

    public synchronized Set<UUID> getPendingAutoClaims() {
        return autoClaims;
    }

    public synchronized QResetTime[] getScheduledResets() // Already sorted by time
    {
        return resetSchedule.toArray(new QResetTime[0]);
    }

    public synchronized void markQuestDirty(UUID questID) {
        markedDirty.add(questID);
    }

    public synchronized void markQuestClean(UUID questID) {
        markedDirty.remove(questID);
    }

    public synchronized void cleanAllQuests() {
        markedDirty.clear();
    }

    public Set<UUID> getDirtyQuests() {
        return markedDirty;
    }

    // TODO: Ensure this is thread safe because we're likely going to run this in the background
    // NOTE: Only run this when the quests completion and claim states change. Use markQuestDirty() for progression changes that need syncing
    public synchronized void updateCache(EntityPlayer player) {
        if (player == null) return;

        UUID uuid = QuestingAPI.getQuestingUUID(player);
        Set<Map.Entry<UUID, IQuest>> questDB = QuestingAPI.getAPI(ApiReference.QUEST_DB).entrySet();

        List<UUID> tmpVisible = new ArrayList<>();
        List<UUID> tmpActive = new ArrayList<>();
        NonNullList<QResetTime> tmpReset = NonNullList.create();
        List<UUID> tmpAutoClaim = new ArrayList<>();

        for (Map.Entry<UUID, IQuest> entry : questDB) {
            if (entry.getValue().isUnlocked(uuid) || entry.getValue().isComplete(uuid) || entry.getValue().getProperty(NativeProps.LOCKED_PROGRESS)) { // Unlocked or actively processing progression data
                int repeat = entry.getValue().getProperty(NativeProps.REPEAT_TIME);
                NBTTagCompound ue = entry.getValue().getCompletionInfo(uuid);

                if ((ue == null && entry.getValue().getTasks().size() <= 0) || entry.getValue().canSubmit(player)) { // Can be active without completion in the case of locked progress. Also account for taskless quests
                    tmpActive.add(entry.getKey());
                } else if (ue != null) { // These conditions only trigger after first completion
                    if (repeat >= 0 && entry.getValue().hasClaimed(uuid)) {
                        long altTime = ue.getLong("timestamp");
                        if (repeat > 1 && !entry.getValue().getProperty(NativeProps.REPEAT_REL))
                            altTime -= (altTime % repeat);
                        tmpReset.add(new QResetTime(entry.getKey(), altTime + (repeat * 50L)));
                    }

                    if (!entry.getValue().hasClaimed(uuid) && entry.getValue().getProperty(NativeProps.AUTO_CLAIM)) {
                        tmpAutoClaim.add(entry.getKey());
                    }
                }
            }

            if (isQuestShown(entry.getValue(), uuid, player)) {
                tmpVisible.add(entry.getKey());
            }
        }

        visibleQuests.clear();
        visibleQuests.addAll(tmpVisible);

        activeQuests.clear();
        activeQuests.addAll(tmpActive);

        resetSchedule.clear();
        resetSchedule.addAll(tmpReset);

        autoClaims.clear();
        autoClaims.addAll(tmpAutoClaim);

        if (player instanceof EntityPlayerMP) NetCacheSync.sendSync((EntityPlayerMP) player);
    }

    @Override
    public synchronized NBTTagCompound serializeNBT() {
        NBTTagCompound tags = new NBTTagCompound();

        tags.setTag("visibleQuests", NBTConverter.UuidValueType.QUEST.writeIds(getVisibleQuests()));
        tags.setTag("activeQuests", NBTConverter.UuidValueType.QUEST.writeIds(getActiveQuests()));
        tags.setTag("autoClaims", NBTConverter.UuidValueType.QUEST.writeIds(getPendingAutoClaims()));
        tags.setTag("markedDirty", NBTConverter.UuidValueType.QUEST.writeIds(getDirtyQuests()));

        NBTTagList tagSchedule = new NBTTagList();
        for (QResetTime entry : getScheduledResets()) {
            NBTTagCompound tagEntry = NBTConverter.UuidValueType.QUEST.writeId(entry.questID);
            NBTConverter.UuidValueType.QUEST.writeId(entry.questID, tagEntry);
            tagEntry.setLong("time", entry.time);
            tagSchedule.appendTag(tagEntry);
        }
        tags.setTag("resetSchedule", tagSchedule);

        return tags;
    }

    @Override
    public synchronized void deserializeNBT(NBTTagCompound nbt) {
        visibleQuests.clear();
        activeQuests.clear();
        resetSchedule.clear();
        autoClaims.clear();
        markedDirty.clear();

        BiConsumer<String, Set<UUID>> handleTag =
                (tagName, map) -> {
                    if (nbt.hasKey(tagName, Constants.NBT.TAG_LIST)) {
                        map.addAll(NBTConverter.UuidValueType.QUEST.readIds(nbt, tagName));
                    } else {
                        // TODO is this NBT ever persisted? We only need this block if it is.
                        Arrays.stream(nbt.getIntArray(tagName))
                                .mapToObj(IUuidDatabase::convertLegacyId)
                                .forEach(map::add);
                    }
                };

        handleTag.accept("visibleQuests", visibleQuests);
        handleTag.accept("activeQuests", activeQuests);
        handleTag.accept("autoClaims", autoClaims);
        handleTag.accept("markedDirty", markedDirty);

        NBTTagList tagList = nbt.getTagList("resetSchedule", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound tagEntry = tagList.getCompoundTagAt(i);
            NBTConverter.UuidValueType.QUEST.tryReadId(tagEntry).ifPresent(
                    uuid -> resetSchedule.add(new QResetTime(uuid, tagEntry.getLong("time"))));
        }
    }

    public static class QResetTime implements Comparable<QResetTime> {
        public final UUID questID;
        public final long time;

        private QResetTime(UUID questID, long time) {
            this.questID = questID;
            this.time = time;
        }

        @Override
        public int compareTo(QResetTime o) {
            return Long.compare(o.time, time);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof QResetTime)) return false;
            return ((QResetTime) o).questID.equals(questID);
        }
    }
}
