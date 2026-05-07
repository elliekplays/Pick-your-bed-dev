package pick.your.respawn;

import pick.your.Constants;
import pick.your.network.payload.BedListPayload;
import pick.your.network.payload.OpenEditorPayload;
import pick.your.network.payload.SelectionResultPayload;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.List;
import java.util.Optional;

public final class PickYourBedServer {
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
                return RespawnValidation.invalid("Broken or destroyed");
            }
            if (!BedBlock.canSetSpawn(level)) {
                return RespawnValidation.invalid("Beds do not work here");
            }

            BlockPos bedPos = state.getValue(BedBlock.PART) == BedPart.HEAD
                ? entry.pos()
                : entry.pos().relative(state.getValue(BedBlock.FACING));
            BlockState bedState = level.getBlockState(bedPos);
            if (!(bedState.getBlock() instanceof BedBlock)) {
                return RespawnValidation.invalid("Broken or destroyed");
            }

            return BedBlock.findStandUpPosition(EntityType.PLAYER, level, bedPos, bedState.getValue(BedBlock.FACING), 0.0F).isPresent()
                ? RespawnValidation.ok()
                : RespawnValidation.invalid("Obstructed");
        }

        if (!(state.getBlock() instanceof RespawnAnchorBlock)) {
            return RespawnValidation.invalid("Broken or destroyed");
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
}
