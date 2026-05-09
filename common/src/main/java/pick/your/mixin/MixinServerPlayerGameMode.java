package pick.your.mixin;

import pick.your.respawn.PickYourBedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class MixinServerPlayerGameMode {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    protected ServerLevel level;

    @Unique
    private BlockState pick_your_bed$destroyedBlockState;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void pick_your_bed$ensureSurvivalStats(BlockPos pos, CallbackInfoReturnable<Boolean> info) {
        this.pick_your_bed$destroyedBlockState = this.level.getBlockState(pos);
        PickYourBedServer.ensureSurvivalStats(this.player);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void pick_your_bed$recordBrokenBlock(BlockPos pos, CallbackInfoReturnable<Boolean> info) {
        BlockState before = this.pick_your_bed$destroyedBlockState;
        this.pick_your_bed$destroyedBlockState = null;
        if (info.getReturnValue() && before != null && !before.isAir() && !this.level.getBlockState(pos).is(before.getBlock())) {
            PickYourBedServer.recordBlockBroken(this.player);
        }
    }
}
