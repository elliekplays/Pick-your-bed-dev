package pick.your;

import net.fabricmc.api.ModInitializer;

public class PickYourBed implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricPickYourBedNetworking.register();
        CommonClass.init();
    }
}
