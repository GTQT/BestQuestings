package betterquesting.api.placeholders.rewards;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.Map;
import java.util.UUID;

public class RewardPlaceholder implements IReward {
    private NBTTagCompound nbtSaved = new NBTTagCompound();

    public NBTTagCompound getRewardConfigData() {
        return nbtSaved;
    }

    public void setRewardConfigData(NBTTagCompound nbt) {
        nbtSaved = nbt;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setTag("orig_data", nbtSaved);

        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        nbtSaved = nbt.getCompoundTag("orig_data");
    }

    @Override
    public String getUnlocalisedName() {
        return "betterquesting.placeholder";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryRewardPlaceholder.INSTANCE.getRegistryName();
    }

    @Override
    public boolean canClaim(EntityPlayer player, Map.Entry<UUID, IQuest> quest) {
        return false;
    }

    @Override
    public void claimReward(EntityPlayer player, Map.Entry<UUID, IQuest> quest) {
    }

    @Override
    public IGuiPanel getRewardGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest) {
        return null;
    }

    @Override
    public GuiScreen getRewardEditor(GuiScreen parent, Map.Entry<UUID, IQuest> quest) {
        return null;
    }
}
