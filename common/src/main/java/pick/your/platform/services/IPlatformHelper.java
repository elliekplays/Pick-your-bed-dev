package pick.your.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.nio.file.Paths;

public interface IPlatformHelper {
    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    default Path getConfigDirectory() {
        return Paths.get("config");
    }

    default void sendToServer(CustomPacketPayload payload) {
    }

    default void sendToClient(ServerPlayer player, CustomPacketPayload payload) {
    }
}
