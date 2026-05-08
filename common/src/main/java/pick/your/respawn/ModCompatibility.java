package pick.your.respawn;

import pick.your.Constants;
import pick.your.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public final class ModCompatibility {
    public static final String NOT_A_RESPAWN_POINT = "Not a respawn point";
    public static final String MOVING_CONTRAPTION = "Moving contraption";

    private ModCompatibility() {
    }

    public static Optional<String> unsupportedReason(Level level, BlockPos pos, BlockState state) {
        if (isComfortsSleepBlock(state)) {
            return Optional.of(NOT_A_RESPAWN_POINT);
        }
        if (isSableSubLevelPosition(level, pos)) {
            return Optional.of(MOVING_CONTRAPTION);
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

    private static boolean isSableSubLevelPosition(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        if (!Services.PLATFORM.isModLoaded("sable") && !Services.PLATFORM.isModLoaded("simulated") && !Services.PLATFORM.isModLoaded("aeronautics")) {
            return false;
        }
        return SableReflection.contains(level, pos);
    }

    private static final class SableReflection {
        private static boolean initialized;
        private static boolean unavailable;
        private static Object helper;
        private static Method getContaining;

        private static boolean contains(Level level, BlockPos pos) {
            try {
                initialize();
                if (unavailable || helper == null || getContaining == null) {
                    return false;
                }
                return getContaining.invoke(helper, level, pos.getX() >> 4, pos.getZ() >> 4) != null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                unavailable = true;
                Constants.LOG.debug("Sable compatibility lookup unavailable", exception);
                return false;
            }
        }

        private static void initialize() throws ReflectiveOperationException {
            if (initialized) {
                return;
            }
            initialized = true;
            Class<?> companion = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            Field helperField = companion.getField("INSTANCE");
            helper = helperField.get(null);
            getContaining = companion.getMethod("getContaining", Level.class, int.class, int.class);
        }
    }
}
