package betterquesting.importers;

import betterquesting.api.client.importers.IImporter;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineDatabase;
import betterquesting.api.utils.FileExtensionFilter;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.io.File;
import java.io.FileFilter;
import java.util.*;
import java.util.stream.Collectors;

public class NativeFileImporter implements IImporter {
    public static final NativeFileImporter INSTANCE = new NativeFileImporter();
    private static final FileFilter FILTER = new FileExtensionFilter(".json");

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.importer.nat_file.name";
    }

    @Override
    public String getUnlocalisedDescription() {
        return "bq_standard.importer.nat_file.desc";
    }

    @Override
    public FileFilter getFileFilter() {
        return FILTER;
    }

    @Override
    public void loadFiles(IQuestDatabase questDB, IQuestLineDatabase lineDB, File[] files) {
        for (File selected : files) {
            if (selected == null || !selected.exists()) continue;

            NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(JsonHelper.ReadFromFile(selected), new NBTTagCompound(), true);
            HashMap<UUID, UUID> remappedIDs = readQuests(nbt.getTagList("questDatabase", 10), questDB);
            readQuestLines(nbt.getTagList("questLines", 10), lineDB, remappedIDs);
        }
    }

    private HashMap<UUID, UUID> readQuests(NBTTagList json, IQuestDatabase questDB) {
        HashMap<UUID, UUID> remappedIDs = new HashMap<>();
        List<IQuest> loadedQuests = new ArrayList<>();

        for (int i = 0; i < json.tagCount(); i++) {
            NBTTagCompound qTag = json.getCompoundTagAt(i);
            Optional<UUID> oldID = NBTConverter.UuidValueType.QUEST.tryReadId(qTag);
            if (!oldID.isPresent()) continue;

            UUID qID = questDB.generateKey();
            IQuest quest = questDB.createNew(qID);
            quest.readFromNBT(qTag);
            remappedIDs.put(oldID.get(), qID);
            loadedQuests.add(quest);
        }

        for (IQuest quest : loadedQuests) {
            quest.setRequirements(quest.getRequirements().stream().map(it -> remappedIDs.getOrDefault(it, it)).collect(Collectors.toList()));
        }

        return remappedIDs;
    }

    private void readQuestLines(NBTTagList json, IQuestLineDatabase lineDB, HashMap<UUID, UUID> remappeIDs) {
        for (int i = 0; i < json.tagCount(); i++) {
            NBTTagCompound jql = json.getCompoundTagAt(i).copy();

            if (jql.hasKey("quests", 9)) {
                NBTTagList qList = jql.getTagList("quests", 10);
                for (int n = 0; n < qList.tagCount(); n++) {
                    NBTTagCompound qTag = qList.getCompoundTagAt(n);

                    int oldID = qTag.hasKey("id", 99) ? qTag.getInteger("id") : -1;
                    if (oldID < 0) continue;
                    UUID qID = remappeIDs.get(oldID);
                    NBTConverter.UuidValueType.QUEST.writeId(qID, qTag);
                }
            }

            IQuestLine ql = lineDB.createNew(lineDB.generateKey());
            ql.readFromNBT(jql, false);
        }
    }
}
