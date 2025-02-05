package betterquesting.api.questing;

import betterquesting.api.enums.EnumQuestState;
import betterquesting.api.properties.IPropertyContainer;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.storage.IDatabaseNBT;
import betterquesting.api2.storage.INBTProgress;
import betterquesting.api2.storage.INBTSaveLoad;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

public interface IQuest extends INBTSaveLoad<NBTTagCompound>, INBTProgress<NBTTagCompound>, IPropertyContainer {

    EnumQuestState getState(EntityPlayer player);

    @Nullable
    NBTTagCompound getCompletionInfo(UUID uuid);

    void setCompletionInfo(UUID uuid, @Nullable NBTTagCompound nbt);

    void update(EntityPlayer player);

    void detect(EntityPlayer player);

    boolean isUnlocked(UUID uuid);

    boolean canSubmit(EntityPlayer player);

    boolean isComplete(UUID uuid);

    void setComplete(UUID uuid, long timeStamp);

    /**
     * Can claim now. (Basically includes info from rewards (is choice reward chosen, for example))
     */
    boolean canClaim(EntityPlayer player);

    /**
     * Can we claim reward at all. (If reward available but we can't claim because a rewards not ready (choice reward not chosen, for example))
     */
    boolean canClaimBasically(EntityPlayer player);

    boolean hasClaimed(UUID uuid);

    void claimReward(EntityPlayer player);

    void setClaimed(UUID uuid, long timestamp);

    void resetUser(@Nullable UUID uuid, boolean fullReset);

    IDatabaseNBT<ITask, NBTTagList, NBTTagList> getTasks();

    IDatabaseNBT<IReward, NBTTagList, NBTTagList> getRewards();

    /**
     * Returns a mutable set. Changes made to the returned set will be reflected in the quest!
     */
    @Nonnull
    Set<UUID> getRequirements();

    void setRequirements(@Nonnull Iterable<UUID> req);

    @Nonnull
    RequirementType getRequirementType(UUID req);

    void setRequirementType(UUID req, @Nonnull RequirementType kind);


    enum RequirementType {
        NORMAL(PresetIcon.ICON_VISIBILITY_NORMAL),
        IMPLICIT(PresetIcon.ICON_VISIBILITY_IMPLICIT),
        HIDDEN(PresetIcon.ICON_VISIBILITY_HIDDEN);

        private static final RequirementType[] VALUES = values();
        private final PresetIcon icon;

        RequirementType(PresetIcon icon) {
            this.icon = icon;
        }

        public static RequirementType from(byte id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NORMAL;
        }

        public byte id() {
            return (byte) ordinal();
        }

        public PresetIcon getIcon() {
            return icon;
        }

        public RequirementType next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }
    }
}
