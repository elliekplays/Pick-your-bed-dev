package pick.your;

import pick.your.respawn.PickYourBedServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod(Constants.MOD_ID)
public class PickYourBed {

    public PickYourBed(IEventBus eventBus) {
        eventBus.addListener(NeoForgePickYourBedNetworking::register);
        NeoForge.EVENT_BUS.addListener(PickYourBed::onPlayerRespawn);
        CommonClass.init();
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PickYourBedServer.removeBrokenRespawnsAfterRespawn(player);
        }
    }
}
