package betterquesting.network.handlers;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.*;
import betterquesting.client.importers.ImportedQuestLines;
import betterquesting.client.importers.ImportedQuests;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

public class NetImport {
    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:import");

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetImport::onServer);
    }

    public static void sendImport(@Nonnull IQuestDatabase questDB, @Nonnull IQuestLineDatabase chapterDB) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("quests", questDB.writeToNBT(new NBTTagList(), null));
        payload.setTag("chapters", chapterDB.writeToNBT(new NBTTagList(), null));
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple<NBTTagCompound, EntityPlayerMP> message) {
        EntityPlayerMP sender = message.getSecond();
        if (sender.getServer() == null) return;

        boolean isOP = sender.getServer().getPlayerList().canSendCommands(sender.getGameProfile());

        if (!isOP) {
            BetterQuesting.logger.log(Level.WARN, "Player " + sender.getName() + " (UUID:" + QuestingAPI.getQuestingUUID(sender) + ") tried to import quests without OP permissions!");
            sender.sendStatusMessage(new TextComponentString(TextFormatting.RED + "You need to be OP to edit quests!"), false);
            return; // Player is not operator. Do nothing
        }

        ImportedQuests impQuestDB = new ImportedQuests();
        IQuestLineDatabase impQuestLineDB = new ImportedQuestLines();

        impQuestDB.readFromNBT(message.getFirst().getTagList("quests", 10), false);
        impQuestLineDB.readFromNBT(message.getFirst().getTagList("chapters", 10), false);

        BetterQuesting.logger.log(Level.INFO, "Importing " + impQuestDB.size() + " quest(s) and " + impQuestLineDB.size() + " quest line(s) from " + sender.getGameProfile().getName());

        BiMap<UUID, UUID> remapped = getRemappedIDs(impQuestDB.keySet());
        for (Map.Entry<UUID, IQuest> entry : impQuestDB.entrySet()) {
            Set<UUID> newRequirements =
                    entry.getValue().getRequirements().stream()
                            .map(req -> remapped.getOrDefault(req, req))
                            .collect(Collectors.toCollection(HashSet::new));
            entry.getValue().setRequirements(newRequirements);

            QuestDatabase.INSTANCE.put(remapped.get(entry.getKey()), entry.getValue());
        }

        for (IQuestLine questLine : impQuestLineDB.values()) {
            Set<Map.Entry<UUID, IQuestLineEntry>> pendingQLE = new HashSet<>(questLine.entrySet());
            questLine.clear();

            for (Map.Entry<UUID, IQuestLineEntry> qle : pendingQLE) {
                if (!remapped.containsKey(qle.getKey())) {
                    BetterQuesting.logger.error("Failed to import quest into quest line. Unable to remap ID " + qle.getKey());
                    continue;
                }

                questLine.put(remapped.get(qle.getKey()), qle.getValue());
            }

            QuestLineDatabase.INSTANCE.put(QuestLineDatabase.INSTANCE.generateKey(), questLine);
        }

        SaveLoadHandler.INSTANCE.markDirty();
        NetQuestSync.quickSync(null, true, true);
        NetChapterSync.sendSync(null, null);
    }

    /**
     * Takes a list of imported IDs and returns a remapping to unused IDs
     */
    private static BiMap<UUID, UUID> getRemappedIDs(Set<UUID> ids) {
        Set<UUID> nextIDs = getNextIDs(ids.size());
        BiMap<UUID, UUID> remapped = HashBiMap.create(ids.size());

        Iterator<UUID> nextIDIterator = nextIDs.iterator();
        for (UUID id : ids) {
            remapped.put(id, nextIDIterator.next());
        }

        return remapped;
    }

    private static Set<UUID> getNextIDs(int num) {
        Set<UUID> nextIds = new HashSet<>();
        while (nextIds.size() < num) {
            // In the extremely unlikely event of a collision,
            // we'll handle it automatically due to nextIds being a Set
            nextIds.add(QuestDatabase.INSTANCE.generateKey());
        }

        return nextIds;
    }
}
