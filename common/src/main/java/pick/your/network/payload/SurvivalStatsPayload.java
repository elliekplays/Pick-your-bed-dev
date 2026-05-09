package pick.your.network.payload;

import pick.your.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SurvivalStatsPayload(
    boolean useServerStats,
    long playTicks,
    long blocksPlaced,
    long blocksBroken,
    long distanceCm
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SurvivalStatsPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "survival_stats"));
    public static final StreamCodec<FriendlyByteBuf, SurvivalStatsPayload> STREAM_CODEC = CustomPacketPayload.codec(
        SurvivalStatsPayload::write,
        SurvivalStatsPayload::new
    );

    private SurvivalStatsPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong());
    }

    public SurvivalStatsPayload {
        playTicks = Math.max(0L, playTicks);
        blocksPlaced = Math.max(0L, blocksPlaced);
        blocksBroken = Math.max(0L, blocksBroken);
        distanceCm = Math.max(0L, distanceCm);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.useServerStats);
        buf.writeVarLong(this.playTicks);
        buf.writeVarLong(this.blocksPlaced);
        buf.writeVarLong(this.blocksBroken);
        buf.writeVarLong(this.distanceCm);
    }

    @Override
    public CustomPacketPayload.Type<SurvivalStatsPayload> type() {
        return TYPE;
    }
}
