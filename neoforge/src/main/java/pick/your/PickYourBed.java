package pick.your;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class PickYourBed {

    public PickYourBed(IEventBus eventBus) {
        eventBus.addListener(NeoForgePickYourBedNetworking::register);
        CommonClass.init();
    }
}
