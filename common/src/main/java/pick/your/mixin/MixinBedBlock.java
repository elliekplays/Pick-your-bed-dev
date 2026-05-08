package pick.your.mixin;

import pick.your.respawn.ModCompatibility;
import pick.your.respawn.PickYourBedServer;
import pick.your.respawn.RespawnEntryType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedBlock.class)
public class MixinBedBlock {
    @Inject(method = "setPlacedBy", at = @At("TAIL"))
    private void pick_your_bed$recordPlacedBed(
        Level level,
        BlockPos pos,
        BlockState state,
        LivingEntity placer,
        ItemStack stack,
        CallbackInfo info
    ) {
        if (!level.isClientSide && placer instanceof ServerPlayer player) {
            BlockPos headPos = state.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(BedBlock.FACING));
            PickYourBedServer.recordPlacedRespawn(player, RespawnEntryType.BED, headPos);
        }
    }

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void pick_your_bed$editBedName(
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
        if (ModCompatibility.shouldLetVanillaHandleEditor(state)) {
            return;
        }

        BlockPos headPos = state.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(BedBlock.FACING));
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PickYourBedServer.openEditor(serverPlayer, RespawnEntryType.BED, headPos);
        }
        info.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
    }

    @Inject(method = "useWithoutItem", at = @At("TAIL"))
    private void pick_your_bed$recordUsedBed(
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
        if (currentRespawn == null || !level.dimension().equals(serverPlayer.getRespawnDimension())) {
            return;
        }

        BlockPos headPos = state.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(BedBlock.FACING));
        BlockPos footPos = state.getValue(BedBlock.PART) == BedPart.FOOT ? pos : pos.relative(state.getValue(BedBlock.FACING).getOpposite());
        if (currentRespawn.equals(headPos) || currentRespawn.equals(footPos)) {
            PickYourBedServer.recordCurrentRespawn(serverPlayer);
        }
    }
}
