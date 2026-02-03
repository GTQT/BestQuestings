package betterquesting.questing.tasks;
import betterquesting.NBTUtil;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.IFluidTask;
import betterquesting.api.questing.tasks.IItemTask;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.client.gui2.tasks.PanelTaskFluid;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.tasks.factory.FactoryTaskFluid;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TaskFluid implements ITaskInventory, IFluidTask, IItemTask {

    private static final boolean DEFAULT_IGNORE_NBT = false;
    private static final boolean DEFAULT_CONSUME = false;
    private static final boolean DEFAULT_GROUP_DETECT = false;
    private static final boolean DEFAULT_AUTO_CONSUME = false;
    private final Set<UUID> completeUsers = new TreeSet<>();
    public final NonNullList<FluidStack> requiredFluids = NonNullList.create();
    public final TreeMap<UUID, int[]> userProgress = new TreeMap<>();
    //public boolean partialMatch = true; // Not many ideal ways of implementing this with fluid handlers
    public boolean ignoreNbt = DEFAULT_IGNORE_NBT;
    public boolean consume = DEFAULT_CONSUME;
    public boolean groupDetect = DEFAULT_GROUP_DETECT;
    public boolean autoConsume = DEFAULT_AUTO_CONSUME;

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskFluid.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.task.fluid";
    }

    @Override
    public boolean isComplete(UUID uuid) {
        return completeUsers.contains(uuid);
    }

    @Override
    public void setComplete(UUID uuid) {
        completeUsers.add(uuid);
    }

    @Override
    public void onInventoryChange(DBEntry<IQuest> quest, ParticipantInfo pInfo, List<ItemStack> changedItems) {
        if (!consume || autoConsume) {
            detect(pInfo, quest, changedItems);
        }
    }

    /**
     * 处理外部流体系统直接提供的流体变化
     * 注意：这种方法与玩家背包完全无关，只处理外部模组直接提供的FluidStack
     * 外部流体无法被消耗，因此此方法不支持消耗模式
     */
    public void onFluidInventoryChange(DBEntry<IQuest> quest, ParticipantInfo pInfo, List<FluidStack> changedFluids) {
        if (!consume || autoConsume) {
            detectExternalFluids(pInfo, quest, changedFluids);
        }
    }

    /**
     * 专门处理外部流体系统提供的流体
     * 与玩家背包无关，不参与消耗模式
     */
    public void detectExternalFluids(ParticipantInfo pInfo, DBEntry<IQuest> quest, List<FluidStack> changedFluids) {
        if (isComplete(pInfo.UUID))
            return;

        // 外部流体无法被消耗，如果是消耗模式则直接返回
        if (consume) {
            return;
        }

        // List of (player uuid, [progress per required fluid])
        List<Tuple<UUID, int[]>> progress = getBulkProgress(pInfo.ALL_UUIDS);
        boolean updated = false;

        // 非消耗模式的进度重置逻辑
        if (groupDetect) {
            // 重置所有检测进度
            progress.forEach((value) -> Arrays.fill(value.getSecond(), 0));
        } else {
            for (int i = 0; i < requiredFluids.size(); i++) {
                final int r = requiredFluids.get(i).amount;
                for (Tuple<UUID, int[]> value : progress) {
                    int n = value.getSecond()[i];
                    if (n != 0 && n < r) {
                        value.getSecond()[i] = 0;
                        updated = true;
                    }
                }
            }
        }

        // 只检查外部流体列表
        if (changedFluids != null && !changedFluids.isEmpty()) {
            updated = checkExternalFluids(changedFluids, progress) || updated;
        }

        if (updated) {
            setBulkProgress(progress);
            checkAndComplete(pInfo, quest, updated, progress);
        }
    }

    /**
     * 直接检查外部流体列表中的流体
     * 与玩家背包无关，不参与消耗，但进度共享
     */
    private boolean checkExternalFluids(List<FluidStack> changedFluids, List<Tuple<UUID, int[]>> progress) {
        boolean updated = false;

        for (FluidStack fluidStack : changedFluids) {
            if (fluidStack == null || fluidStack.amount <= 0) continue;

            // 为每种所需流体检查
            for (int j = 0; j < requiredFluids.size(); j++) {
                FluidStack rStack = requiredFluids.get(j);

                // 检查是否还需要这种流体
                boolean needsThisFluid = false;
                for (Tuple<UUID, int[]> value : progress) {
                    if (value.getSecond()[j] < rStack.amount) {
                        needsThisFluid = true;
                        break;
                    }
                }
                if (!needsThisFluid) continue;

                // 检查流体是否匹配
                FluidStack compareStack = rStack.copy();
                if (ignoreNbt) {
                    compareStack.tag = null;
                    fluidStack = fluidStack.copy();
                    fluidStack.tag = null;
                }

                if (!fluidStack.isFluidEqual(compareStack)) continue;

                // 更新所有玩家的进度（外部流体不参与消耗，只增加进度）
                for (Tuple<UUID, int[]> value : progress) {
                    if (value.getSecond()[j] >= rStack.amount) continue;
                    int remaining = rStack.amount - value.getSecond()[j];

                    // 计算可以添加的流体量
                    int amountToAdd = Math.min(fluidStack.amount, remaining);
                    if (amountToAdd <= 0) continue;

                    value.getSecond()[j] += amountToAdd;
                    updated = true;
                }

                break; // 处理完这种流体，继续下一个流体
            }
        }

        return updated;
    }


    @Override
    public void detect(ParticipantInfo pInfo, DBEntry<IQuest> quest){
        detect(pInfo, quest, null);
    }

    public void detect(ParticipantInfo pInfo, DBEntry<IQuest> quest, List<ItemStack> changedItems) {
        if (isComplete(pInfo.UUID))
            return;

        // 如果是消耗模式且有额外物品列表，直接返回
        if (changedItems != null && consume) {
            return;
        }

        // List of (player uuid, [progress per required fluid])
        List<Tuple<UUID, int[]>> progress = getBulkProgress(
                consume ? Collections.singletonList(pInfo.UUID) : pInfo.ALL_UUIDS);
        boolean updated = false;

        if (!consume) {
            if (groupDetect) // Reset all detect progress
                progress.forEach((value) -> Arrays.fill(value.getSecond(), 0));
            else {
                for (int i = 0; i < requiredFluids.size(); i++) {
                    final int r = requiredFluids.get(i).amount;
                    for (Tuple<UUID, int[]> value : progress) {
                        int n = value.getSecond()[i];
                        if (n != 0 && n < r) {
                            value.getSecond()[i] = 0;
                            updated = true;
                        }
                    }
                }
            }
        }

        // 如果changedItems不为空，只检查changedItems
        if (changedItems != null && !changedItems.isEmpty()) {
            updated = checkChangedFluidItems(changedItems, progress, pInfo) || updated;
        } else {
            // 否则检查玩家背包
            updated = checkPlayerFluidInventories(pInfo, progress) || updated;
        }

        if (updated) {
            setBulkProgress(progress);
            // 重用progress参数，避免重新获取
            checkAndComplete(pInfo, quest, updated, progress);
        }
    }

    /**
     * 检查changedItems中的流体容器
     * changedItems被视为额外物品，不参与消耗，但进度共享
     */
    private boolean checkChangedFluidItems(List<ItemStack> changedItems, List<Tuple<UUID, int[]>> progress, ParticipantInfo pInfo) {
        boolean updated = false;

        for (ItemStack stack : changedItems) {
            if (stack.isEmpty()) continue;

            // 为检索流体信息创建一个副本
            ItemStack singleStack = stack.copy();
            singleStack.setCount(1);

            // 获取流体处理器
            IFluidHandlerItem handler = FluidUtil.getFluidHandler(singleStack);
            if (handler == null) continue;

            // 为每种所需流体检查容器
            for (int j = 0; j < requiredFluids.size(); j++) {
                FluidStack rStack = requiredFluids.get(j);

                // 检查是否还需要这种流体
                boolean needsThisFluid = false;
                for (Tuple<UUID, int[]> value : progress) {
                    if (value.getSecond()[j] < rStack.amount) {
                        needsThisFluid = true;
                        break;
                    }
                }
                if (!needsThisFluid) continue;

                // 尝试抽取流体（模拟，不实际抽取）
                FluidStack rStackOg = rStack.copy();
                rStackOg.amount = (int) Math.ceil((double) rStack.amount / (double) stack.getCount());
                FluidStack sample = handler.drain(rStackOg, false);
                if (sample == null || sample.amount <= 0) continue;

                // 检查流体是否匹配
                FluidStack compareStack = rStack.copy();
                if (ignoreNbt) {
                    compareStack.tag = null;
                    sample = sample.copy();
                    sample.tag = null;
                }

                if (!sample.isFluidEqual(compareStack)) continue;

                // 更新所有玩家的进度（changedItems不参与消耗，只增加进度）
                for (Tuple<UUID, int[]> value : progress) {
                    if (value.getSecond()[j] >= rStack.amount) continue;
                    int remaining = rStack.amount - value.getSecond()[j];

                    FluidStack drain = rStack.copy();
                    drain.amount = (int) Math.ceil((double) remaining / (double) stack.getCount());
                    if (ignoreNbt) drain.tag = null;
                    if (drain.amount <= 0) continue;

                    FluidStack fluid = handler.drain(drain, false); // 不实际抽取
                    if (fluid == null || fluid.amount <= 0) continue;

                    value.getSecond()[j] += Math.min(fluid.amount * stack.getCount(), remaining);
                    updated = true;
                }

                break; // 处理完这种流体，继续下一个物品
            }
        }

        return updated;
    }

    /**
     * 检查玩家背包中的流体容器（原逻辑）
     */
    private boolean checkPlayerFluidInventories(ParticipantInfo pInfo, List<Tuple<UUID, int[]>> progress) {
        boolean updated = false;

        List<InventoryPlayer> invoList;
        if (consume) {
            // 消耗模式下不支持从其他成员的库存中消耗资源
            invoList = Collections.singletonList(pInfo.PLAYER.inventory);
        } else {
            invoList = new ArrayList<>();
            pInfo.ACTIVE_PLAYERS.forEach((p) -> invoList.add(p.inventory));
        }

        for (InventoryPlayer invo : invoList) {
            for (int i = 0; i < invo.getSizeInventory(); i++) {
                ItemStack stack = invo.getStackInSlot(i);

                if (stack.isEmpty()) continue;

                // 为检索流体信息创建一个副本
                ItemStack singleStack = stack.copy();
                singleStack.setCount(1);

                IFluidHandlerItem handler = FluidUtil.getFluidHandler(singleStack);
                if (handler == null) continue;

                for (int j = 0; j < requiredFluids.size(); j++) {
                    FluidStack rStack = requiredFluids.get(j);

                    boolean hasDrained = false;
                    boolean requiresFullDrain = false;
                    int fullDrainAmt = 0;

                    // 初步检查
                    FluidStack rStackOg = rStack.copy();
                    rStackOg.amount = (int) Math.ceil((double) rStack.amount / (double) stack.getCount());
                    FluidStack sample = handler.drain(rStackOg, false);
                    if (sample == null || sample.amount <= 0) {
                        // 检查是否可以完全排空容器
                        if (handler.getTankProperties().length < 1) continue;

                        fullDrainAmt = handler.getTankProperties()[0].getCapacity();
                        if (fullDrainAmt <= 0) continue;
                        rStackOg.amount = fullDrainAmt;

                        sample = handler.drain(rStackOg, false);
                        if (sample == null || sample.amount <= 0) continue;

                        requiresFullDrain = true;
                    }

                    // 检查流体是否匹配
                    FluidStack compareStack = rStack.copy();
                    if (ignoreNbt) {
                        compareStack.tag = null;
                        sample = sample.copy();
                        sample.tag = null;
                    }
                    if (!sample.isFluidEqual(compareStack)) continue;

                    // 更新进度
                    for (Tuple<UUID, int[]> value : progress) {
                        if (value.getSecond()[j] >= rStack.amount) continue;
                        int remaining = rStack.amount - value.getSecond()[j];

                        FluidStack drain = rStack.copy();

                        // 取上限，这样我们不会移除少于所需的数量
                        if (requiresFullDrain)
                            drain.amount = fullDrainAmt;
                        else
                            drain.amount = (int) Math.ceil((double) remaining / (double) stack.getCount());
                        if (ignoreNbt) drain.tag = null;
                        if (drain.amount <= 0) continue;

                        FluidStack fluid = handler.drain(drain, consume); // 根据消耗模式决定是否实际抽取
                        if (fluid == null || fluid.amount <= 0) continue;

                        value.getSecond()[j] += Math.min(fluid.amount * stack.getCount(), remaining);
                        hasDrained = true;
                        updated = true;
                    }

                    if (!hasDrained) continue;

                    // 如果是消耗模式，更新容器内容
                    if (consume) {
                        ItemStack result = handler.getContainer();
                        result.setCount(stack.getCount());
                        invo.setInventorySlotContents(i, result);
                    }

                    break;
                }
            }
        }

        return updated;
    }

    private void checkAndComplete(ParticipantInfo pInfo, DBEntry<IQuest> quest, boolean resync) {
        checkAndComplete(pInfo, quest, resync, getBulkProgress(consume ? Collections.singletonList(pInfo.UUID) : pInfo.ALL_UUIDS));
    }

    private void checkAndComplete(ParticipantInfo pInfo, DBEntry<IQuest> quest, boolean resync, List<Tuple<UUID, int[]>> progress) {
        boolean updated = resync;

        topLoop:
        for (Tuple<UUID, int[]> value : progress) {
            for (int j = 0; j < requiredFluids.size(); j++) {
                if (value.getSecond()[j] >= requiredFluids.get(j).amount) continue;
                continue topLoop;
            }

            updated = true;

            if (consume) {
                setComplete(value.getFirst());
            } else {
                progress.forEach((pair) -> setComplete(pair.getFirst()));
                break;
            }
        }

        if (updated) {
            if (consume) {
                pInfo.markDirty(Collections.singletonList(quest.getID()));
            } else {
                pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
            }
        }
    }

    @Deprecated
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return writeToNBT(nbt, false);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt, boolean reduce) {
        //json.setBoolean("partialMatch", partialMatch);
        NBTUtil.setBoolean(nbt, "ignoreNBT", ignoreNbt, DEFAULT_IGNORE_NBT, reduce);
        NBTUtil.setBoolean(nbt, "consume", consume, DEFAULT_CONSUME, reduce);
        NBTUtil.setBoolean(nbt, "groupDetect", groupDetect, DEFAULT_GROUP_DETECT, reduce);
        NBTUtil.setBoolean(nbt, "autoConsume", autoConsume, DEFAULT_AUTO_CONSUME, reduce);

        NBTTagList itemArray = new NBTTagList();
        for (FluidStack stack : this.requiredFluids) {
            itemArray.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        nbt.setTag("requiredFluids", itemArray);

        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        //partialMatch = json.getBoolean("partialMatch");
        ignoreNbt = NBTUtil.getBoolean(nbt, "ignoreNBT", DEFAULT_IGNORE_NBT);
        consume = NBTUtil.getBoolean(nbt, "consume", DEFAULT_CONSUME);
        groupDetect = NBTUtil.getBoolean(nbt, "groupDetect", DEFAULT_GROUP_DETECT);
        autoConsume = NBTUtil.getBoolean(nbt, "autoConsume", DEFAULT_AUTO_CONSUME);

        requiredFluids.clear();
        NBTTagList fList = nbt.getTagList("requiredFluids", 10);
        for (int i = 0; i < fList.tagCount(); i++) {
            requiredFluids.add(JsonHelper.JsonToFluidStack(fList.getCompoundTagAt(i)));
        }
    }

    @Override
    public void readProgressFromNBT(NBTTagCompound nbt, boolean merge) {
        if (!merge) {
            completeUsers.clear();
            userProgress.clear();
        }

        NBTTagList cList = nbt.getTagList("completeUsers", 8);
        for (int i = 0; i < cList.tagCount(); i++) {
            try {
                completeUsers.add(UUID.fromString(cList.getStringTagAt(i)));
            } catch (Exception e) {
                BetterQuesting.logger.log(Level.ERROR, "Unable to load UUID for task", e);
            }
        }

        NBTTagList pList = nbt.getTagList("userProgress", 10);
        for (int n = 0; n < pList.tagCount(); n++) {
            try {
                NBTTagCompound pTag = pList.getCompoundTagAt(n);
                UUID uuid = UUID.fromString(pTag.getString("uuid"));

                int[] data = new int[requiredFluids.size()];
                NBTTagList dNbt = pTag.getTagList("data", 3);
                for (int i = 0; i < data.length && i < dNbt.tagCount(); i++) // TODO: Change this to an int array. This is dumb...
                {
                    data[i] = dNbt.getIntAt(i);
                }

                userProgress.put(uuid, data);
            } catch (Exception e) {
                BetterQuesting.logger.log(Level.ERROR, "Unable to load user progress for task", e);
            }
        }
    }

    @Override
    public NBTTagCompound writeProgressToNBT(NBTTagCompound nbt, @Nullable List<UUID> users) {
        NBTTagList jArray = new NBTTagList();
        NBTTagList progArray = new NBTTagList();

        if (users != null) {
            users.forEach((uuid) -> {
                if (completeUsers.contains(uuid)) jArray.appendTag(new NBTTagString(uuid.toString()));

                int[] data = userProgress.get(uuid);
                if (data != null) {
                    NBTTagCompound pJson = new NBTTagCompound();
                    pJson.setString("uuid", uuid.toString());
                    NBTTagList pArray = new NBTTagList(); // TODO: Why the heck isn't this just an int array?!
                    for (int i : data) pArray.appendTag(new NBTTagInt(i));
                    pJson.setTag("data", pArray);
                    progArray.appendTag(pJson);
                }
            });
        } else {
            completeUsers.forEach((uuid) -> jArray.appendTag(new NBTTagString(uuid.toString())));

            userProgress.forEach((uuid, data) -> {
                NBTTagCompound pJson = new NBTTagCompound();
                pJson.setString("uuid", uuid.toString());
                NBTTagList pArray = new NBTTagList(); // TODO: Why the heck isn't this just an int array?!
                for (int i : data) pArray.appendTag(new NBTTagInt(i));
                pJson.setTag("data", pArray);
                progArray.appendTag(pJson);
            });
        }

        nbt.setTag("completeUsers", jArray);
        nbt.setTag("userProgress", progArray);

        return nbt;
    }

    @Override
    public void resetUser(@Nullable UUID uuid) {
        if (uuid == null) {
            completeUsers.clear();
            userProgress.clear();
        } else {
            completeUsers.remove(uuid);
            userProgress.remove(uuid);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskFluid(rect, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen screen, DBEntry<IQuest> quest) {
        return null;
    }

    @Override
    public boolean canAcceptFluid(UUID owner, DBEntry<IQuest> quest, FluidStack fluid) {
        if (owner == null || fluid == null || fluid.getFluid() == null || !consume || isComplete(owner) || requiredFluids.size() <= 0) {
            return false;
        }

        int[] progress = getUsersProgress(owner);

        for (int j = 0; j < requiredFluids.size(); j++) {
            FluidStack rStack = requiredFluids.get(j).copy();
            if (ignoreNbt) rStack.tag = null;
            if (progress[j] < rStack.amount && rStack.equals(fluid)) return true;
        }

        return false;
    }

    @Override
    public boolean canAcceptItem(UUID owner, DBEntry<IQuest> quest, ItemStack item) {
        if (owner == null || item == null || item.isEmpty() || !consume || isComplete(owner) || requiredFluids.size() <= 0) {
            return false;
        }

        IFluidHandlerItem handler = FluidUtil.getFluidHandler(item);

        if (handler == null) return false;

        for (IFluidTankProperties tank : handler.getTankProperties()) {
            if (!tank.canDrain()) continue;

            for (FluidStack rStack : requiredFluids) {
                if (rStack.equals(tank.getContents())) return true;
            }
        }

        return false;
    }

    @Override
    public FluidStack submitFluid(UUID owner, DBEntry<IQuest> quest, FluidStack fluid) {
        return submitFluidInternal(owner, quest, fluid, true);
    }

    private FluidStack submitFluidInternal(UUID owner, DBEntry<IQuest> quest, FluidStack fluid, boolean doFill) {
        if (owner == null || fluid == null || fluid.amount <= 0 || !consume || isComplete(owner) || requiredFluids.size() <= 0) {
            return fluid;
        }

        int[] progress = getUsersProgress(owner).clone();
        boolean updated = false;

        for (int j = 0; j < requiredFluids.size(); j++) {
            FluidStack rStack = requiredFluids.get(j);

            if (progress[j] >= rStack.amount) continue;

            int remaining = rStack.amount - progress[j];

            if (rStack.isFluidEqual(fluid)) {
                int removed = Math.min(fluid.amount, remaining);
                progress[j] += removed;
                fluid.amount -= removed;
                updated = true;

                if (fluid.amount <= 0) {
                    fluid = null;
                    break;
                }
            }
        }

        if (updated && doFill) {
            setUserProgress(owner, progress);

            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            EntityPlayerMP player = server == null ? null : server.getPlayerList().getPlayerByUUID(owner);

            if (player != null) {
                checkAndComplete(new ParticipantInfo(player), quest, true);
            } else {
                // It's implied to be a consume task so no need to lookup the party
                boolean hasAll = true;
                for (int j = 0; j < requiredFluids.size(); j++) {
                    if (progress[j] >= requiredFluids.get(j).amount) continue;

                    hasAll = false;
                    break;
                }

                if (hasAll) setComplete(owner);
            }
        }

        return fluid;
    }

    @Override
    public ItemStack submitItem(UUID owner, DBEntry<IQuest> quest, ItemStack input) {
        if (owner == null || input.isEmpty() || !consume || isComplete(owner)) return input;

        ItemStack item = input.splitStack(1); // Prevents issues with stack filling/draining

        IFluidHandlerItem handler = FluidUtil.getFluidHandler(item);
        if (handler == null) return item;

        boolean hasDrained = false;

        for (IFluidTankProperties tank : handler.getTankProperties()) {
            if (!tank.canDrain() || tank.getContents() == null || !tank.canDrainFluidType(tank.getContents())) continue;

            // Figure out how much of this fluid is left to submit to the task
            FluidStack remaining = submitFluidInternal(owner, quest, tank.getContents().copy(), false);
            FluidStack drain = tank.getContents().copy();
            drain.amount -= remaining == null ? 0 : remaining.amount;

            if (drain.amount <= 0) continue;

            // Attempt drain of remaining amount and submit to task progress
            submitFluidInternal(owner, quest, handler.drain(drain, true), true);
            hasDrained = true;
        }

        return hasDrained ? handler.getContainer() : item;
    }

    private void setUserProgress(UUID uuid, int[] progress) {
        userProgress.put(uuid, progress);
    }

    public int[] getUsersProgress(UUID uuid) {
        int[] progress = userProgress.get(uuid);
        return progress == null || progress.length != requiredFluids.size() ? new int[requiredFluids.size()] : progress;
    }

    private List<Tuple<UUID, int[]>> getBulkProgress(@Nonnull List<UUID> uuids) {
        if (uuids.size() <= 0) return Collections.emptyList();
        List<Tuple<UUID, int[]>> list = new ArrayList<>();
        uuids.forEach((key) -> list.add(new Tuple<>(key, getUsersProgress(key))));
        return list;
    }

    private void setBulkProgress(@Nonnull List<Tuple<UUID, int[]>> list) {
        list.forEach((entry) -> setUserProgress(entry.getFirst(), entry.getSecond()));
    }

    @Override
    public List<String> getTextForSearch() {
        List<String> texts = new ArrayList<>();
        for (FluidStack fluid : requiredFluids) {
            texts.add(fluid.getLocalizedName());
            texts.add(fluid.getUnlocalizedName());
        }
        return texts;
    }
}
