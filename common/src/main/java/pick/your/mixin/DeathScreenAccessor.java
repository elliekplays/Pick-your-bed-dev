package pick.your.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DeathScreen.class)
public interface DeathScreenAccessor {
    @Accessor("causeOfDeath")
    Component pick_your_bed$causeOfDeath();

    @Accessor("hardcore")
    boolean pick_your_bed$hardcore();
}
