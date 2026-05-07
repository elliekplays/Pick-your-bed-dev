package pick.your.mixin;

import pick.your.client.PickYourBedDeathScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void pick_your_bed$replaceDeathScreen(Screen screen, CallbackInfo info) {
        if (screen instanceof DeathScreen deathScreen && !(screen instanceof PickYourBedDeathScreen)) {
            DeathScreenAccessor accessor = (DeathScreenAccessor)deathScreen;
            Component cause = accessor.pick_your_bed$causeOfDeath();
            boolean hardcore = accessor.pick_your_bed$hardcore();
            info.cancel();
            ((Minecraft)(Object)this).setScreen(new PickYourBedDeathScreen(cause, hardcore));
        }
    }
}
