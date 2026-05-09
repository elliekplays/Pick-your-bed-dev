package pick.your.respawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class RespawnSavedData extends SavedData {
    private static final String DATA_NAME = "pick_your_bed_respawns";
    private static final SavedData.Factory<RespawnSavedData> FACTORY = new SavedData.Factory<>(
        RespawnSavedData::new,
        RespawnSavedData::load,
        DataFixTypes.LEVEL
    );

    private final Map<UUID, List<RespawnEntry>> entriesByOwner = new LinkedHashMap<>();
    private final Map<UUID, List<Long>> respawnHistoryByOwner = new LinkedHashMap<>();
    private long nextId = 1L;

    public static RespawnSavedData get(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public RespawnEntry addOrUpdate(UUID owner, RespawnEntryType type, ResourceLocation dimension, BlockPos pos) {
        List<RespawnEntry> entries = this.entriesByOwner.computeIfAbsent(owner, unused -> new ArrayList<>());
        Optional<RespawnEntry> existing = findInPlace(entries, type, dimension, pos);
        if (existing.isPresent()) {
            return existing.get();
        }

        String baseName = RespawnEntry.fallbackName(type);
        String name = baseName + " " + (countOfType(entries, type) + 1);
        RespawnEntry entry = new RespawnEntry(this.nextId++, owner, type, dimension, pos, name);
        entries.add(entry);
        this.setDirty();
        return entry;
    }

    public List<RespawnEntry> entriesFor(UUID owner) {
        List<RespawnEntry> entries = this.entriesByOwner.get(owner);
        if (entries == null) {
            return List.of();
        }

        return entries.stream()
            .sorted(Comparator.comparingLong(RespawnEntry::id).reversed())
            .toList();
    }

    public int entryCount(UUID owner) {
        List<RespawnEntry> entries = this.entriesByOwner.get(owner);
        return entries == null ? 0 : entries.size();
    }

    public Optional<RespawnEntry> findPlace(UUID owner, RespawnEntryType type, ResourceLocation dimension, BlockPos pos) {
        List<RespawnEntry> entries = this.entriesByOwner.get(owner);
        if (entries == null) {
            return Optional.empty();
        }
        return findInPlace(entries, type, dimension, pos);
    }

    public List<RespawnEntry> respawnHistoryFor(UUID owner) {
        List<Long> history = this.respawnHistoryByOwner.get(owner);
        List<RespawnEntry> entries = this.entriesByOwner.get(owner);
        if (history == null || entries == null) {
            return List.of();
        }

        List<RespawnEntry> ordered = new ArrayList<>();
        for (long id : history) {
            findIn(entries, id).ifPresent(ordered::add);
        }
        return List.copyOf(ordered);
    }

    public Optional<RespawnEntry> find(UUID owner, long id) {
        return this.entriesFor(owner).stream().filter(entry -> entry.id() == id).findFirst();
    }

    public void markRespawnUsed(UUID owner, long id) {
        List<RespawnEntry> entries = this.entriesByOwner.get(owner);
        if (entries == null || findIn(entries, id).isEmpty()) {
            return;
        }

        List<Long> history = this.respawnHistoryByOwner.computeIfAbsent(owner, unused -> new ArrayList<>());
        List<Long> previous = List.copyOf(history);
        history.removeIf(value -> value == id);
        history.add(0, id);
        pruneRespawnHistory(owner);
        List<Long> updated = this.respawnHistoryByOwner.getOrDefault(owner, List.of());
        if (!updated.equals(previous)) {
            this.setDirty();
        }
    }

    public boolean rename(UUID owner, long id, String name) {
        Optional<RespawnEntry> entry = this.find(owner, id);
        if (entry.isEmpty()) {
            return false;
        }

        entry.get().rename(name);
        this.setDirty();
        return true;
    }

    public int removeEntries(UUID owner, Predicate<RespawnEntry> predicate) {
        List<RespawnEntry> entries = this.entriesByOwner.get(owner);
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        int before = entries.size();
        entries.removeIf(predicate);
        int removed = before - entries.size();
        if (removed > 0) {
            if (entries.isEmpty()) {
                this.entriesByOwner.remove(owner);
                this.respawnHistoryByOwner.remove(owner);
            } else {
                pruneRespawnHistory(owner);
            }
            this.setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextId", this.nextId);
        ListTag entries = new ListTag();
        for (List<RespawnEntry> ownerEntries : this.entriesByOwner.values()) {
            for (RespawnEntry entry : ownerEntries) {
                entries.add(entry.save());
            }
        }
        tag.put("Entries", entries);

        ListTag histories = new ListTag();
        for (Map.Entry<UUID, List<Long>> ownerHistory : this.respawnHistoryByOwner.entrySet()) {
            if (ownerHistory.getValue().isEmpty()) {
                continue;
            }

            CompoundTag history = new CompoundTag();
            history.putUUID("Owner", ownerHistory.getKey());
            ListTag ids = new ListTag();
            for (long id : ownerHistory.getValue()) {
                ids.add(LongTag.valueOf(id));
            }
            history.put("Ids", ids);
            histories.add(history);
        }
        tag.put("RespawnHistory", histories);
        return tag;
    }

    private static RespawnSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RespawnSavedData data = new RespawnSavedData();
        data.nextId = Math.max(1L, tag.getLong("NextId"));
        ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            RespawnEntry entry = RespawnEntry.load(entries.getCompound(i));
            data.entriesByOwner.computeIfAbsent(entry.owner(), unused -> new ArrayList<>()).add(entry);
            data.nextId = Math.max(data.nextId, entry.id() + 1L);
        }

        ListTag histories = tag.getList("RespawnHistory", Tag.TAG_COMPOUND);
        for (int i = 0; i < histories.size(); i++) {
            CompoundTag history = histories.getCompound(i);
            if (!history.hasUUID("Owner")) {
                continue;
            }

            UUID owner = history.getUUID("Owner");
            ListTag ids = history.getList("Ids", Tag.TAG_LONG);
            List<Long> ownerHistory = new ArrayList<>();
            for (int idIndex = 0; idIndex < ids.size(); idIndex++) {
                if (ids.get(idIndex) instanceof LongTag id) {
                    ownerHistory.add(id.getAsLong());
                }
            }
            if (!ownerHistory.isEmpty()) {
                data.respawnHistoryByOwner.put(owner, ownerHistory);
            }
        }
        for (UUID owner : data.entriesByOwner.keySet()) {
            data.pruneRespawnHistory(owner);
        }
        return data;
    }

    private void pruneRespawnHistory(UUID owner) {
        List<Long> history = this.respawnHistoryByOwner.get(owner);
        List<RespawnEntry> entries = this.entriesByOwner.get(owner);
        if (history == null) {
            return;
        }
        if (entries == null || entries.isEmpty()) {
            this.respawnHistoryByOwner.remove(owner);
            return;
        }

        List<Long> clean = new ArrayList<>(history.size());
        for (long id : history) {
            if (!clean.contains(id) && findIn(entries, id).isPresent()) {
                clean.add(id);
            }
        }
        if (clean.isEmpty()) {
            this.respawnHistoryByOwner.remove(owner);
        } else {
            this.respawnHistoryByOwner.put(owner, clean);
        }
    }

    private static Optional<RespawnEntry> findIn(List<RespawnEntry> entries, long id) {
        return entries.stream().filter(entry -> entry.id() == id).findFirst();
    }

    private static Optional<RespawnEntry> findInPlace(List<RespawnEntry> entries, RespawnEntryType type, ResourceLocation dimension, BlockPos pos) {
        return entries.stream().filter(entry -> entry.samePlace(type, dimension, pos)).findFirst();
    }

    private static int countOfType(List<RespawnEntry> entries, RespawnEntryType type) {
        int count = 0;
        for (RespawnEntry entry : entries) {
            if (entry.type() == type) {
                count++;
            }
        }
        return count;
    }
}
