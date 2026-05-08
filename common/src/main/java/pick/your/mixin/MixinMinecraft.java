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
        Minecraft minecraft = (Minecraft)(Object)this;
        if (screen instanceof DeathScreen deathScreen) {
            replaceVanillaDeathScreen(minecraft, deathScreen);
            info.cancel();
            return;
        }

        if (screen == null && minecraft.level != null && minecraft.player != null && minecraft.player.isDeadOrDying()) {
            info.cancel();
            if (minecraft.player.shouldShowDeathScreen()) {
                minecraft.setScreen(new PickYourBedDeathScreen(null, minecraft.level.getLevelData().isHardcore()));
            } else {
                minecraft.player.respawn();
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void pick_your_bed$replaceExistingDeathScreen(CallbackInfo info) {
        Minecraft minecraft = (Minecraft)(Object)this;
        if (minecraft.screen instanceof DeathScreen deathScreen) {
            replaceVanillaDeathScreen(minecraft, deathScreen);
        } else if (minecraft.screen == null && minecraft.level != null && minecraft.player != null && minecraft.player.isDeadOrDying() && minecraft.player.shouldShowDeathScreen()) {
            minecraft.setScreen(new PickYourBedDeathScreen(null, minecraft.level.getLevelData().isHardcore()));
        }
    }

    private static void replaceVanillaDeathScreen(Minecraft minecraft, DeathScreen deathScreen) {
        DeathScreenAccessor accessor = (DeathScreenAccessor)deathScreen;
        Component cause = accessor.pick_your_bed$causeOfDeath();
        boolean hardcore = accessor.pick_your_bed$hardcore();
        minecraft.setScreen(new PickYourBedDeathScreen(cause, hardcore));
    }
}
