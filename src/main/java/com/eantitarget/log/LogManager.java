package com.eantitarget.log;

import com.eantitarget.EAntiTargetPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogManager {

    private final EAntiTargetPlugin plugin;
    private final List<LogEntry> entries = new CopyOnWriteArrayList<>();
    private final File file;
    private static final int MAX = 900; // 20 sayfa * 45

    public LogManager(EAntiTargetPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "logs.yml");
        load();
    }

    public void add(UUID uuid, String name, String check, String info, int vl, int threshold) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        entries.add(0, new LogEntry(id, uuid, name, check, info, vl, threshold, System.currentTimeMillis()));
        while (entries.size() > MAX) entries.remove(entries.size() - 1);
        saveAsync();
    }

    public boolean remove(String id) {
        boolean ok = entries.removeIf(e -> e.id.equals(id));
        if (ok) saveAsync();
        return ok;
    }

    public List<LogEntry> all() {
        return Collections.unmodifiableList(entries);
    }

    public int size() { return entries.size(); }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration y = YamlConfiguration.loadConfiguration(file);
        List<?> list = y.getList("entries");
        if (list == null) return;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            try {
                String id = String.valueOf(m.get("id"));
                UUID uuid = UUID.fromString(String.valueOf(m.get("uuid")));
                String name = String.valueOf(m.get("name"));
                String check = String.valueOf(m.get("check"));
                String info = String.valueOf(m.get("info"));
                int vl = Integer.parseInt(String.valueOf(m.get("vl")));
                int th = Integer.parseInt(String.valueOf(m.get("threshold")));
                long time = Long.parseLong(String.valueOf(m.get("time")));
                entries.add(new LogEntry(id, uuid, name, check, info, vl, th, time));
            } catch (Exception ignored) {}
        }
    }

    private void saveAsync() {
        List<LogEntry> snapshot = new ArrayList<>(entries);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            FileConfiguration y = new YamlConfiguration();
            List<Map<String, Object>> list = new ArrayList<>();
            for (LogEntry e : snapshot) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.id);
                m.put("uuid", e.uuid.toString());
                m.put("name", e.name);
                m.put("check", e.check);
                m.put("info", e.info);
                m.put("vl", e.vl);
                m.put("threshold", e.threshold);
                m.put("time", e.time);
                list.add(m);
            }
            y.set("entries", list);
            try { y.save(file); } catch (IOException ignored) {}
        });
    }
}
