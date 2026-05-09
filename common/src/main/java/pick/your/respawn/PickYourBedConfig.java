package pick.your.respawn;

import pick.your.Constants;
import pick.your.platform.Services;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class PickYourBedConfig {
    private static final String FILE_NAME = Constants.MOD_ID + "-server.properties";
    private static final String KEY_LIMIT_ENABLED = "respawn_point_limit_enabled";
    private static final String KEY_MAX_RESPAWN_POINTS = "max_respawn_points_per_player";
    private static final boolean DEFAULT_LIMIT_ENABLED = false;
    private static final int DEFAULT_MAX_RESPAWN_POINTS = 5;

    private static Settings settings = new Settings(DEFAULT_LIMIT_ENABLED, DEFAULT_MAX_RESPAWN_POINTS);
    private static boolean loaded;

    private PickYourBedConfig() {
    }

    public static synchronized Settings get() {
        if (!loaded) {
            load();
        }
        return settings;
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    private static void load() {
        Path path = configPath();
        Properties properties = new Properties();
        boolean changed = false;

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException exception) {
                Constants.LOG.error("Failed to read Pick your bed config at {}", path, exception);
            }
        } else {
            changed = true;
        }

        boolean limitEnabled = DEFAULT_LIMIT_ENABLED;
        String limitEnabledValue = properties.getProperty(KEY_LIMIT_ENABLED);
        if (limitEnabledValue == null) {
            changed = true;
        } else if ("true".equalsIgnoreCase(limitEnabledValue) || "false".equalsIgnoreCase(limitEnabledValue)) {
            limitEnabled = Boolean.parseBoolean(limitEnabledValue);
        } else {
            Constants.LOG.warn("Invalid {} value '{}' in {}; using {}", KEY_LIMIT_ENABLED, limitEnabledValue, FILE_NAME, DEFAULT_LIMIT_ENABLED);
            changed = true;
        }

        int maxRespawnPoints = DEFAULT_MAX_RESPAWN_POINTS;
        String maxRespawnPointsValue = properties.getProperty(KEY_MAX_RESPAWN_POINTS);
        if (maxRespawnPointsValue == null) {
            changed = true;
        } else {
            try {
                maxRespawnPoints = Math.max(1, Integer.parseInt(maxRespawnPointsValue.trim()));
                if (!Integer.toString(maxRespawnPoints).equals(maxRespawnPointsValue.trim())) {
                    changed = true;
                }
            } catch (NumberFormatException exception) {
                Constants.LOG.warn("Invalid {} value '{}' in {}; using {}", KEY_MAX_RESPAWN_POINTS, maxRespawnPointsValue, FILE_NAME, DEFAULT_MAX_RESPAWN_POINTS);
                changed = true;
            }
        }

        settings = new Settings(limitEnabled, maxRespawnPoints);
        loaded = true;

        if (changed) {
            write(path, settings);
        }
    }

    private static Path configPath() {
        return Services.PLATFORM.getConfigDirectory().resolve(FILE_NAME);
    }

    private static void write(Path path, Settings settings) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, """
                # Pick your bed server config
                # When disabled, players can have unlimited saved respawn points.
                # When enabled, new beds and respawn anchors stop registering after the per-player cap is reached.
                respawn_point_limit_enabled=%s
                max_respawn_points_per_player=%d
                """.formatted(settings.respawnPointLimitEnabled(), settings.maxRespawnPointsPerPlayer()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            Constants.LOG.error("Failed to write Pick your bed config at {}", path, exception);
        }
    }

    public record Settings(boolean respawnPointLimitEnabled, int maxRespawnPointsPerPlayer) {
        public Settings {
            maxRespawnPointsPerPlayer = Math.max(1, maxRespawnPointsPerPlayer);
        }

        public boolean limitsRespawnPoints() {
            return this.respawnPointLimitEnabled;
        }
    }
}
