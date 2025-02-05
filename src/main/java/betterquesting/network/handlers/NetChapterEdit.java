package betterquesting.network.handlers;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.utils.NBTConverter;
import betterquesting.core.BetterQuesting;
import betterquesting.core.ModReference;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestLineDatabase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import java.util.*;

public class NetChapterEdit {
    private static final ResourceLocation ID_NAME = new ResourceLocation(ModReference.MODID, "chapter_edit");

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetChapterEdit::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetChapterEdit::onClient);
        }
    }

    @SideOnly(Side.CLIENT)
    public static void sendEdit(NBTTagCompound payload) { // TODO: Make these use proper methods for each action rather than directly assembling the payload
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple<NBTTagCompound, EntityPlayerMP> message) {
        EntityPlayerMP sender = message.getSecond();
        MinecraftServer server = sender.getServer();
        if (server == null) return; // Here mostly just to keep intellisense happy

        boolean isOP = server.getPlayerList().canSendCommands(sender.getGameProfile());

        if (!isOP) { // OP pre-check
            BetterQuesting.logger.log(Level.WARN, "Player " + sender.getName() + " (UUID:" + QuestingAPI.getQuestingUUID(sender) + ") tried to edit chapters without OP permissions!");
            sender.sendStatusMessage(new TextComponentString(TextFormatting.RED + "You need to be OP to edit quests!"), true);
            return; // Player is not operator. Do nothing
        }

        NBTTagCompound tag = message.getFirst();
        int action = !message.getFirst().hasKey("action", 99) ? -1 : message.getFirst().getInteger("action");

        switch (action) {
            case 0: {
                editChapters(tag.getTagList("data", 10));
                break;
            }
            case 1: {
                deleteChapters(NBTConverter.UuidValueType.QUEST_LINE.readIds(tag, "questLineIDs"));
                break;
            }
            case 2: {
                reorderChapters(NBTConverter.UuidValueType.QUEST_LINE.readIds(tag, "questLineIDs"));
                break;
            }
            case 3: {
                createChapters(tag.getTagList("data", 10));
                break;
            }
            default: {
                BetterQuesting.logger.log(Level.ERROR, "Invalid chapter edit action '" + action + "'. Full payload:\n" + message.getFirst());
            }
        }
    }

    private static void editChapters(NBTTagList data) {
        List<UUID> ids = new ArrayList<>(data.tagCount());
        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound entry = data.getCompoundTagAt(i);
            UUID chapterID = NBTConverter.UuidValueType.QUEST_LINE.readId(entry);
            ids.add(chapterID);

            IQuestLine chapter = QuestLineDatabase.INSTANCE.get(chapterID);
            if (chapter != null) chapter.readFromNBT(entry.getCompoundTag("config"), false);
        }

        SaveLoadHandler.INSTANCE.markDirty();
        NetChapterSync.sendSync(null, ids);
    }

    private static void deleteChapters(Collection<UUID> chapterIDs) {
        for (UUID id : chapterIDs) {
            QuestLineDatabase.INSTANCE.remove(id);
        }

        SaveLoadHandler.INSTANCE.markDirty();

        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("questLineIDs", NBTConverter.UuidValueType.QUEST_LINE.writeIds(chapterIDs));
        payload.setInteger("action", 1);
        PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
    }

    private static void reorderChapters(List<UUID> chapterIDs) {
        for (int n = 0; n < chapterIDs.size(); n++) {
            QuestLineDatabase.INSTANCE.setOrderIndex(chapterIDs.get(n), n);
        }

        SaveLoadHandler.INSTANCE.markDirty();

        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("questLineIDs", NBTConverter.UuidValueType.QUEST_LINE.writeIds(chapterIDs));
        payload.setInteger("action", 2);
        PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
    }

    private static void createChapters(NBTTagList data) { // Includes future copy potential
        List<UUID> ids = new ArrayList<>(data.tagCount());
        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound entry = data.getCompoundTagAt(i);
            Optional<UUID> chapterIDOptional = NBTConverter.UuidValueType.QUEST_LINE.tryReadId(entry);
            UUID chapterID = chapterIDOptional.orElseGet(QuestLineDatabase.INSTANCE::generateKey);

            ids.add(chapterID);

            IQuestLine chapter = QuestLineDatabase.INSTANCE.get(chapterID);

            if (chapter == null) {
                chapter = QuestLineDatabase.INSTANCE.createNew(chapterID);
            }

            if (entry.hasKey("config", 10)) {
                chapter.readFromNBT(entry.getCompoundTag("config"), false);
            }
        }

        SaveLoadHandler.INSTANCE.markDirty();
        NetChapterSync.sendSync(null, ids);
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound message) {
        int action = !message.hasKey("action", 99) ? -1 : message.getInteger("action");

        switch (action) { // Change to a switch statement when more actions are required
            case 1: { // Delete
                for (int id : message.getIntArray("chapterIDs")) {
                    QuestLineDatabase.INSTANCE.remove(id);
                }

                MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.CHAPTER));
                break;
            }
            case 2: { // Reorder
                List<UUID> chapterIDs = NBTConverter.UuidValueType.QUEST_LINE.readIds(message, "questLineIDs");
                for (int n = 0; n < chapterIDs.size(); n++) {
                    QuestLineDatabase.INSTANCE.setOrderIndex(chapterIDs.get(n), n);
                }

                MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.CHAPTER));
                break;
            }
        }
    }
}
