package pick.your;

import pick.your.network.payload.BedListPayload;
import pick.your.network.payload.BedListRequestPayload;
import pick.your.network.payload.OpenEditorPayload;
import pick.your.network.payload.RenameRespawnPayload;
import pick.your.network.payload.SelectRespawnPayload;
import pick.your.network.payload.SelectionResultPayload;
import pick.your.respawn.PickYourBedServer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class FabricPickYourBedNetworking {
    private FabricPickYourBedNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(BedListRequestPayload.TYPE, BedListRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RenameRespawnPayload.TYPE, RenameRespawnPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SelectRespawnPayload.TYPE, SelectRespawnPayload.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(BedListPayload.TYPE, BedListPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OpenEditorPayload.TYPE, OpenEditorPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SelectionResultPayload.TYPE, SelectionResultPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BedListRequestPayload.TYPE, (payload, context) -> PickYourBedServer.handleListRequest(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(RenameRespawnPayload.TYPE, (payload, context) -> PickYourBedServer.handleRename(context.player(), payload.id(), payload.name()));
        ServerPlayNetworking.registerGlobalReceiver(SelectRespawnPayload.TYPE, (payload, context) -> PickYourBedServer.handleSelect(context.player(), payload.id()));
    }
}
