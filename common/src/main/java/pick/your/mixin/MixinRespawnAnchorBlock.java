package pick.your.mixin;

import pick.your.respawn.RespawnEntryType;
import pick.your.respawn.PickYourBedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RespawnAnchorBlock.class)
public class MixinRespawnAnchorBlock {
    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void pick_your_bed$editAnchorName(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult,
        CallbackInfoReturnable<InteractionResult> info
    ) {
        if (!player.isShiftKeyDown()) {
            return;
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PickYourBedServer.openEditor(serverPlayer, RespawnEntryType.RESPAWN_ANCHOR, pos);
        }
        info.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }

    @Inject(method = "useWithoutItem", at = @At("TAIL"))
    private void pick_your_bed$recordUsedAnchor(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult,
        CallbackInfoReturnable<InteractionResult> info
    ) {
        if (level.isClientSide || player.isShiftKeyDown() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        BlockPos currentRespawn = serverPlayer.getRespawnPosition();
        if (currentRespawn != null && currentRespawn.equals(pos) && level.dimension().equals(serverPlayer.getRespawnDimension())) {
            PickYourBedServer.recordCurrentRespawn(serverPlayer);
        }
    }
}
