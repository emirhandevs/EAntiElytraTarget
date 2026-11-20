package com.eantitarget.behavior;

import com.eantitarget.EAntiTargetPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oyuncu fişek aralığı verisini kaydeder, global normal ortalamayla karşılaştırır.
 */
public class BehaviorManager {

    private final EAntiTargetPlugin plugin;
    private final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();
    private double globalMeanGap = 180.0;
    private double globalStdGap = 60.0;
    private long globalSamples = 0;
    private final File file;

    public BehaviorManager(EAntiTargetPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "behavior.yml");
        load();
    }

    public void sampleRocketGap(UUID id, long gapMs) {
        if (gapMs <= 0 || gapMs > 5000) return;
        PlayerStats s = stats.computeIfAbsent(id, k -> new PlayerStats());
        s.addGap(gapMs);
        // global
        globalSamples++;
        double n = globalSamples;
        double delta = gapMs - globalMeanGap;
        globalMeanGap += delta / n;
        globalStdGap = Math.sqrt(((n - 1) * globalStdGap * globalStdGap + delta * (gapMs - globalMeanGap)) / Math.max(1, n));
        if (globalSamples % 50 == 0) saveAsync();
    }

    public void sampleRotation(UUID id, double stdDev) {
        if (stdDev < 0 || stdDev > 10) return;
        PlayerStats s = stats.computeIfAbsent(id, k -> new PlayerStats());
        s.addRot(stdDev);
    }

    /**
     * @return anomali skoru (0 = normal, 2+ supheli, 3+ cok yuksek)
     */
    public double anomalyScore(UUID id) {
        if (!plugin.getConfig().getBoolean("behavior.enabled", true)) return 0;
        int min = plugin.getConfig().getInt("behavior.min-samples", 20);
        PlayerStats s = stats.get(id);
        if (s == null || s.gapSamples < Math.min(min, 8)) return 0;
        double mean = s.meanGap();
        // Cok dusuk gap (macro) global ortalamaya gore
        if (globalSamples < 10) {
            return mean < 70 ? 2.5 : 0;
        }
        double z = (globalMeanGap - mean) / Math.max(15.0, globalStdGap);
        // mean cok kucukse z pozitif buyuk
        return Math.max(0, z);
    }

    public void saveAsync() {
        Map<UUID, PlayerStats> snap = new HashMap<>(stats);
        double gMean = globalMeanGap, gStd = globalStdGap;
        long gN = globalSamples;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            FileConfiguration y = new YamlConfiguration();
            y.set("global.mean-gap", gMean);
            y.set("global.std-gap", gStd);
            y.set("global.samples", gN);
            for (Map.Entry<UUID, PlayerStats> e : snap.entrySet()) {
                String path = "players." + e.getKey();
                y.set(path + ".mean-gap", e.getValue().meanGap());
                y.set(path + ".gap-samples", e.getValue().gapSamples);
                y.set(path + ".mean-rot", e.getValue().meanRot());
                y.set(path + ".rot-samples", e.getValue().rotSamples);
            }
            try { y.save(file); } catch (IOException ignored) {}
        });
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration y = YamlConfiguration.loadConfiguration(file);
        globalMeanGap = y.getDouble("global.mean-gap", 180);
        globalStdGap = y.getDouble("global.std-gap", 60);
        globalSamples = y.getLong("global.samples", 0);
        if (y.isConfigurationSection("players")) {
            for (String key : y.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    PlayerStats s = new PlayerStats();
                    s.gapSamples = y.getInt("players." + key + ".gap-samples", 0);
                    s.gapSum = y.getDouble("players." + key + ".mean-gap", 180) * Math.max(1, s.gapSamples);
                    s.rotSamples = y.getInt("players." + key + ".rot-samples", 0);
                    s.rotSum = y.getDouble("players." + key + ".mean-rot", 0.05) * Math.max(1, s.rotSamples);
                    stats.put(id, s);
                } catch (Exception ignored) {}
            }
        }
    }

    private static class PlayerStats {
        long gapSamples, rotSamples;
        double gapSum, rotSum;
        void addGap(long gap) { gapSamples++; gapSum += gap; }
        void addRot(double v) { rotSamples++; rotSum += v; }
        double meanGap() { return gapSamples == 0 ? 180 : gapSum / gapSamples; }
        double meanRot() { return rotSamples == 0 ? 0.05 : rotSum / rotSamples; }
    }
}
