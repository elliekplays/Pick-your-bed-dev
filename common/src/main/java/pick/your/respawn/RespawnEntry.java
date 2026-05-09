package pick.your.respawn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class RespawnEntry {
    public static final int MAX_NAME_LENGTH = 20;

    private final long id;
    private final UUID owner;
    private final RespawnEntryType type;
    private final ResourceLocation dimension;
    private final BlockPos pos;
    private String name;

    public RespawnEntry(long id, UUID owner, RespawnEntryType type, ResourceLocation dimension, BlockPos pos, String name) {
        this.id = id;
        this.owner = owner;
        this.type = type;
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.name = sanitizeName(name, fallbackName(type));
    }

    public long id() {
        return this.id;
    }

    public UUID owner() {
        return this.owner;
    }

    public RespawnEntryType type() {
        return this.type;
    }

    public ResourceLocation dimension() {
        return this.dimension;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public String name() {
        return this.name;
    }

    public void rename(String name) {
        this.name = sanitizeName(name, fallbackName(this.type));
    }

    public boolean samePlace(RespawnEntryType type, ResourceLocation dimension, BlockPos pos) {
        return this.type == type && this.dimension.equals(dimension) && this.pos.equals(pos);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Id", this.id);
        tag.putUUID("Owner", this.owner);
        tag.putString("Type", this.type.serializedName());
        tag.putString("Dimension", this.dimension.toString());
        tag.putInt("X", this.pos.getX());
        tag.putInt("Y", this.pos.getY());
        tag.putInt("Z", this.pos.getZ());
        tag.putString("Name", this.name);
        return tag;
    }

    public static RespawnEntry load(CompoundTag tag) {
        UUID owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : new UUID(0L, 0L);
        RespawnEntryType type = RespawnEntryType.byName(tag.getString("Type"));
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) {
            dimension = Level.OVERWORLD.location();
        }
        BlockPos pos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        return new RespawnEntry(tag.getLong("Id"), owner, type, dimension, pos, tag.getString("Name"));
    }

    public static String fallbackName(RespawnEntryType type) {
        return type == RespawnEntryType.BED ? "Bed" : "Respawn Anchor";
    }

    public static String sanitizeName(String name, String fallback) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            trimmed = fallback;
        }

        StringBuilder clean = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!Character.isISOControl(c)) {
                clean.append(c);
            }
        }

        if (clean.isEmpty()) {
            clean.append(fallback);
        }
        if (clean.length() > MAX_NAME_LENGTH) {
            clean.setLength(MAX_NAME_LENGTH);
        }

        return clean.toString();
    }
}
