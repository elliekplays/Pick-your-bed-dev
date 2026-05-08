package pick.your;

import pick.your.respawn.PickYourBedServer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.api.ModInitializer;

public class PickYourBed implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricPickYourBedNetworking.register();
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> PickYourBedServer.handleAfterRespawn(newPlayer));
        CommonClass.init();
    }
}
