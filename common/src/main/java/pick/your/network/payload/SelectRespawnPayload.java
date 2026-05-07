package pick.your.network.payload;

import pick.your.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectRespawnPayload(long id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SelectRespawnPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "select"));
    public static final StreamCodec<FriendlyByteBuf, SelectRespawnPayload> STREAM_CODEC = CustomPacketPayload.codec(
        SelectRespawnPayload::write,
        SelectRespawnPayload::new
    );

    private SelectRespawnPayload(FriendlyByteBuf buf) {
        this(buf.readVarLong());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarLong(this.id);
    }

    @Override
    public CustomPacketPayload.Type<SelectRespawnPayload> type() {
        return TYPE;
    }
}
