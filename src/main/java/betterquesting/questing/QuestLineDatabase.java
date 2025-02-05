package betterquesting.questing;

import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineDatabase;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.storage.IUuidDatabase;
import betterquesting.api2.storage.UuidDatabase;
import betterquesting.api2.utils.QuestLineSorter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class QuestLineDatabase extends UuidDatabase<IQuestLine> implements IQuestLineDatabase {
    public static final QuestLineDatabase INSTANCE = new QuestLineDatabase();

    /**
     * NOTE: this isn't kept perfectly in-sync with the contents of the super class (BiMap).
     *
     * <p>In order to keep this perfectly in-sync, we would need to override all methods that mutate
     * the underlying data, in order to also update lineOrder
     *
     * <p>I <em>think</em> that we are okay without doing this, as our methods handle lineOrder
     * missing some keys, or containing keys that aren't used anymore, but if we do run into issues,
     * this might be the cause.
     */
    protected final List<UUID> lineOrder = new ArrayList<>();
    protected final QuestLineSorter SORTER = new QuestLineSorter(this);

    @Override
    public synchronized int getOrderIndex(UUID lineID) {
        if (!containsKey(lineID)) return -1;
        int order = lineOrder.indexOf(lineID);
        if (order >= 0) return order;

        lineOrder.add(lineID);
        return lineOrder.size() - 1;
    }

    @Override
    public void setOrderIndex(UUID lineID, int index) {
        lineOrder.remove(lineID);
        lineOrder.add(MathHelper.clamp(index, 0, lineOrder.size()), lineID);
    }

    @Override
    public synchronized List<Map.Entry<UUID, IQuestLine>> getOrderedEntries() {
        return entrySet().stream()
                .sorted(SORTER)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public IQuestLine createNew(UUID lineID) {
        IQuestLine ql = new QuestLine();
        put(lineID, ql);
        return ql;
    }

    @Override
    public void removeQuest(UUID questID) {
        values().forEach(ql -> ql.remove(questID));
    }

    @Override
    public NBTTagList writeToNBT(NBTTagList json, @Nullable List<UUID> subset) {
        for (Map.Entry<UUID, IQuestLine> entry : entrySet()) {
            if (subset != null && !subset.contains(entry.getKey())) continue;
            NBTTagCompound jObj = entry.getValue().writeToNBT(new NBTTagCompound(), null);
            NBTConverter.UuidValueType.QUEST_LINE.writeId(entry.getKey(), jObj);
            jObj.setInteger("order", getOrderIndex(entry.getKey()));
            json.appendTag(jObj);
        }

        return json;
    }

    @Override
    public synchronized void readFromNBT(NBTTagList json, boolean merge) {
        if (!merge) clear();

        List<IQuestLine> unassigned = new ArrayList<>();
        SortedMap<Integer, UUID> orderMap = new TreeMap<>();

        for (int i = 0; i < json.tagCount(); i++) {
            NBTTagCompound jql = json.getCompoundTagAt(i);

            Optional<UUID> lineIDOptional = NBTConverter.UuidValueType.QUEST_LINE.tryReadId(jql);
            UUID lineID = null;

            if (lineIDOptional.isPresent()) {
                lineID = lineIDOptional.get();
            } else if (jql.hasKey("lineID", 99)) {
                lineID = IUuidDatabase.convertLegacyId(jql.getInteger("lineID"));
            }

            int order = jql.hasKey("order", 99) ? jql.getInteger("order") : -1;

            IQuestLine line = getOrDefault(lineID, new QuestLine());
            line.readFromNBT(jql, false);

            if (lineID != null) {
                put(lineID, line);
            } else {
                unassigned.add(line);
            }

            if (order >= 0) orderMap.put(order, lineID);
        }

        // Legacy support ONLY
        for (IQuestLine q : unassigned) put(generateKey(), q);

        List<Integer> orderKeys = new ArrayList<>(orderMap.keySet());
        Collections.sort(orderKeys);

        lineOrder.clear();
        lineOrder.addAll(orderMap.values());
    }

    @Override
    public synchronized void clear() {
        super.clear();
        lineOrder.clear();
    }
}