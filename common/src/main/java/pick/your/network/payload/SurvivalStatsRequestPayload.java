package pick.your.network.payload;

import pick.your.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SurvivalStatsRequestPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SurvivalStatsRequestPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "survival_stats_request"));
    public static final StreamCodec<FriendlyByteBuf, SurvivalStatsRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(
        SurvivalStatsRequestPayload::write,
        SurvivalStatsRequestPayload::new
    );

    private SurvivalStatsRequestPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public CustomPacketPayload.Type<SurvivalStatsRequestPayload> type() {
        return TYPE;
    }
}
