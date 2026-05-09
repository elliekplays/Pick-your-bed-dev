package pick.your;

import pick.your.client.PickYourBedClient;
import pick.your.network.payload.BedListPayload;
import pick.your.network.payload.BedListRequestPayload;
import pick.your.network.payload.LastRespawnPayload;
import pick.your.network.payload.OpenEditorPayload;
import pick.your.network.payload.RenameRespawnPayload;
import pick.your.network.payload.SelectRespawnPayload;
import pick.your.network.payload.SelectionResultPayload;
import pick.your.network.payload.SurvivalStatsPayload;
import pick.your.network.payload.SurvivalStatsRequestPayload;
import pick.your.network.payload.WorldSpawnRespawnPayload;
import pick.your.respawn.PickYourBedServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

public final class NeoForgePickYourBedNetworking {
    private NeoForgePickYourBedNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(BedListRequestPayload.TYPE, BedListRequestPayload.STREAM_CODEC,
            (payload, context) -> handleServer(context, "list request", PickYourBedServer::handleListRequest));
        registrar.playToServer(RenameRespawnPayload.TYPE, RenameRespawnPayload.STREAM_CODEC,
            (payload, context) -> handleServer(context, "rename", player -> PickYourBedServer.handleRename(player, payload.id(), payload.name())));
        registrar.playToServer(SelectRespawnPayload.TYPE, SelectRespawnPayload.STREAM_CODEC,
            (payload, context) -> handleServer(context, "select", player -> PickYourBedServer.handleSelect(player, payload.id())));
        registrar.playToServer(SurvivalStatsRequestPayload.TYPE, SurvivalStatsRequestPayload.STREAM_CODEC,
            (payload, context) -> handleServer(context, "survival stats request", PickYourBedServer::handleSurvivalStatsRequest));
        registrar.playToServer(WorldSpawnRespawnPayload.TYPE, WorldSpawnRespawnPayload.STREAM_CODEC,
            (payload, context) -> handleServer(context, "world spawn respawn", PickYourBedServer::handleWorldSpawnRespawn));
        registrar.playToServer(LastRespawnPayload.TYPE, LastRespawnPayload.STREAM_CODEC,
            (payload, context) -> handleServer(context, "last respawn", PickYourBedServer::handleLastRespawn));

        registrar.playToClient(BedListPayload.TYPE, BedListPayload.STREAM_CODEC, NeoForgePickYourBedNetworking::handleList);
        registrar.playToClient(OpenEditorPayload.TYPE, OpenEditorPayload.STREAM_CODEC, NeoForgePickYourBedNetworking::handleOpenEditor);
        registrar.playToClient(SelectionResultPayload.TYPE, SelectionResultPayload.STREAM_CODEC, NeoForgePickYourBedNetworking::handleSelectionResult);
        registrar.playToClient(SurvivalStatsPayload.TYPE, SurvivalStatsPayload.STREAM_CODEC, NeoForgePickYourBedNetworking::handleSurvivalStats);
    }

    private static void handleServer(IPayloadContext context, String action, Consumer<ServerPlayer> handler) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                try {
                    handler.accept(player);
                } catch (RuntimeException exception) {
                    Constants.LOG.error("Failed to handle {} packet on the server", action, exception);
                }
            }
        });
    }

    private static void handleList(BedListPayload payload, IPayloadContext context) {
        handleClient(context, "list", () -> PickYourBedClient.handleList(payload));
    }

    private static void handleOpenEditor(OpenEditorPayload payload, IPayloadContext context) {
        handleClient(context, "open editor", () -> PickYourBedClient.handleOpenEditor(payload));
    }

    private static void handleSelectionResult(SelectionResultPayload payload, IPayloadContext context) {
        handleClient(context, "selection result", () -> PickYourBedClient.handleSelectionResult(payload));
    }

    private static void handleSurvivalStats(SurvivalStatsPayload payload, IPayloadContext context) {
        handleClient(context, "survival stats", () -> PickYourBedClient.handleSurvivalStats(payload));
    }

    private static void handleClient(IPayloadContext context, String action, Runnable handler) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) {
                try {
                    handler.run();
                } catch (RuntimeException exception) {
                    Constants.LOG.error("Failed to handle {} packet on the client", action, exception);
                }
            }
        });
    }
}
