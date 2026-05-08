package pick.your;

import pick.your.respawn.PickYourBedServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Constants.MOD_ID)
public class PickYourBed {

    public PickYourBed(IEventBus eventBus) {
        eventBus.addListener(NeoForgePickYourBedNetworking::register);
        NeoForge.EVENT_BUS.addListener(PickYourBed::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(PickYourBed::onServerStarted);
        NeoForge.EVENT_BUS.addListener(PickYourBed::onServerTick);
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PickYourBedServer.handleAfterRespawn(player);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        PickYourBedServer.handleServerStarted(event.getServer());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        PickYourBedServer.handleServerTick(event.getServer());
    }
}
