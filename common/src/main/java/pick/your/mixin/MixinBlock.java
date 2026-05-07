package pick.your.mixin;

import pick.your.respawn.RespawnEntryType;
import pick.your.respawn.PickYourBedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class MixinBlock {
    @Inject(method = "setPlacedBy", at = @At("TAIL"))
    private void pick_your_bed$recordPlacedRespawnAnchor(
        Level level,
        BlockPos pos,
        BlockState state,
        LivingEntity placer,
        ItemStack stack,
        CallbackInfo info
    ) {
        if (!level.isClientSide && state.getBlock() instanceof RespawnAnchorBlock && placer instanceof ServerPlayer player) {
            PickYourBedServer.recordPlacedRespawn(player, RespawnEntryType.RESPAWN_ANCHOR, pos);
        }
    }
}
