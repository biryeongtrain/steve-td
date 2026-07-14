package kim.biryeong.semiontd.cosmetic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public final class CosmeticCatalog {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path path;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private boolean available;

    public CosmeticCatalog(Path path) {
        this.path = path;
    }

    public synchronized boolean load(RegistryAccess registryAccess) {
        if (path == null || Files.notExists(path)) {
            entries.clear();
            available = true;
            return true;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray serializedEntries = root.getAsJsonArray("entries");
            if (serializedEntries == null) {
                throw new IllegalArgumentException("Missing entries array.");
            }
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
            LinkedHashMap<String, Entry> loadedEntries = new LinkedHashMap<>();
            for (JsonElement serializedEntry : serializedEntries) {
                JsonObject object = serializedEntry.getAsJsonObject();
                String id = object.get("id").getAsString();
                long price = object.get("price").getAsLong();
                ItemStack item = ItemStack.STRICT_SINGLE_ITEM_CODEC.parse(ops, object.get("item"))
                        .getOrThrow(IllegalArgumentException::new);
                Entry entry = validatedEntry(id, price, item);
                if (loadedEntries.putIfAbsent(entry.id(), entry) != null) {
                    throw new IllegalArgumentException("Duplicate cosmetic id: " + entry.id());
                }
            }
            entries.clear();
            entries.putAll(loadedEntries);
            available = true;
            return true;
        } catch (Exception exception) {
            SemionTd.LOGGER.error("Failed to load cosmetic catalog {}. Existing catalog state will remain unchanged.", path, exception);
            return false;
        }
    }

    public synchronized boolean available() {
        return available;
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public synchronized Optional<Entry> find(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public synchronized MutationResult add(RegistryAccess registryAccess, String id, long price, ItemStack item) {
        if (!available) {
            return MutationResult.UNAVAILABLE;
        }
        if (entries.containsKey(id)) {
            return MutationResult.DUPLICATE;
        }
        Entry entry;
        try {
            entry = validatedEntry(id, price, item);
        } catch (IllegalArgumentException exception) {
            return MutationResult.INVALID;
        }
        entries.put(entry.id(), entry);
        if (save(registryAccess)) {
            return MutationResult.SUCCESS;
        }
        entries.remove(entry.id());
        return MutationResult.SAVE_FAILED;
    }

    public synchronized MutationResult update(RegistryAccess registryAccess, String id, long price, ItemStack item) {
        if (!available) {
            return MutationResult.UNAVAILABLE;
        }
        Entry previous = entries.get(id);
        if (previous == null) {
            return MutationResult.MISSING;
        }
        Entry entry;
        try {
            entry = validatedEntry(id, price, item);
        } catch (IllegalArgumentException exception) {
            return MutationResult.INVALID;
        }
        entries.put(id, entry);
        if (save(registryAccess)) {
            return MutationResult.SUCCESS;
        }
        entries.put(id, previous);
        return MutationResult.SAVE_FAILED;
    }

    public synchronized MutationResult remove(RegistryAccess registryAccess, String id) {
        if (!available) {
            return MutationResult.UNAVAILABLE;
        }
        LinkedHashMap<String, Entry> previousEntries = new LinkedHashMap<>(entries);
        Entry previous = entries.remove(id);
        if (previous == null) {
            return MutationResult.MISSING;
        }
        if (save(registryAccess)) {
            return MutationResult.SUCCESS;
        }
        entries.clear();
        entries.putAll(previousEntries);
        return MutationResult.SAVE_FAILED;
    }

    private boolean save(RegistryAccess registryAccess) {
        if (path == null) {
            return true;
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
            JsonArray serializedEntries = new JsonArray();
            for (Entry entry : entries.values()) {
                JsonObject serializedEntry = new JsonObject();
                serializedEntry.addProperty("id", entry.id());
                serializedEntry.addProperty("price", entry.price());
                serializedEntry.add("item", ItemStack.STRICT_SINGLE_ITEM_CODEC.encodeStart(ops, entry.item())
                        .getOrThrow(IllegalArgumentException::new));
                serializedEntries.add(serializedEntry);
            }
            JsonObject root = new JsonObject();
            root.add("entries", serializedEntries);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(
                    temporary,
                    GSON.toJson(root) + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            SemionTd.LOGGER.error("Failed to save cosmetic catalog {}.", path, exception);
            return false;
        }
    }

    private static Entry validatedEntry(String id, long price, ItemStack item) {
        if (id == null || id.isBlank() || price < 0 || item == null || item.isEmpty()) {
            throw new IllegalArgumentException("Invalid cosmetic entry.");
        }
        Equippable equippable = item.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != EquipmentSlot.HEAD) {
            throw new IllegalArgumentException("Cosmetic item must be equippable in the head slot.");
        }
        return new Entry(id, price, item.copyWithCount(1));
    }

    public record Entry(String id, long price, ItemStack item) {
    }

    public enum MutationResult {
        SUCCESS,
        DUPLICATE,
        MISSING,
        INVALID,
        SAVE_FAILED,
        UNAVAILABLE
    }
}
