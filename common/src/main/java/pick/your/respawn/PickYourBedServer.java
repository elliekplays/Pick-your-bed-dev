package pick.your.respawn;

import pick.your.Constants;
import pick.your.network.payload.BedListPayload;
import pick.your.network.payload.OpenEditorPayload;
import pick.your.network.payload.SelectionResultPayload;
import pick.your.network.payload.SurvivalStatsPayload;
import pick.your.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.List;
import java.util.Optional;

public final class PickYourBedServer {
    private static final String BROKEN_OR_DESTROYED = "Broken or destroyed";
    private static final String OBSTRUCTED = "Obstructed";
    private static final String OBSTRUCTED_FALLBACK_MESSAGE = "Last respawn was obstructed. Using previous respawn point.";
    private static final String LIMIT_REACHED_MESSAGE = "Allowed respawn points by the server reached. This %s won't register.";

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
        RespawnValidation compatibility = validateCompatibility(player.level(), pos);
        if (!compatibility.valid()) {
            return;
        }

        RespawnSavedData data = RespawnSavedData.get(player.server);
        addOrUpdateRespawn(player, data, type, player.level().dimension().location(), pos.immutable(), true)
            .ifPresent(entry -> syncList(player));
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
        RespawnValidation compatibility = validateCompatibility(player.level(), pos);
        if (!compatibility.valid()) {
            notifyPlayer(player, compatibility.reason());
            return;
        }

        RespawnSavedData data = RespawnSavedData.get(player.server);
        Optional<RespawnEntry> entry = addOrUpdateRespawn(player, data, type, player.level().dimension().location(), pos.immutable(), true);
        if (entry.isEmpty()) {
            return;
        }

