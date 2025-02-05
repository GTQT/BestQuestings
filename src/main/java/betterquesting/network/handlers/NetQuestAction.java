package betterquesting.network.handlers;

import betterquesting.api.network.QuestingPacket;
import betterquesting.api.utils.NBTConverter;
import betterquesting.core.BetterQuesting;
import betterquesting.core.ModReference;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class NetQuestAction {
    private static final ResourceLocation ID_NAME = new ResourceLocation(ModReference.MODID, "quest_action");

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetQuestAction::onServer);
    }

    @SideOnly(Side.CLIENT)
    public static void requestClaim(@Nonnull Collection<UUID> questIDs) {
        if (questIDs.isEmpty()) return;
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("action", 0);
        payload.setTag("questIDs", NBTConverter.UuidValueType.QUEST.writeIds(questIDs));
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    public static void requestDetect(@Nonnull Collection<UUID> questIDs) {
        if (questIDs.isEmpty()) return;
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("action", 1);
        payload.setTag("questIDs", NBTConverter.UuidValueType.QUEST.writeIds(questIDs));
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple<NBTTagCompound, EntityPlayerMP> message) {
        int action = !message.getFirst().hasKey("action", 99) ? -1 : message.getFirst().getInteger("action");
        Supplier<List<UUID>> getQuestIDs =
                () -> NBTConverter.UuidValueType.QUEST.readIds(message.getFirst(), "questIDs");

        switch (action) {
            case 0: {
                claimQuest(getQuestIDs.get(), message.getSecond());
                break;
            }
            case 1: {
                detectQuest(getQuestIDs.get(), message.getSecond());
                break;
            }
            default: {
                BetterQuesting.logger.log(Level.ERROR, "Invalid quest user action '" + action + "'. Full payload:\n" + message.getFirst());
            }
        }
    }

    public static void claimQuest(Collection<UUID> questIDs, EntityPlayerMP player) {
        QuestDatabase.INSTANCE.getAll(questIDs)
                .filter(q -> q.canClaim(player))
                .forEach(q -> q.claimReward(player));
    }

    public static void detectQuest(Collection<UUID> questIDs, EntityPlayerMP player) {
        QuestDatabase.INSTANCE.filterKeys(questIDs).values().forEach(q -> q.detect(player));
    }
}
