package pick.your.network.payload;

import pick.your.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BedListRequestPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BedListRequestPayload> TYPE = CustomPacketPayload.createType(Constants.MOD_ID + ":list_request");
    public static final StreamCodec<FriendlyByteBuf, BedListRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(
        BedListRequestPayload::write,
        BedListRequestPayload::new
    );

    private BedListRequestPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public CustomPacketPayload.Type<BedListRequestPayload> type() {
        return TYPE;
    }
}
