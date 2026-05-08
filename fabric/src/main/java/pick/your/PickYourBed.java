package pick.your;

import pick.your.respawn.PickYourBedServer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.api.ModInitializer;

public class PickYourBed implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricPickYourBedNetworking.register();
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> PickYourBedServer.handleAfterRespawn(newPlayer));
        ServerLifecycleEvents.SERVER_STARTED.register(PickYourBedServer::handleServerStarted);
        ServerTickEvents.END_SERVER_TICK.register(PickYourBedServer::handleServerTick);
        CommonClass.init();
    }
}
