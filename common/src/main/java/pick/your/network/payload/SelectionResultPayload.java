package pick.your.network.payload;

import pick.your.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SelectionResultPayload(boolean success, String message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SelectionResultPayload> TYPE = CustomPacketPayload.createType(Constants.MOD_ID + ":selection_result");
    public static final StreamCodec<FriendlyByteBuf, SelectionResultPayload> STREAM_CODEC = CustomPacketPayload.codec(
        SelectionResultPayload::write,
        SelectionResultPayload::new
    );

    private SelectionResultPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readUtf(80));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.success);
        buf.writeUtf(this.message, 80);
    }

    @Override
    public CustomPacketPayload.Type<SelectionResultPayload> type() {
        return TYPE;
    }
}
