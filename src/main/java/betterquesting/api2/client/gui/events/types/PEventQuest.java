package betterquesting.api2.client.gui.events.types;

import betterquesting.api2.client.gui.events.PanelEvent;

import java.util.*;

// Use whenever one or more quests change
public class PEventQuest extends PanelEvent {
    private final Set<UUID> questIDs;

    public PEventQuest(UUID questID) {
        this.questIDs = Collections.singleton(questID);
    }

    public PEventQuest(Collection<UUID> questIDs) {
        this.questIDs = Collections.unmodifiableSet(new TreeSet<>(questIDs));
    }

    public Set<UUID> getQuestID() {
        return this.questIDs;
    }

    @Override
    public boolean canCancel() {
        return false;
    }
}
