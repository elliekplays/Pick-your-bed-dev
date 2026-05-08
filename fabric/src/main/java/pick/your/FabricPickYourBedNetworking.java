package pick.your;

import pick.your.network.payload.BedListPayload;
import pick.your.network.payload.BedListRequestPayload;
import pick.your.network.payload.OpenEditorPayload;
import pick.your.network.payload.RenameRespawnPayload;
import pick.your.network.payload.SelectRespawnPayload;
import pick.your.network.payload.SelectionResultPayload;
import pick.your.network.payload.SurvivalStatsPayload;
import pick.your.network.payload.SurvivalStatsRequestPayload;
import pick.your.respawn.PickYourBedServer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class FabricPickYourBedNetworking {
    private FabricPickYourBedNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(BedListRequestPayload.TYPE, BedListRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RenameRespawnPayload.TYPE, RenameRespawnPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SelectRespawnPayload.TYPE, SelectRespawnPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SurvivalStatsRequestPayload.TYPE, SurvivalStatsRequestPayload.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(BedListPayload.TYPE, BedListPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenEditorPayload.TYPE, OpenEditorPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SelectionResultPayload.TYPE, SelectionResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SurvivalStatsPayload.TYPE, SurvivalStatsPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BedListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            runOnServer(context, "list request", () -> PickYourBedServer.handleListRequest(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(RenameRespawnPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            runOnServer(context, "rename", () -> PickYourBedServer.handleRename(player, payload.id(), payload.name()));
        });
        ServerPlayNetworking.registerGlobalReceiver(SelectRespawnPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            runOnServer(context, "select", () -> PickYourBedServer.handleSelect(player, payload.id()));
        });
        ServerPlayNetworking.registerGlobalReceiver(SurvivalStatsRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            runOnServer(context, "survival stats request", () -> PickYourBedServer.handleSurvivalStatsRequest(player));
        });
    }

    private static void runOnServer(ServerPlayNetworking.Context context, String action, Runnable handler) {
        context.server().execute(() -> {
            try {
                handler.run();
            } catch (RuntimeException exception) {
                Constants.LOG.error("Failed to handle {} packet on the server", action, exception);
            }
        });
    }
}
