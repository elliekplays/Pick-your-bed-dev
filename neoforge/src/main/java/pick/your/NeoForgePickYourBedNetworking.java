package pick.your;

import pick.your.client.PickYourBedClient;
import pick.your.network.payload.BedListPayload;
import pick.your.network.payload.BedListRequestPayload;
import pick.your.network.payload.OpenEditorPayload;
import pick.your.network.payload.RenameRespawnPayload;
import pick.your.network.payload.SelectRespawnPayload;
import pick.your.network.payload.SelectionResultPayload;
import pick.your.respawn.PickYourBedServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgePickYourBedNetworking {
    private NeoForgePickYourBedNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(BedListRequestPayload.TYPE, BedListRequestPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                PickYourBedServer.handleListRequest(player);
            }
        });
        registrar.playToServer(RenameRespawnPayload.TYPE, RenameRespawnPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                PickYourBedServer.handleRename(player, payload.id(), payload.name());
            }
        });
        registrar.playToServer(SelectRespawnPayload.TYPE, SelectRespawnPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                PickYourBedServer.handleSelect(player, payload.id());
            }
        });

        registrar.playToClient(BedListPayload.TYPE, BedListPayload.STREAM_CODEC, NeoForgePickYourBedNetworking::handleList);
        registrar.playToClient(OpenEditorPayload.TYPE, OpenEditorPayload.STREAM_CODEC, NeoForgePickYourBedNetworking::handleOpenEditor);
        registrar.playToClient(SelectionResultPayload.TYPE, SelectionResultPayload.STREAM_CODEC, NeoForgePickYourBedNetworking::handleSelectionResult);
    }

    private static void handleList(BedListPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            PickYourBedClient.handleList(payload);
        }
    }

    private static void handleOpenEditor(OpenEditorPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            PickYourBedClient.handleOpenEditor(payload);
        }
    }

    private static void handleSelectionResult(SelectionResultPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            PickYourBedClient.handleSelectionResult(payload);
        }
    }
}
