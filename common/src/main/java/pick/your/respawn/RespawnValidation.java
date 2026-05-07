package pick.your.respawn;

public record RespawnValidation(boolean valid, String reason) {
    public static RespawnValidation ok() {
        return new RespawnValidation(true, "");
    }

    public static RespawnValidation invalid(String reason) {
        return new RespawnValidation(false, reason);
    }
}
