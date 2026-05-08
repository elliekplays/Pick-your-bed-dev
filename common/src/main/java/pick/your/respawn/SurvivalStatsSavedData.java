package pick.your.respawn;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SurvivalStatsSavedData extends SavedData {
    private static final String DATA_NAME = "pick_your_bed_survival_stats";
    private static final long NEW_WORLD_GRACE_TICKS = 200L;
    private static final SavedData.Factory<SurvivalStatsSavedData> FACTORY = new SavedData.Factory<>(
        SurvivalStatsSavedData::new,
        SurvivalStatsSavedData::load,
        DataFixTypes.LEVEL
    );

    private final Map<UUID, Long> playTicksByPlayer = new HashMap<>();
    private boolean initialized;
    private boolean modTimerEnabled;

    public static SurvivalStatsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void initializeForServer(MinecraftServer server) {
        if (this.initialized) {
            return;
        }

        this.initialized = true;
        // New saves create this data before meaningful world time has passed; existing saves fall back to vanilla stats.
        this.modTimerEnabled = server.overworld().getGameTime() <= NEW_WORLD_GRACE_TICKS;
        this.setDirty();
    }

    public boolean modTimerEnabled() {
        return this.initialized && this.modTimerEnabled;
    }

    public long playTicks(UUID playerId) {
        return Math.max(0L, this.playTicksByPlayer.getOrDefault(playerId, 0L));
    }

    public boolean tickPlayer(ServerPlayer player) {
        if (!this.modTimerEnabled() || player.isSpectator() || !player.isAlive()) {
            return false;
        }

        this.playTicksByPlayer.merge(player.getUUID(), 1L, Long::sum);
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Initialized", this.initialized);
        tag.putBoolean("ModTimerEnabled", this.modTimerEnabled);

        ListTag players = new ListTag();
        for (Map.Entry<UUID, Long> entry : this.playTicksByPlayer.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Owner", entry.getKey());
            playerTag.putLong("PlayTicks", Math.max(0L, entry.getValue()));
            players.add(playerTag);
        }
        tag.put("Players", players);
        return tag;
    }

    private static SurvivalStatsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SurvivalStatsSavedData data = new SurvivalStatsSavedData();
        data.initialized = tag.getBoolean("Initialized");
        data.modTimerEnabled = tag.getBoolean("ModTimerEnabled");

        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            if (playerTag.hasUUID("Owner")) {
                data.playTicksByPlayer.put(playerTag.getUUID("Owner"), Math.max(0L, playerTag.getLong("PlayTicks")));
            }
        }
        return data;
    }
}
