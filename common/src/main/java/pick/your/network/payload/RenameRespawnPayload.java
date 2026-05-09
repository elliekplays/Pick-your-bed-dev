package pick.your.network.payload;

import pick.your.Constants;
import pick.your.respawn.RespawnEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RenameRespawnPayload(long id, String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RenameRespawnPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "rename"));
    public static final StreamCodec<FriendlyByteBuf, RenameRespawnPayload> STREAM_CODEC = CustomPacketPayload.codec(
        RenameRespawnPayload::write,
        RenameRespawnPayload::new
    );

    private RenameRespawnPayload(FriendlyByteBuf buf) {
        this(buf.readVarLong(), buf.readUtf(RespawnEntry.MAX_NAME_LENGTH));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarLong(this.id);
        buf.writeUtf(this.name, RespawnEntry.MAX_NAME_LENGTH);
    }

    @Override
    public CustomPacketPayload.Type<RenameRespawnPayload> type() {
        return TYPE;
    }
}
