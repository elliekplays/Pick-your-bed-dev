package pick.your.respawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

public class RespawnSavedData extends SavedData {
    private static final String DATA_NAME = "pick_your_bed_respawns";
    private static final SavedData.Factory<RespawnSavedData> FACTORY = new SavedData.Factory<>(
        RespawnSavedData::new,
        RespawnSavedData::load,
        DataFixTypes.LEVEL
    );

    private final Map<UUID, List<RespawnEntry>> entriesByOwner = new LinkedHashMap<>();
    private long nextId = 1L;

    public static RespawnSavedData get(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public RespawnEntry addOrUpdate(UUID owner, RespawnEntryType type, ResourceLocation dimension, BlockPos pos) {
        List<RespawnEntry> entries = this.entriesByOwner.computeIfAbsent(owner, unused -> new ArrayList<>());
        for (RespawnEntry entry : entries) {
            if (entry.samePlace(type, dimension, pos)) {
                this.setDirty();
                return entry;
            }
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

    public Optional<RespawnEntry> find(UUID owner, long id) {
        return this.entriesFor(owner).stream().filter(entry -> entry.id() == id).findFirst();
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
        return data;
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
