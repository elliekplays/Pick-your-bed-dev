package pick.your;

import pick.your.client.PickYourBedClient;
import pick.your.network.payload.BedListPayload;
import pick.your.network.payload.OpenEditorPayload;
import pick.your.network.payload.SelectionResultPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class FabricPickYourBedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BedListPayload.TYPE, (payload, context) -> PickYourBedClient.handleList(payload));
        ClientPlayNetworking.registerGlobalReceiver(OpenEditorPayload.TYPE, (payload, context) -> PickYourBedClient.handleOpenEditor(payload));
        ClientPlayNetworking.registerGlobalReceiver(SelectionResultPayload.TYPE, (payload, context) -> PickYourBedClient.handleSelectionResult(payload));
    }
}
