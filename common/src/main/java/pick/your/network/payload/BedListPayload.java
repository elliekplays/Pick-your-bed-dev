package pick.your.network.payload;

import pick.your.Constants;
import pick.your.respawn.RespawnEntryView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record BedListPayload(List<RespawnEntryView> entries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BedListPayload> TYPE = CustomPacketPayload.createType(Constants.MOD_ID + ":list");
    public static final StreamCodec<FriendlyByteBuf, BedListPayload> STREAM_CODEC = CustomPacketPayload.codec(
        BedListPayload::write,
        BedListPayload::new
    );

    private BedListPayload(FriendlyByteBuf buf) {
        this(readEntries(buf));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entries.size());
        for (RespawnEntryView entry : this.entries) {
            entry.write(buf);
        }
    }

    private static List<RespawnEntryView> readEntries(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<RespawnEntryView> entries = new ArrayList<>(Math.min(size, 128));
        for (int i = 0; i < size; i++) {
            entries.add(RespawnEntryView.read(buf));
        }
        return List.copyOf(entries);
    }

    @Override
    public CustomPacketPayload.Type<BedListPayload> type() {
        return TYPE;
    }
}
