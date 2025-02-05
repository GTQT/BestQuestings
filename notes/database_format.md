# BQuu Database Formats

As of currently, this mod has a lot of ways of storing and coding data. This is an attempt to classify all of them.

## Codec

### `QuestCommandDefaults#loadLegacy()`

- Deserialization
- `/bq_admin default load`
- Can load modpack defaults
- Long JSON format

### `QuestCommandDefaults#saveLegacy()`

- Serialization
- `/bq_admin default savelegacy`
- Long JSON format

### `SaveLoadHandler#saveConfig()`

- Serialization
- Runs as part of the world save sequence
- Long JSON format

### `QuestCommandDefaults#load()`

- Deserialization
- `/bq_admin default load`
- Can load modpack defaults
- VCS-friendly JSON format

### `QuestCommandDefaults#save()`

- Serialization
- `/bq_admin default save`
- VCS-friendly JSON format

### `betterquesting.importers.hqm`

- Deserialization
- An importer
- Reads the Hardcore Questing Mode format

### `betterquesting.importers.ftbq`

- Deserialization
- An importer
- Reads the FTB Quests format

### `betterquesting.importers.AdvImporter`

- Deserialization
- An importer
- Reads vanilla Advancements off a file

### `betterquesting.importers.NativeFileImporter`

- Deserialization
- An importer
- Serves no purpose?

## Formats

### The Java API

- `IDatabase`, `IUuidDatabase`
- `ApiReference.QUEST_DB`
- The only real source of truth during runtime

### NBT

- Used in networking code
- The only codec that API entities actually know

### Long JSON format

- NBT converted to JSON by `NBTConverter`
- Doesn't work well with version control systems
- Not designed for outside editing

### VCS-friendly JSON format

- NBT converted to JSON by `NBTConverter`
- Uses the filesystem to make it easier to manipulate with file explorers and version control systems
- Made for modpacks

### Read-only formats

1.  Hardcore Questing Mode
2.  FTB Quests
3.  Vanilla advancements

## But where's the formal format specification?

The code is the specification.