        sendToClient(player, new OpenEditorPayload(toView(player.server, entry.get())));
        syncList(player);
    }

    public static void handleListRequest(ServerPlayer player) {
        try {
            ensureCurrentRespawnKnown(player);
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to include current respawn point for {}", player.getGameProfile().getName(), exception);
        }
        syncList(player);
    }

    public static void recordCurrentRespawn(ServerPlayer player) {
        try {
            if (ensureCurrentRespawnKnown(player)) {
                syncList(player);
            }
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to record current respawn point for {}", player.getGameProfile().getName(), exception);
        }
    }

    public static void handleSurvivalStatsRequest(ServerPlayer player) {
        SurvivalStatsSavedData data = SurvivalStatsSavedData.get(player.server);
        data.initializeForServer(player.server);
        recordHardcoreDeathIfNeeded(data, player);
        SurvivalStatsSavedData.Snapshot stats = data.deathStats(player.getUUID()).orElseGet(() -> data.stats(player));
        sendToClient(player, new SurvivalStatsPayload(
            true,
            stats.playTicks(),
            stats.blocksPlaced(),
            stats.blocksBroken(),
            stats.distanceCm()
        ));
    }

    public static void handleServerStarted(MinecraftServer server) {
        PickYourBedConfig.reload();
        SurvivalStatsSavedData.get(server).initializeForServer(server);
    }

    public static void handleServerTick(MinecraftServer server) {
        SurvivalStatsSavedData data = SurvivalStatsSavedData.get(server);
        data.initializeForServer(server);

        boolean changed = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            changed |= recordHardcoreDeathIfNeeded(data, player);
            changed |= data.tickPlayer(player);
        }
        if (changed && server.getTickCount() % 100 == 0) {
            data.setDirty();
        }
    }

    public static void recordBlockPlaced(ServerPlayer player) {
        try {
            SurvivalStatsSavedData.get(player.server).recordBlockPlaced(player);
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to record placed block for {}", player.getGameProfile().getName(), exception);
        }
    }

    public static void recordBlockBroken(ServerPlayer player) {
        try {
            SurvivalStatsSavedData.get(player.server).recordBlockBroken(player);
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to record broken block for {}", player.getGameProfile().getName(), exception);
        }
    }

    public static void ensureSurvivalStats(ServerPlayer player) {
        try {
            SurvivalStatsSavedData.get(player.server).stats(player);
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to initialize survival stats for {}", player.getGameProfile().getName(), exception);
        }
    }

    public static void handleAfterRespawn(ServerPlayer player) {
        removeInvalidRespawnsAfterRespawn(player);
    }

    private static void removeInvalidRespawnsAfterRespawn(ServerPlayer player) {
        try {
            RespawnSavedData data = RespawnSavedData.get(player.server);
            int removed = data.removeEntries(player.getUUID(), entry -> shouldRemoveAfterRespawn(validate(player.server, entry)));
            if (removed > 0) {
                syncList(player);
            }
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to remove invalid respawn points for {}", player.getGameProfile().getName(), exception);
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

    public static void handleWorldSpawnRespawn(ServerPlayer player) {
        try {
            player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
            sendToClient(player, new SelectionResultPayload(true, ""));
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to select world spawn respawn for {}", player.getGameProfile().getName(), exception);
            sendToClient(player, new SelectionResultPayload(false, "World spawn respawn failed"));
        }
    }

    public static void handleLastRespawn(ServerPlayer player) {
        try {
            RespawnSavedData data = RespawnSavedData.get(player.server);
            ensureCurrentRespawnKnown(player);
            Optional<RespawnChoice> choice = findLastRespawnOrFallback(player, data.entriesFor(player.getUUID()));
            if (choice.isEmpty()) {
                syncList(player);
                sendToClient(player, new SelectionResultPayload(false, "No usable respawn point found"));
                return;
            }

            setRespawnPosition(player, data, choice.get().entry());
            syncList(player);
            sendToClient(player, new SelectionResultPayload(true, choice.get().obstructedFallback() ? OBSTRUCTED_FALLBACK_MESSAGE : ""));
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to select last respawn fallback for {}", player.getGameProfile().getName(), exception);
            sendToClient(player, new SelectionResultPayload(false, "Last respawn selection failed"));
        }
    }

    private static void handleSelectUnsafe(ServerPlayer player, long id) {
        ensureCurrentRespawnKnown(player);
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
            List<RespawnEntry> entries = data.entriesFor(player.getUUID());
            Optional<RespawnEntry> fallback = findFallbackAfter(player, data.respawnHistoryFor(player.getUUID()), entry.id())
                .or(() -> findFallbackAfter(player, entries, entry.id()))
                .or(() -> findFirstValidExcluding(player, entries, entry.id()));
            if (fallback.isPresent()) {
                setRespawnPosition(player, data, fallback.get());
                syncList(player);
                sendToClient(player, new SelectionResultPayload(true, ""));
                return;
            }

            syncList(player);
            sendToClient(player, new SelectionResultPayload(false, validation.reason()));
            return;
        }

        setRespawnPosition(player, data, entry);
        sendToClient(player, new SelectionResultPayload(true, ""));
    }

    public static void syncList(ServerPlayer player) {
        RespawnSavedData data = RespawnSavedData.get(player.server);
        List<RespawnEntryView> views = data.entriesFor(player.getUUID()).stream()
            .map(entry -> toView(player.server, entry))
            .toList();
        sendToClient(player, new BedListPayload(views));
    }

    private static boolean ensureCurrentRespawnKnown(ServerPlayer player) {
        BlockPos pos = player.getRespawnPosition();
        if (pos == null) {
            return false;
        }

        ResourceKey<Level> dimension = player.getRespawnDimension();
        if (dimension == null) {
            return false;
        }

        ServerLevel level = player.server.getLevel(dimension);
        if (level == null) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        RespawnEntryType type;
        BlockPos savedPos = pos;
        if (state.getBlock() instanceof BedBlock) {
            savedPos = state.getValue(BedBlock.PART) == BedPart.HEAD
                ? pos
                : pos.relative(state.getValue(BedBlock.FACING));
            state = level.getBlockState(savedPos);
            if (!(state.getBlock() instanceof BedBlock)) {
                return false;
            }
            type = RespawnEntryType.BED;
        } else if (state.getBlock() instanceof RespawnAnchorBlock) {
            type = RespawnEntryType.RESPAWN_ANCHOR;
        } else {
            return false;
        }

        RespawnValidation compatibility = validateCompatibility(level, savedPos, state);
        if (!compatibility.valid()) {
            return false;
        }

        RespawnSavedData data = RespawnSavedData.get(player.server);
        Optional<RespawnEntry> entry = addOrUpdateRespawn(player, data, type, level.dimension().location(), savedPos.immutable(), false);
        if (entry.isEmpty()) {
            return false;
        }

        data.markRespawnUsed(player.getUUID(), entry.get().id());
        return true;
    }

    private static Optional<RespawnEntry> addOrUpdateRespawn(
        ServerPlayer player,
        RespawnSavedData data,
        RespawnEntryType type,
        ResourceLocation dimension,
        BlockPos pos,
        boolean notifyWhenLimited
    ) {
        BlockPos savedPos = pos.immutable();
        if (!canAddRespawn(player, data, type, dimension, savedPos, notifyWhenLimited)) {
            return Optional.empty();
        }

        return Optional.of(data.addOrUpdate(player.getUUID(), type, dimension, savedPos));
    }

    private static boolean canAddRespawn(
        ServerPlayer player,
        RespawnSavedData data,
        RespawnEntryType type,
        ResourceLocation dimension,
        BlockPos pos,
        boolean notifyWhenLimited
    ) {
        if (data.findPlace(player.getUUID(), type, dimension, pos).isPresent()) {
            return true;
        }

        PickYourBedConfig.Settings config = PickYourBedConfig.get();
        if (!config.limitsRespawnPoints() || data.entryCount(player.getUUID()) < config.maxRespawnPointsPerPlayer()) {
            return true;
        }

        if (notifyWhenLimited) {
            notifyLimitReached(player, type);
        }
        return false;
    }

    private static void notifyLimitReached(ServerPlayer player, RespawnEntryType type) {
        player.displayClientMessage(Component.literal(LIMIT_REACHED_MESSAGE.formatted(limitTypeName(type))).withStyle(ChatFormatting.RED), true);
    }

    private static String limitTypeName(RespawnEntryType type) {
        return type == RespawnEntryType.BED ? "bed" : "respawn anchor";
    }

    private static Optional<RespawnChoice> findLastRespawnOrFallback(ServerPlayer player, List<RespawnEntry> entries) {
        List<RespawnEntry> history = RespawnSavedData.get(player.server).respawnHistoryFor(player.getUUID());
        Optional<RespawnEntry> current = currentRespawnEntry(player, entries);
        if (current.isPresent()) {
            RespawnEntry entry = current.get();
            RespawnValidation validation = validate(player.server, entry);
            if (validation.valid()) {
                return Optional.of(new RespawnChoice(entry, false));
            }

            boolean obstructedFallback = OBSTRUCTED.equals(validation.reason());
            return findFallbackAfter(player, history, entry.id())
                .or(() -> findFallbackAfter(player, entries, entry.id()))
                .or(() -> findFirstValidExcluding(player, entries, entry.id()))
                .map(fallback -> new RespawnChoice(fallback, obstructedFallback));
        }

        return firstValid(history, player)
            .or(() -> firstValid(entries, player))
            .map(entry -> new RespawnChoice(entry, false));
    }

    private static Optional<RespawnEntry> currentRespawnEntry(ServerPlayer player, List<RespawnEntry> entries) {
        BlockPos pos = player.getRespawnPosition();
        ResourceKey<Level> dimension = player.getRespawnDimension();
        if (pos == null || dimension == null) {
            return Optional.empty();
        }

        return entries.stream()
            .filter(entry -> entry.dimension().equals(dimension.location()))
            .filter(entry -> matchesRespawnPosition(player.server, entry, pos))
            .findFirst();
    }

    private static boolean matchesRespawnPosition(MinecraftServer server, RespawnEntry entry, BlockPos pos) {
        if (entry.pos().equals(pos)) {
            return true;
        }
        if (entry.type() != RespawnEntryType.BED) {
            return false;
        }

        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, entry.dimension()));
        if (level == null) {
            return false;
        }

        BlockState state = level.getBlockState(entry.pos());
        if (!(state.getBlock() instanceof BedBlock)) {
            return false;
        }

        BlockPos headPos = state.getValue(BedBlock.PART) == BedPart.HEAD
            ? entry.pos()
            : entry.pos().relative(state.getValue(BedBlock.FACING));
        BlockPos footPos = state.getValue(BedBlock.PART) == BedPart.FOOT
            ? entry.pos()
            : entry.pos().relative(state.getValue(BedBlock.FACING).getOpposite());
        return pos.equals(headPos) || pos.equals(footPos);
    }

    private static Optional<RespawnEntry> findFallbackAfter(ServerPlayer player, List<RespawnEntry> entries, long entryId) {
        boolean afterEntry = false;
        for (RespawnEntry entry : entries) {
            if (entry.id() == entryId) {
                afterEntry = true;
                continue;
            }
            if (afterEntry && validate(player.server, entry).valid()) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static Optional<RespawnEntry> findFirstValidExcluding(ServerPlayer player, List<RespawnEntry> entries, long excludedId) {
        return entries.stream()
            .filter(entry -> entry.id() != excludedId)
            .filter(entry -> validate(player.server, entry).valid())
            .findFirst();
    }

    private static Optional<RespawnEntry> firstValid(List<RespawnEntry> entries, ServerPlayer player) {
        return entries.stream()
            .filter(entry -> validate(player.server, entry).valid())
            .findFirst();
    }

    private static void setRespawnPosition(ServerPlayer player, RespawnSavedData data, RespawnEntry entry) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, entry.dimension());
        player.setRespawnPosition(dimension, entry.pos(), player.getYRot(), false, false);
        data.markRespawnUsed(player.getUUID(), entry.id());
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
        RespawnValidation compatibility = validateCompatibility(level, entry.pos(), state);
        if (!compatibility.valid()) {
            return compatibility;
        }

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
                : RespawnValidation.invalid(OBSTRUCTED);
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
            : RespawnValidation.invalid(OBSTRUCTED);
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

    private static boolean shouldRemoveAfterRespawn(RespawnValidation validation) {
        return isBrokenOrDestroyed(validation) || !validation.valid() && ModCompatibility.shouldRemoveAfterRespawn(validation.reason());
    }

    private static RespawnValidation validateCompatibility(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return RespawnValidation.invalid("Invalid location");
        }
        return validateCompatibility(level, pos, level.getBlockState(pos));
    }

    private static RespawnValidation validateCompatibility(Level level, BlockPos pos, BlockState state) {
        return ModCompatibility.unsupportedReason(level, pos, state)
            .map(RespawnValidation::invalid)
            .orElseGet(RespawnValidation::ok);
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

    private record RespawnChoice(RespawnEntry entry, boolean obstructedFallback) {
    }
}
