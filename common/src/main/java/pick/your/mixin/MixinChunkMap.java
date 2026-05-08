package pick.your.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public class MixinChunkMap {
    @Inject(method = "skipPlayer", at = @At("HEAD"), cancellable = true)
    private void pick_your_bed$spectatorsGenerateChunks(ServerPlayer player, CallbackInfoReturnable<Boolean> info) {
        if (player.isSpectator()) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "playerIsCloseEnoughForSpawning", at = @At("HEAD"), cancellable = true)
    private void pick_your_bed$spectatorsTickChunks(ServerPlayer player, ChunkPos chunkPos, CallbackInfoReturnable<Boolean> info) {
        if (player.isSpectator()) {
            info.setReturnValue(distanceToChunkCenterSqr(chunkPos, player) < 16384.0D);
        }
    }

    private static double distanceToChunkCenterSqr(ChunkPos chunkPos, ServerPlayer player) {
        double chunkCenterX = SectionPos.sectionToBlockCoord(chunkPos.x, 8);
        double chunkCenterZ = SectionPos.sectionToBlockCoord(chunkPos.z, 8);
        double xDistance = chunkCenterX - player.getX();
        double zDistance = chunkCenterZ - player.getZ();
        return xDistance * xDistance + zDistance * zDistance;
    }
}
