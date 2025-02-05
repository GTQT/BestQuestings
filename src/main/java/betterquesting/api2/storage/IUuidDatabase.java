package betterquesting.api2.storage;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/**
 * Database that uses randomly-generated UUIDs as keys.
 */
public interface IUuidDatabase<T> extends BiMap<UUID, T> {
    /**
     * Converts a legacy integer ID to a UUID.
     */
    static UUID convertLegacyId(int legacyId) {
        // Negative legacy IDs are invalid, and are used to indicate an unset ID.
        Preconditions.checkArgument(legacyId >= 0);
        return new UUID(0L, legacyId);
    }

    /**
     * Returns an unused UUID.
     */
    UUID generateKey();

    @Nullable
    UUID lookupKey(T value);

    Stream<T> getAll(Collection<UUID> keys);

    Map<UUID, T> filterKeys(Collection<UUID> keys);

    BiMap<UUID, T> filterValues(Collection<T> values);

    BiMap<UUID, T> filterEntries(BiPredicate<UUID, T> filter);

    /**
     * Removes {@code value} from the database, and returns its corresponding key.
     * Returns null if {@code value} is not present in the database.
     */
    @Nullable
    UUID removeValue(T value);
}