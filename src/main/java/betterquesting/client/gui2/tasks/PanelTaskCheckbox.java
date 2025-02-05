package betterquesting.client.gui2.tasks;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.network.handlers.NetTaskCheckbox;
import betterquesting.questing.tasks.TaskCheckbox;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;

public class PanelTaskCheckbox extends CanvasMinimum {

    private final IGuiRect initialRect;
    private final Map.Entry<UUID, IQuest> quest;
    private final TaskCheckbox task;

    public PanelTaskCheckbox(IGuiRect rect, Map.Entry<UUID, IQuest> quest, TaskCheckbox task) {
        super(rect);
        this.initialRect = rect;
        this.quest = quest;
        this.task = task;
    }

    @Override
    public void initPanel() {
        super.initPanel();

        boolean isComplete = task.isComplete(QuestingAPI.getQuestingUUID(Minecraft.getMinecraft().player));
        final UUID questID = quest.getKey();
        final int taskID = quest.getValue().getTasks().getID(task);

        PanelButton btnCheck = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, (initialRect.getWidth() - 32) / 2, 0, 32, 32, 0), -1, "") {
            @Override
            public void onButtonClick() {
                setIcon(PresetIcon.ICON_TICK.getTexture(), new GuiColorStatic(0xFF00FF00), 4);
                setActive(false);

                NetTaskCheckbox.requestClick(questID, taskID);
            }
        };
        btnCheck.setIcon(isComplete ? PresetIcon.ICON_TICK.getTexture() : PresetIcon.ICON_CROSS.getTexture(), new GuiColorStatic(isComplete ? 0xFF00FF00 : 0xFFFF0000), 4);
        btnCheck.setActive(!isComplete);
        this.addPanel(btnCheck);
        recalculateSizes();
    }
}
