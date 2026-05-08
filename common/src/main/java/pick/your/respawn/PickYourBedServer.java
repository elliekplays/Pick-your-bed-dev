package pick.your.respawn;

import pick.your.Constants;
import pick.your.network.payload.BedListPayload;
import pick.your.network.payload.OpenEditorPayload;
import pick.your.network.payload.SelectionResultPayload;
import pick.your.network.payload.SurvivalStatsPayload;
import pick.your.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PickYourBedServer {
    private static final String BROKEN_OR_DESTROYED = "Broken or destroyed";
    private static final Map<UUID, RespawnSnapshot> PENDING_RESPAWN_RESTORES = new ConcurrentHashMap<>();

    private PickYourBedServer() {
    }

    public static void recordPlacedRespawn(ServerPlayer player, RespawnEntryType type, BlockPos pos) {
        try {
            recordPlacedRespawnUnsafe(player, type, pos);
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to record {} respawn at {}", type.serializedName(), pos, exception);
        }
    }

    private static void recordPlacedRespawnUnsafe(ServerPlayer player, RespawnEntryType type, BlockPos pos) {
        RespawnSavedData data = RespawnSavedData.get(player.server);
        data.addOrUpdate(player.getUUID(), type, player.level().dimension().location(), pos.immutable());
        syncList(player);
    }

    public static void openEditor(ServerPlayer player, RespawnEntryType type, BlockPos pos) {
        try {
            openEditorUnsafe(player, type, pos);
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to open respawn editor for {} at {}", type.serializedName(), pos, exception);
            notifyPlayer(player, "Could not open respawn editor");
        }
    }

    private static void openEditorUnsafe(ServerPlayer player, RespawnEntryType type, BlockPos pos) {
        RespawnSavedData data = RespawnSavedData.get(player.server);
        RespawnEntry entry = data.addOrUpdate(player.getUUID(), type, player.level().dimension().location(), pos.immutable());
        sendToClient(player, new OpenEditorPayload(toView(player.server, entry)));
        syncList(player);
    }

    public static void handleListRequest(ServerPlayer player) {
        syncList(player);
    }

    public static void handleSurvivalStatsRequest(ServerPlayer player) {
        SurvivalStatsSavedData data = SurvivalStatsSavedData.get(player.server);
        data.initializeForServer(player.server);
        if (data.modTimerEnabled()) {
            recordHardcoreDeathIfNeeded(data, player);
            OptionalLong deathPlayTicks = data.deathPlayTicks(player.getUUID());
            sendToClient(player, new SurvivalStatsPayload(true, deathPlayTicks.orElse(data.playTicks(player.getUUID()))));
            return;
        }

        sendToClient(player, new SurvivalStatsPayload(true, vanillaDeathPlayTicks(player)));
    }

    public static void handleServerStarted(MinecraftServer server) {
        SurvivalStatsSavedData.get(server).initializeForServer(server);
    }

    public static void handleServerTick(MinecraftServer server) {
        SurvivalStatsSavedData data = SurvivalStatsSavedData.get(server);
        data.initializeForServer(server);
        if (!data.modTimerEnabled()) {
            return;
        }

        boolean changed = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            changed |= recordHardcoreDeathIfNeeded(data, player);
            changed |= data.tickPlayer(player);
        }
        if (changed && server.getTickCount() % 100 == 0) {
            data.setDirty();
        }
    }

    public static void handleAfterRespawn(ServerPlayer player) {
        restoreOriginalRespawn(player);
        removeBrokenRespawnsAfterRespawn(player);
    }

    private static void removeBrokenRespawnsAfterRespawn(ServerPlayer player) {
        try {
            RespawnSavedData data = RespawnSavedData.get(player.server);
            int removed = data.removeEntries(player.getUUID(), entry -> isBrokenOrDestroyed(validate(player.server, entry)));
            if (removed > 0) {
                syncList(player);
            }
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to remove broken respawn points for {}", player.getGameProfile().getName(), exception);
        }
    }

    public static void handleRename(ServerPlayer player, long id, String name) {
        RespawnSavedData data = RespawnSavedData.get(player.server);
        if (data.rename(player.getUUID(), id, name)) {
            syncList(player);
        }
    }

    public static void handleSelect(ServerPlayer player, long id) {
        try {
            handleSelectUnsafe(player, id);
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to select respawn point {}", id, exception);
            sendToClient(player, new SelectionResultPayload(false, "Respawn selection failed"));
        }
    }

    private static void handleSelectUnsafe(ServerPlayer player, long id) {
        RespawnSavedData data = RespawnSavedData.get(player.server);
        Optional<RespawnEntry> optional = data.find(player.getUUID(), id);
        if (optional.isEmpty()) {
            syncList(player);
            sendToClient(player, new SelectionResultPayload(false, "Respawn point was not found"));
            return;
        }

        RespawnEntry entry = optional.get();
        RespawnValidation validation = validate(player.server, entry);
        if (!validation.valid()) {
            syncList(player);
            sendToClient(player, new SelectionResultPayload(false, validation.reason()));
            return;
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, entry.dimension());
        PENDING_RESPAWN_RESTORES.computeIfAbsent(player.getUUID(), unused -> RespawnSnapshot.capture(player));
        player.setRespawnPosition(dimension, entry.pos(), player.getYRot(), false, false);
        sendToClient(player, new SelectionResultPayload(true, ""));
    }

    public static void syncList(ServerPlayer player) {
        RespawnSavedData data = RespawnSavedData.get(player.server);
        List<RespawnEntryView> views = data.entriesFor(player.getUUID()).stream()
            .map(entry -> toView(player.server, entry))
            .toList();
        sendToClient(player, new BedListPayload(views));
    }

    public static RespawnEntryView toView(MinecraftServer server, RespawnEntry entry) {
        RespawnValidation validation = validate(server, entry);
        return new RespawnEntryView(
            entry.id(),
            entry.type(),
            entry.dimension(),
            entry.pos(),
            entry.name(),
            validation.valid(),
            validation.reason()
        );
    }

    public static RespawnValidation validate(MinecraftServer server, RespawnEntry entry) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, entry.dimension()));
        if (level == null) {
            return RespawnValidation.invalid("Dimension unavailable");
        }

        BlockState state = level.getBlockState(entry.pos());
        if (entry.type() == RespawnEntryType.BED) {
            if (!(state.getBlock() instanceof BedBlock)) {
                return RespawnValidation.invalid(BROKEN_OR_DESTROYED);
            }
            if (!BedBlock.canSetSpawn(level)) {
                return RespawnValidation.invalid("Beds do not work here");
            }

            BlockPos bedPos = state.getValue(BedBlock.PART) == BedPart.HEAD
                ? entry.pos()
                : entry.pos().relative(state.getValue(BedBlock.FACING));
            BlockState bedState = level.getBlockState(bedPos);
            if (!(bedState.getBlock() instanceof BedBlock)) {
                return RespawnValidation.invalid(BROKEN_OR_DESTROYED);
            }

            return BedBlock.findStandUpPosition(EntityType.PLAYER, level, bedPos, bedState.getValue(BedBlock.FACING), 0.0F).isPresent()
                ? RespawnValidation.ok()
                : RespawnValidation.invalid("Obstructed");
        }

        if (!(state.getBlock() instanceof RespawnAnchorBlock)) {
            return RespawnValidation.invalid(BROKEN_OR_DESTROYED);
        }
        if (!RespawnAnchorBlock.canSetSpawn(level)) {
            return RespawnValidation.invalid("Respawn anchors do not work here");
        }
        if (state.getValue(RespawnAnchorBlock.CHARGE) <= 0) {
            return RespawnValidation.invalid("No charges");
        }

        return RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, entry.pos()).isPresent()
            ? RespawnValidation.ok()
            : RespawnValidation.invalid("Obstructed");
    }

    public static void notifyPlayer(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private static void sendToClient(ServerPlayer player, CustomPacketPayload payload) {
        try {
            Services.PLATFORM.sendToClient(player, payload);
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to send {} packet to {}", payload.type().id(), player.getGameProfile().getName(), exception);
        }
    }

    private static boolean isBrokenOrDestroyed(RespawnValidation validation) {
        return !validation.valid() && BROKEN_OR_DESTROYED.equals(validation.reason());
    }

    private static boolean recordHardcoreDeathIfNeeded(SurvivalStatsSavedData data, ServerPlayer player) {
        if (!player.server.isHardcore()) {
            return false;
        }
        if (player.isDeadOrDying() || player.isSpectator() && player.getLastDeathLocation().isPresent()) {
            return data.recordDeath(player);
        }
        return false;
    }

    private static long vanillaDeathPlayTicks(ServerPlayer player) {
        long playTicks = Math.max(0, player.getStats().getValue(Stats.CUSTOM, Stats.PLAY_TIME));
        int deaths = Math.max(0, player.getStats().getValue(Stats.CUSTOM, Stats.DEATHS));
        int timeSinceDeath = Math.max(0, player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH));
        return deaths > 0 ? Math.max(0L, playTicks - timeSinceDeath) : playTicks;
    }

    private static void restoreOriginalRespawn(ServerPlayer player) {
        RespawnSnapshot snapshot = PENDING_RESPAWN_RESTORES.remove(player.getUUID());
        if (snapshot == null) {
            return;
        }

        try {
            snapshot.restore(player);
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to restore original respawn point for {}", player.getGameProfile().getName(), exception);
        }
    }

    private record RespawnSnapshot(ResourceKey<Level> dimension, BlockPos position, float angle, boolean forced) {
        static RespawnSnapshot capture(ServerPlayer player) {
            BlockPos position = player.getRespawnPosition();
            return new RespawnSnapshot(
                player.getRespawnDimension(),
                position == null ? null : position.immutable(),
                player.getRespawnAngle(),
                player.isRespawnForced()
            );
        }

        void restore(ServerPlayer player) {
            player.setRespawnPosition(this.dimension, this.position, this.angle, this.forced, false);
        }
    }
}
