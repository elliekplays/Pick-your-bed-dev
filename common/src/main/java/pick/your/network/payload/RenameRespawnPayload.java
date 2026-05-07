package pick.your.network.payload;

import pick.your.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RenameRespawnPayload(long id, String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RenameRespawnPayload> TYPE = CustomPacketPayload.createType(Constants.MOD_ID + ":rename");
    public static final StreamCodec<FriendlyByteBuf, RenameRespawnPayload> STREAM_CODEC = CustomPacketPayload.codec(
        RenameRespawnPayload::write,
        RenameRespawnPayload::new
    );

    private RenameRespawnPayload(FriendlyByteBuf buf) {
        this(buf.readVarLong(), buf.readUtf(32));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarLong(this.id);
        buf.writeUtf(this.name, 32);
    }

    @Override
    public CustomPacketPayload.Type<RenameRespawnPayload> type() {
        return TYPE;
    }
}
