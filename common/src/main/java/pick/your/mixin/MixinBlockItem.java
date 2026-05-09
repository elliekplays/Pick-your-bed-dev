package pick.your.mixin;

import pick.your.respawn.PickYourBedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class MixinBlockItem {
    @Inject(method = "place", at = @At("HEAD"))
    private void pick_your_bed$ensureSurvivalStats(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> info) {
        if (!context.getLevel().isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            PickYourBedServer.ensureSurvivalStats(player);
        }
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void pick_your_bed$recordPlacedBlock(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> info) {
        if (!context.getLevel().isClientSide && info.getReturnValue().consumesAction() && context.getPlayer() instanceof ServerPlayer player) {
            PickYourBedServer.recordBlockPlaced(player);
        }
    }
}
