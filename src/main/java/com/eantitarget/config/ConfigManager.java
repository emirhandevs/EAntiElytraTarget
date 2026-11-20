package com.eantitarget.config;

import com.eantitarget.EAntiTargetPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private final EAntiTargetPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration checks;

    private String prefix;
    private int decayTimeSeconds;
    private boolean checkOnlyGliding;
    private boolean consoleLog;
    private boolean alerts;
    private boolean kickAnimationEnabled;

    private boolean autoFireworkEnabled;
    private long autoFireworkMaxMs;
    private int autoFireworkThreshold;
    private String autoFireworkPunishment;

    private boolean rotationConsistencyEnabled;
    private int rotationConsistencySampleSize;
    private double rotationConsistencyMaxStdDev;
    private double rotationConsistencyMinMeanDelta;
    private int rotationConsistencyThreshold;
    private String rotationConsistencyPunishment;

    private boolean centerMassLockEnabled;
    private double centerMassLockMaxAngle;
    private int centerMassLockConsecutiveHits;
    private int centerMassLockThreshold;
    private String centerMassLockPunishment;

    private boolean snapToTargetEnabled;
    private int snapToTargetWatchTicks;
    private double snapToTargetMinSnapAngle;
    private double snapToTargetMaxLockAngle;
    private double snapToTargetMaxDistance;
    private int snapToTargetThreshold;
    private String snapToTargetPunishment;

    private boolean noLookHitEnabled;
    private double noLookHitMaxAngle;
    private int noLookHitThreshold;
    private String noLookHitPunishment;

    public ConfigManager(EAntiTargetPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        messages = loadYaml("messages.yml");
        checks = loadYaml("checks.yml");

        prefix = config.getString("prefix", "&8» ");
        decayTimeSeconds = config.getInt("decay-time-seconds", 30);
        checkOnlyGliding = config.getBoolean("check-only-gliding", true);
        consoleLog = config.getBoolean("console-log", true);
        alerts = config.getBoolean("alerts", true);
        kickAnimationEnabled = config.getBoolean("kick-animation.enabled", true);

        autoFireworkEnabled = checks.getBoolean("auto-firework.enabled", true);
        autoFireworkMaxMs = checks.getLong("auto-firework.max-ms-threshold", 80L);
        autoFireworkThreshold = checks.getInt("auto-firework.violation-threshold", 5);
        autoFireworkPunishment = checks.getString("auto-firework.punishment", "KICK");

        rotationConsistencyEnabled = checks.getBoolean("rotation-consistency.enabled", true);
        rotationConsistencySampleSize = checks.getInt("rotation-consistency.sample-size", 10);
        rotationConsistencyMaxStdDev = checks.getDouble("rotation-consistency.max-std-dev-threshold", 0.005);
        rotationConsistencyMinMeanDelta = checks.getDouble("rotation-consistency.min-mean-delta", 0.1);
        rotationConsistencyThreshold = checks.getInt("rotation-consistency.violation-threshold", 3);
        rotationConsistencyPunishment = checks.getString("rotation-consistency.punishment", "KICK");

        centerMassLockEnabled = checks.getBoolean("center-mass-lock.enabled", true);
        centerMassLockMaxAngle = checks.getDouble("center-mass-lock.max-angle-deviation", 0.005);
        centerMassLockConsecutiveHits = checks.getInt("center-mass-lock.consecutive-hits-threshold", 5);
        centerMassLockThreshold = checks.getInt("center-mass-lock.violation-threshold", 2);
        centerMassLockPunishment = checks.getString("center-mass-lock.punishment", "KICK");

        snapToTargetEnabled = checks.getBoolean("snap-to-target.enabled", true);
        snapToTargetWatchTicks = checks.getInt("snap-to-target.watch-ticks", 2);
        snapToTargetMinSnapAngle = checks.getDouble("snap-to-target.min-snap-angle", 30.0);
        snapToTargetMaxLockAngle = checks.getDouble("snap-to-target.max-lock-angle", 1.5);
        snapToTargetMaxDistance = checks.getDouble("snap-to-target.max-target-distance", 50.0);
        snapToTargetThreshold = checks.getInt("snap-to-target.violation-threshold", 2);
        snapToTargetPunishment = checks.getString("snap-to-target.punishment", "KICK");

        noLookHitEnabled = checks.getBoolean("no-look-hit.enabled", true);
        noLookHitMaxAngle = checks.getDouble("no-look-hit.max-allowed-angle", 75.0);
        noLookHitThreshold = checks.getInt("no-look-hit.violation-threshold", 7);
        noLookHitPunishment = checks.getString("no-look-hit.punishment", "KICK");
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        InputStream def = plugin.getResource(name);
        if (def != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
        return yaml;
    }

    public String msg(String key) {
        String raw = messages.getString(key, key);
        return raw.replace("%prefix%", prefix);
    }

    public String getPunishmentForCheck(String checkName) {
        return switch (checkName) {
            case "AutoFirework" -> autoFireworkPunishment;
            case "RotationConsistency" -> rotationConsistencyPunishment;
            case "CenterMassLock" -> centerMassLockPunishment;
            case "SnapToTarget" -> snapToTargetPunishment;
            case "NoLookHit" -> noLookHitPunishment;
            default -> "KICK";
        };
    }

    public String getPrefix() { return prefix; }
    public int getDecayTimeSeconds() { return decayTimeSeconds; }
    public long getDecayTimeMs() { return decayTimeSeconds * 1000L; }
    public boolean isCheckOnlyGliding() { return checkOnlyGliding; }
    public boolean isConsoleLogEnabled() { return consoleLog; }
    public boolean isAlertEnabled() { return alerts; }
    public boolean isKickAnimationEnabled() { return kickAnimationEnabled; }
    public boolean isAutoFireworkEnabled() { return autoFireworkEnabled; }
    public long getAutoFireworkMaxMs() { return autoFireworkMaxMs; }
    public int getAutoFireworkThreshold() { return autoFireworkThreshold; }
    public boolean isRotationConsistencyEnabled() { return rotationConsistencyEnabled; }
    public int getRotationConsistencySampleSize() { return rotationConsistencySampleSize; }
    public double getRotationConsistencyMaxStdDev() { return rotationConsistencyMaxStdDev; }
    public double getRotationConsistencyMinMeanDelta() { return rotationConsistencyMinMeanDelta; }
    public int getRotationConsistencyThreshold() { return rotationConsistencyThreshold; }
    public boolean isCenterMassLockEnabled() { return centerMassLockEnabled; }
    public double getCenterMassLockMaxAngle() { return centerMassLockMaxAngle; }
    public int getCenterMassLockConsecutiveHits() { return centerMassLockConsecutiveHits; }
    public int getCenterMassLockThreshold() { return centerMassLockThreshold; }
    public boolean isSnapToTargetEnabled() { return snapToTargetEnabled; }
    public int getSnapToTargetWatchTicks() { return snapToTargetWatchTicks; }
    public double getSnapToTargetMinSnapAngle() { return snapToTargetMinSnapAngle; }
    public double getSnapToTargetMaxLockAngle() { return snapToTargetMaxLockAngle; }
    public double getSnapToTargetMaxDistance() { return snapToTargetMaxDistance; }
    public int getSnapToTargetThreshold() { return snapToTargetThreshold; }
    public boolean isNoLookHitEnabled() { return noLookHitEnabled; }
    public double getNoLookHitMaxAngle() { return noLookHitMaxAngle; }
    public int getNoLookHitThreshold() { return noLookHitThreshold; }
    public boolean isKickEnabled() { return true; }
    public String getKickReason() { return msg("kick-reason"); }
    public String getAlertMessage() { return msg("alert"); }
    public String getConsoleLogMessage() { return msg("console-log"); }
}
