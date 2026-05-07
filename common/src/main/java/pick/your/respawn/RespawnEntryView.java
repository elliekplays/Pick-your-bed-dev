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
    public static RespawnEntryView read(FriendlyByteBuf buf) {
        return new RespawnEntryView(
            buf.readVarLong(),
            buf.readEnum(RespawnEntryType.class),
            buf.readResourceLocation(),
            buf.readBlockPos(),
            buf.readUtf(32),
            buf.readBoolean(),
            buf.readUtf(80)
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarLong(this.id);
        buf.writeEnum(this.type);
        buf.writeResourceLocation(this.dimension);
        buf.writeBlockPos(this.pos);
        buf.writeUtf(this.name, 32);
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
