package betterquesting.importers.ftbq;

import java.util.UUID;

public class FTBEntry {
    public final UUID id;
    public final Object obj;
    public final FTBEntryType type;

    public FTBEntry(UUID id, Object obj, FTBEntryType type) {
        this.id = id;
        this.obj = obj;
        this.type = type;
    }

    public enum FTBEntryType {
        QUEST,
        LINE,
        VAR
    }
}
