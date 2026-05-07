package pick.your.respawn;

import net.minecraft.network.chat.Component;

public enum RespawnEntryType {
    BED("bed", "Bed"),
    RESPAWN_ANCHOR("anchor", "Respawn Anchor");

    private final String serializedName;
    private final String displayName;

    RespawnEntryType(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public Component displayName() {
        return Component.literal(this.displayName);
    }

    public boolean isOtherRespawn() {
        return this != BED;
    }

    public static RespawnEntryType byName(String name) {
        for (RespawnEntryType type : values()) {
            if (type.serializedName.equals(name) || type.name().equals(name)) {
                return type;
            }
        }

        return BED;
    }
}
