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
        ClientPlayNetworking.registerGlobalReceiver(BedListPayload.TYPE,
            (payload, context) -> runOnClient(context, "list", () -> PickYourBedClient.handleList(payload)));
        ClientPlayNetworking.registerGlobalReceiver(OpenEditorPayload.TYPE,
            (payload, context) -> runOnClient(context, "open editor", () -> PickYourBedClient.handleOpenEditor(payload)));
        ClientPlayNetworking.registerGlobalReceiver(SelectionResultPayload.TYPE,
            (payload, context) -> runOnClient(context, "selection result", () -> PickYourBedClient.handleSelectionResult(payload)));
    }

    private static void runOnClient(ClientPlayNetworking.Context context, String action, Runnable handler) {
        context.client().execute(() -> {
            try {
                handler.run();
            } catch (RuntimeException exception) {
                Constants.LOG.error("Failed to handle {} packet on the client", action, exception);
            }
        });
    }
}
