package pick.your.network.payload;

import pick.your.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LastRespawnPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LastRespawnPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "last_respawn"));
    public static final StreamCodec<FriendlyByteBuf, LastRespawnPayload> STREAM_CODEC = CustomPacketPayload.codec(
        LastRespawnPayload::write,
        LastRespawnPayload::new
    );

    private LastRespawnPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public CustomPacketPayload.Type<LastRespawnPayload> type() {
        return TYPE;
    }
}
