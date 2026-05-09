package pick.your.network.payload;

import pick.your.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WorldSpawnRespawnPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorldSpawnRespawnPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "world_spawn_respawn"));
    public static final StreamCodec<FriendlyByteBuf, WorldSpawnRespawnPayload> STREAM_CODEC = CustomPacketPayload.codec(
        WorldSpawnRespawnPayload::write,
        WorldSpawnRespawnPayload::new
    );

    private WorldSpawnRespawnPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public CustomPacketPayload.Type<WorldSpawnRespawnPayload> type() {
        return TYPE;
    }
}
