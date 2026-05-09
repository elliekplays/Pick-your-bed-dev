package pick.your.respawn;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SurvivalStatsSavedData extends SavedData {
    private static final String DATA_NAME = "pick_your_bed_survival_stats";
    private static final double MAX_TRACKED_DISTANCE_PER_TICK = 100.0D;
    private static final SavedData.Factory<SurvivalStatsSavedData> FACTORY = new SavedData.Factory<>(
        SurvivalStatsSavedData::new,
        SurvivalStatsSavedData::load,
        DataFixTypes.LEVEL
    );

    private final Map<UUID, PlayerStats> statsByPlayer = new HashMap<>();
    private final Map<UUID, PlayerStats> deathStatsByPlayer = new HashMap<>();
    private boolean initialized;

    public static SurvivalStatsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void initializeForServer(MinecraftServer server) {
        if (this.initialized) {
            return;
        }

        this.initialized = true;
        this.setDirty();
    }

    public Snapshot stats(ServerPlayer player) {
        return this.ensurePlayer(player).snapshot();
    }

    public Optional<Snapshot> deathStats(UUID playerId) {
        PlayerStats stats = this.deathStatsByPlayer.get(playerId);
        return stats == null ? Optional.empty() : Optional.of(stats.snapshot());
    }

    public boolean tickPlayer(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (!canRecordLiveStats(player)) {
            this.clearLastPosition(player.getUUID());
            return false;
        }

        PlayerStats stats = this.ensurePlayer(player);
        if (stats.skipNextPlayTick) {
            stats.skipNextPlayTick = false;
        } else {
            stats.playTicks = safeAdd(stats.playTicks, 1L);
        }
        stats.distanceCm = safeAdd(stats.distanceCm, distanceCmSinceLastTick(player, stats));
        return true;
    }

    public boolean recordBlockPlaced(ServerPlayer player) {
        if (!canRecordActionStats(player)) {
            return false;
        }

        PlayerStats stats = this.ensurePlayer(player);
        stats.blocksPlaced = safeAdd(stats.blocksPlaced, 1L);
        this.setDirty();
        return true;
    }

    public boolean recordBlockBroken(ServerPlayer player) {
        if (!canRecordActionStats(player)) {
            return false;
        }

        PlayerStats stats = this.ensurePlayer(player);
        stats.blocksBroken = safeAdd(stats.blocksBroken, 1L);
        this.setDirty();
        return true;
    }

    public boolean recordDeath(ServerPlayer player) {
        PlayerStats stats = this.ensurePlayer(player);
        PlayerStats existingDeathStats = this.deathStatsByPlayer.get(player.getUUID());
        if (existingDeathStats != null) {
            if (!existingDeathStats.importedVanillaStats) {
                existingDeathStats.mergeVanillaStats(player);
                this.setDirty();
                return true;
            }
            return false;
        }

        this.deathStatsByPlayer.put(player.getUUID(), stats.copy());
        this.setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Initialized", this.initialized);
        tag.putBoolean("ModTimerEnabled", true);

        Set<UUID> playerIds = new HashSet<>(this.statsByPlayer.keySet());
        playerIds.addAll(this.deathStatsByPlayer.keySet());

        ListTag players = new ListTag();
        for (UUID playerId : playerIds) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Owner", playerId);
            this.statsByPlayer.getOrDefault(playerId, PlayerStats.ZERO).saveActive(playerTag);

            PlayerStats deathStats = this.deathStatsByPlayer.get(playerId);
            if (deathStats != null) {
                deathStats.saveDeath(playerTag);
            }
            players.add(playerTag);
        }
        tag.put("Players", players);
        return tag;
    }

    private PlayerStats ensurePlayer(ServerPlayer player) {
        PlayerStats stats = this.statsByPlayer.computeIfAbsent(player.getUUID(), unused -> new PlayerStats());
        if (!stats.importedVanillaStats) {
            stats.mergeVanillaStats(player);
            this.setDirty();
        }
        return stats;
    }

    private void clearLastPosition(UUID playerId) {
        PlayerStats stats = this.statsByPlayer.get(playerId);
        if (stats != null) {
            stats.clearLastPosition();
        }
    }

    private static SurvivalStatsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SurvivalStatsSavedData data = new SurvivalStatsSavedData();
        data.initialized = tag.getBoolean("Initialized");

        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            if (!playerTag.hasUUID("Owner")) {
                continue;
            }

            UUID owner = playerTag.getUUID("Owner");
            data.statsByPlayer.put(owner, PlayerStats.loadActive(playerTag));
            if (playerTag.contains("DeathPlayTicks", Tag.TAG_LONG)) {
                data.deathStatsByPlayer.put(owner, PlayerStats.loadDeath(playerTag));
            }
        }
        return data;
    }

    private static boolean canRecordLiveStats(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator();
    }

    private static boolean canRecordActionStats(ServerPlayer player) {
        return player != null && !player.isSpectator();
    }

    private static long distanceCmSinceLastTick(ServerPlayer player, PlayerStats stats) {
        ResourceLocation dimension = player.level().dimension().location();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        if (!stats.hasLastPosition || !dimension.equals(stats.lastDimension)) {
            stats.setLastPosition(dimension, x, y, z);
            return 0L;
        }

        double xDistance = x - stats.lastX;
        double yDistance = y - stats.lastY;
        double zDistance = z - stats.lastZ;
        stats.setLastPosition(dimension, x, y, z);

        double distance = Math.sqrt(xDistance * xDistance + yDistance * yDistance + zDistance * zDistance);
        if (!Double.isFinite(distance) || distance <= 0.0D || distance > MAX_TRACKED_DISTANCE_PER_TICK) {
            return 0L;
        }
        return Math.max(0L, Math.round(distance * 100.0D));
    }

    private static long vanillaDeathPlayTicks(ServerPlayer player) {
        long playTicks = Math.max(0, player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME));
        int deaths = Math.max(0, player.getStats().getValue(Stats.CUSTOM, Stats.DEATHS));
        int timeSinceDeath = Math.max(0, player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH));
        return deaths > 0 ? Math.max(0L, playTicks - timeSinceDeath) : playTicks;
    }

    private static long vanillaBlocksBroken(ServerPlayer player) {
        long total = 0L;
        for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
            total = safeAdd(total, Math.max(0, player.getStats().getValue(Stats.BLOCK_MINED, block)));
        }
        return total;
    }

    private static long vanillaBlocksPlaced(ServerPlayer player) {
        long total = 0L;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem) {
                total = safeAdd(total, Math.max(0, player.getStats().getValue(Stats.ITEM_USED, item)));
            }
        }
        return total;
    }

    private static long vanillaDistanceCm(ServerPlayer player) {
        return sumCustomStats(
            player,
            Stats.WALK_ONE_CM,
            Stats.CROUCH_ONE_CM,
            Stats.SPRINT_ONE_CM,
            Stats.SWIM_ONE_CM,
            Stats.FALL_ONE_CM,
            Stats.CLIMB_ONE_CM,
            Stats.FLY_ONE_CM,
            Stats.WALK_ON_WATER_ONE_CM,
            Stats.WALK_UNDER_WATER_ONE_CM,
            Stats.MINECART_ONE_CM,
            Stats.BOAT_ONE_CM,
            Stats.PIG_ONE_CM,
            Stats.HORSE_ONE_CM,
            Stats.AVIATE_ONE_CM,
            Stats.STRIDER_ONE_CM
        );
    }

    private static long sumCustomStats(ServerPlayer player, ResourceLocation... stats) {
        long total = 0L;
        for (ResourceLocation stat : stats) {
            total = safeAdd(total, Math.max(0, player.getStats().getValue(Stats.CUSTOM, stat)));
        }
        return total;
    }

    private static long safeAdd(long current, long amount) {
        long cleanCurrent = Math.max(0L, current);
        long cleanAmount = Math.max(0L, amount);
        if (Long.MAX_VALUE - cleanCurrent < cleanAmount) {
            return Long.MAX_VALUE;
        }
        return cleanCurrent + cleanAmount;
    }

    public record Snapshot(long playTicks, long blocksPlaced, long blocksBroken, long distanceCm) {
        public Snapshot {
            playTicks = Math.max(0L, playTicks);
            blocksPlaced = Math.max(0L, blocksPlaced);
            blocksBroken = Math.max(0L, blocksBroken);
            distanceCm = Math.max(0L, distanceCm);
        }
    }

    private static final class PlayerStats {
        private static final PlayerStats ZERO = new PlayerStats();

        private long playTicks;
        private long blocksPlaced;
        private long blocksBroken;
        private long distanceCm;
        private boolean importedVanillaStats;
        private boolean skipNextPlayTick;
        private boolean hasLastPosition;
        private ResourceLocation lastDimension;
        private double lastX;
        private double lastY;
        private double lastZ;

        private Snapshot snapshot() {
            return new Snapshot(this.playTicks, this.blocksPlaced, this.blocksBroken, this.distanceCm);
        }

        private PlayerStats copy() {
            PlayerStats copy = new PlayerStats();
            copy.playTicks = this.playTicks;
            copy.blocksPlaced = this.blocksPlaced;
            copy.blocksBroken = this.blocksBroken;
            copy.distanceCm = this.distanceCm;
            copy.importedVanillaStats = this.importedVanillaStats;
            return copy;
        }

        private void mergeVanillaStats(ServerPlayer player) {
            this.playTicks = Math.max(this.playTicks, vanillaDeathPlayTicks(player));
            this.blocksPlaced = Math.max(this.blocksPlaced, vanillaBlocksPlaced(player));
            this.blocksBroken = Math.max(this.blocksBroken, vanillaBlocksBroken(player));
            this.distanceCm = Math.max(this.distanceCm, vanillaDistanceCm(player));
            this.importedVanillaStats = true;
            this.skipNextPlayTick = true;
        }

        private void setLastPosition(ResourceLocation dimension, double x, double y, double z) {
            this.hasLastPosition = true;
            this.lastDimension = dimension;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
        }

        private void clearLastPosition() {
            this.hasLastPosition = false;
            this.lastDimension = null;
        }

        private void saveActive(CompoundTag tag) {
            tag.putLong("PlayTicks", Math.max(0L, this.playTicks));
            tag.putLong("BlocksPlaced", Math.max(0L, this.blocksPlaced));
            tag.putLong("BlocksBroken", Math.max(0L, this.blocksBroken));
            tag.putLong("DistanceCm", Math.max(0L, this.distanceCm));
            tag.putBoolean("ImportedVanillaStats", this.importedVanillaStats);
        }

        private void saveDeath(CompoundTag tag) {
            tag.putLong("DeathPlayTicks", Math.max(0L, this.playTicks));
            tag.putLong("DeathBlocksPlaced", Math.max(0L, this.blocksPlaced));
            tag.putLong("DeathBlocksBroken", Math.max(0L, this.blocksBroken));
            tag.putLong("DeathDistanceCm", Math.max(0L, this.distanceCm));
            tag.putBoolean("DeathImportedVanillaStats", this.importedVanillaStats);
        }

        private static PlayerStats loadActive(CompoundTag tag) {
            PlayerStats stats = new PlayerStats();
            stats.playTicks = Math.max(0L, tag.getLong("PlayTicks"));
            stats.blocksPlaced = Math.max(0L, tag.getLong("BlocksPlaced"));
            stats.blocksBroken = Math.max(0L, tag.getLong("BlocksBroken"));
            stats.distanceCm = Math.max(0L, tag.getLong("DistanceCm"));
            stats.importedVanillaStats = tag.getBoolean("ImportedVanillaStats");
            return stats;
        }

        private static PlayerStats loadDeath(CompoundTag tag) {
            PlayerStats stats = new PlayerStats();
            stats.playTicks = Math.max(0L, tag.getLong("DeathPlayTicks"));
            stats.blocksPlaced = Math.max(0L, tag.getLong("DeathBlocksPlaced"));
            stats.blocksBroken = Math.max(0L, tag.getLong("DeathBlocksBroken"));
            stats.distanceCm = Math.max(0L, tag.getLong("DeathDistanceCm"));
            stats.importedVanillaStats = tag.getBoolean("DeathImportedVanillaStats");
            return stats;
        }
    }
}
