package betterquesting.api.questing.rewards;

import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.INBTSaveLoad;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

public interface IReward extends INBTSaveLoad<NBTTagCompound> {
    String getUnlocalisedName();

    ResourceLocation getFactoryID();

    boolean canClaim(EntityPlayer player, Map.Entry<UUID, IQuest> quest);

    void claimReward(EntityPlayer player, Map.Entry<UUID, IQuest> quest);

    @SideOnly(Side.CLIENT)
    IGuiPanel getRewardGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest);

    @Nullable
    @SideOnly(Side.CLIENT)
    GuiScreen getRewardEditor(GuiScreen parent, Map.Entry<UUID, IQuest> quest);
}
