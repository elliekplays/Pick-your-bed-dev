package pick.your.respawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public final class ModCompatibility {
    public static final String NOT_A_RESPAWN_POINT = "Not a respawn point";

    private ModCompatibility() {
    }

    public static Optional<String> unsupportedReason(Level level, BlockPos pos, BlockState state) {
        if (isComfortsSleepBlock(state)) {
            return Optional.of(NOT_A_RESPAWN_POINT);
        }
        return Optional.empty();
    }

    public static boolean shouldRemoveAfterRespawn(String reason) {
        return NOT_A_RESPAWN_POINT.equals(reason);
    }

    public static boolean shouldLetVanillaHandleEditor(BlockState state) {
        return isComfortsSleepBlock(state);
    }

    private static boolean isComfortsSleepBlock(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!"comforts".equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path.contains("sleeping_bag") || path.contains("hammock");
    }
}
