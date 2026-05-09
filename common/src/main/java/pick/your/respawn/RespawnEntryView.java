package pick.your.respawn;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record RespawnEntryView(
    long id,
    RespawnEntryType type,
    ResourceLocation dimension,
    BlockPos pos,
    String name,
    boolean valid,
    String invalidReason
) {
    public RespawnEntryView {
        name = RespawnEntry.sanitizeName(name, RespawnEntry.fallbackName(type));
        invalidReason = invalidReason == null ? "" : invalidReason;
    }

    public static RespawnEntryView read(FriendlyByteBuf buf) {
        return new RespawnEntryView(
            buf.readVarLong(),
            buf.readEnum(RespawnEntryType.class),
            buf.readResourceLocation(),
            buf.readBlockPos(),
            buf.readUtf(RespawnEntry.MAX_NAME_LENGTH),
            buf.readBoolean(),
            buf.readUtf(80)
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarLong(this.id);
        buf.writeEnum(this.type);
        buf.writeResourceLocation(this.dimension);
        buf.writeBlockPos(this.pos);
        buf.writeUtf(RespawnEntry.sanitizeName(this.name, RespawnEntry.fallbackName(this.type)), RespawnEntry.MAX_NAME_LENGTH);
        buf.writeBoolean(this.valid);
        buf.writeUtf(this.invalidReason, 80);
    }

    public String coordinateText() {
        return this.pos.getX() + ", " + this.pos.getY() + ", " + this.pos.getZ();
    }

    public String dimensionText() {
        String namespace = this.dimension.getNamespace();
        String path = this.dimension.getPath();
        return namespace.equals("minecraft") ? path : namespace + ":" + path;
    }
}
