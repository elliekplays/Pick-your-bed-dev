package pick.your.network.payload;

import pick.your.Constants;
import pick.your.respawn.RespawnEntryView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenEditorPayload(RespawnEntryView entry) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenEditorPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "open_editor"));
    public static final StreamCodec<FriendlyByteBuf, OpenEditorPayload> STREAM_CODEC = CustomPacketPayload.codec(
        OpenEditorPayload::write,
        OpenEditorPayload::new
    );

    private OpenEditorPayload(FriendlyByteBuf buf) {
        this(RespawnEntryView.read(buf));
    }

    private void write(FriendlyByteBuf buf) {
        this.entry.write(buf);
    }

    @Override
    public CustomPacketPayload.Type<OpenEditorPayload> type() {
        return TYPE;
    }
}
