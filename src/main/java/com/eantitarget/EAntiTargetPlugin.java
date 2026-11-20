package com.eantitarget;

import com.eantitarget.animation.FreezeListener;
import com.eantitarget.log.LogManager;
import com.eantitarget.behavior.BehaviorManager;
import com.eantitarget.log.LogsGui;
import com.eantitarget.animation.KickAnimationManager;
import com.eantitarget.check.AutoFireworkCheck;
import com.eantitarget.check.ElytraTargetCheck;
import com.eantitarget.command.AntiTargetCommand;
import com.eantitarget.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class EAntiTargetPlugin extends JavaPlugin {

    private static EAntiTargetPlugin instance;
    private ConfigManager configManager;
    private AutoFireworkCheck autoFireworkCheck;
    private ElytraTargetCheck elytraTargetCheck;
    private KickAnimationManager kickAnimationManager;
    private LogManager logManager;
    private BehaviorManager behaviorManager;
    private final java.util.Set<java.util.UUID> alertsDisabled = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private LogsGui logsGui;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("checks.yml", false);

        configManager = new ConfigManager(this);
        configManager.loadConfig();

        kickAnimationManager = new KickAnimationManager(this);
        logManager = new LogManager(this);
        behaviorManager = new BehaviorManager(this);
        registerElytraBoostHook();
        logsGui = new LogsGui(this);
        getServer().getPluginManager().registerEvents(logsGui, this);

        autoFireworkCheck = new AutoFireworkCheck(this);
        elytraTargetCheck = new ElytraTargetCheck(this);
        getServer().getPluginManager().registerEvents(autoFireworkCheck, this);
        getServer().getPluginManager().registerEvents(elytraTargetCheck, this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);

        if (getCommand("eantitarget") != null) {
            AntiTargetCommand cmd = new AntiTargetCommand(this);
            getCommand("eantitarget").setExecutor(cmd);
            getCommand("eantitarget").setTabCompleter(cmd);
        }
        getLogger().info("eAntiTarget aktif — kick animasyonu hazir.");
    }

    @Override
    public void onDisable() {
        if (kickAnimationManager != null) kickAnimationManager.cleanupAll();
        if (autoFireworkCheck != null) autoFireworkCheck.cleanup();
        if (elytraTargetCheck != null) elytraTargetCheck.cleanup();
    }

    public void reportViolation(Player player, String checkName, String info, int violations, int threshold) {
        if (kickAnimationManager != null && kickAnimationManager.isAnimating(player.getUniqueId())) return;

        // Log her zaman (OP dahil)
        if (configManager.isConsoleLogEnabled()) {
            String msg = configManager.msg("console-log")
                    .replace("%player%", player.getName())
                    .replace("%check%", checkName)
                    .replace("%info%", info)
                    .replace("%vl%", String.valueOf(violations))
                    .replace("%threshold%", String.valueOf(threshold));
            getLogger().warning(strip(msg));
            try {
                if (logManager != null) {
                    logManager.add(player.getUniqueId(), player.getName(), checkName, info, violations, threshold);
                }
            } catch (Throwable ignored) {}
        }

        // Staff + oyuncunun kendisi logu gorsun
        if (configManager.isAlertEnabled()) {
            String msgStr = configManager.msg("alert")
                    .replace("%player%", player.getName())
                    .replace("%check%", checkName)
                    .replace("%info%", info)
                    .replace("%vl%", String.valueOf(violations))
                    .replace("%threshold%", String.valueOf(threshold));
            Component alert = LegacyComponentSerializer.legacyAmpersand().deserialize(msgStr);
            for (Player online : getServer().getOnlinePlayers()) {
                if (!wantsAlerts(online)) continue;
                if (online.equals(player) || online.hasPermission("eantitarget.alerts") || online.isOp()) {
                    online.sendMessage(alert);
                }
            }
        }

        // Esik altindaysa ceza yok
        if (violations < threshold) return;

        // Bypass: OP varsayilan olarak CEZA ALIR (punish-ops: true).
        // Sadece non-op + eantitarget.bypass izinlisi atlar.
        if (hasRealBypass(player)) {
            getLogger().info("[eAntiTarget] " + player.getName() + " bypass (no punishment) | " + checkName + " VL " + violations + "/" + threshold);
            return;
        }

        String punishment = configManager.getPunishmentForCheck(checkName);
        if (punishment == null || punishment.isEmpty()) punishment = "KICK";
        if (punishment.equalsIgnoreCase("NONE")) return;

        getLogger().info("[eAntiTarget] Punishment starting: " + player.getName() + " | " + checkName
                + " VL " + violations + "/" + threshold + " -> " + punishment);

        try {
            if (configManager.isKickAnimationEnabled() && kickAnimationManager != null) {
                kickAnimationManager.start(player, checkName, info, punishment);
            } else {
                executePunishment(player, checkName, info, punishment);
            }
        } catch (Throwable ex) {
            getLogger().warning("Animation/punish error, direct kick: " + ex.getMessage());
            executePunishment(player, checkName, info, punishment);
        }
    }

    /** OP her zaman cezalanir (punish-ops true). Bypass sadece normal oyunculara. */
    private boolean hasRealBypass(Player player) {
        boolean punishOps = true;
        try {
            punishOps = getConfig().getBoolean("punish-ops", true);
        } catch (Throwable ignored) {}
        if (player.isOp() && punishOps) return false;
        return player.hasPermission("eantitarget.bypass");
    }

    public void executePunishment(Player player, String checkName, String info, String punishment) {
        if (player == null || !player.isOnline()) return;
        punishment = punishment == null ? "KICK" : punishment.toUpperCase();

        if (punishment.equals("BAN")) {
            String banReason = configManager.msg("ban-reason")
                    .replace("%player%", player.getName())
                    .replace("%check%", checkName)
                    .replace("%info%", info);
            Component banComp = LegacyComponentSerializer.legacyAmpersand().deserialize(banReason);
            String plain = strip(banReason);
            try {
                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), plain, null, "eAntiTarget");
            } catch (Throwable ignored) {}
            player.kick(banComp);
            broadcastStaff(configManager.msg("punished-ban")
                    .replace("%player%", player.getName())
                    .replace("%check%", checkName));
            return;
        }

        String kickReason = configManager.msg("kick-reason")
                .replace("%player%", player.getName())
                .replace("%check%", checkName)
                .replace("%info%", info);
        Component kickComp = LegacyComponentSerializer.legacyAmpersand().deserialize(kickReason);
        player.kick(kickComp);
        broadcastStaff(configManager.msg("punished-kick")
                .replace("%player%", player.getName())
                .replace("%check%", checkName));
    }

    private void broadcastStaff(String msg) {
        Component c = LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.hasPermission("eantitarget.alerts")) p.sendMessage(c);
        }
    }

    private String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orx]", "").replaceAll("&[0-9a-fk-orx]", "");
    }

    public static EAntiTargetPlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public AutoFireworkCheck getAutoFireworkCheck() { return autoFireworkCheck; }
    public ElytraTargetCheck getElytraTargetCheck() { return elytraTargetCheck; }
    public KickAnimationManager getKickAnimationManager() { return kickAnimationManager; }
    public LogManager getLogManager() { return logManager; }
    public BehaviorManager getBehaviorManager() { return behaviorManager; }
    public LogsGui getLogsGui() { return logsGui; }

    
    public boolean toggleAlerts(java.util.UUID id) {
        if (alertsDisabled.contains(id)) {
            alertsDisabled.remove(id);
            return true; // now on
        }
        alertsDisabled.add(id);
        return false; // now off
    }

    public boolean wantsAlerts(org.bukkit.entity.Player p) {
        if (p == null) return false;
        if (alertsDisabled.contains(p.getUniqueId())) return false;
        return true;
    }

private void registerElytraBoostHook() {
        try {
            Class<?> boostClass = Class.forName("com.destroystokyo.paper.event.player.PlayerElytraBoostEvent");
            getServer().getPluginManager().registerEvent(
                    (Class) boostClass,
                    autoFireworkCheck,
                    org.bukkit.event.EventPriority.MONITOR,
                    (listener, event) -> {
                        try {
                            Object player = event.getClass().getMethod("getPlayer").invoke(event);
                            if (player instanceof org.bukkit.entity.Player p) {
                                autoFireworkCheck.onBoost(p);
                            }
                        } catch (Throwable ignored) {}
                    },
                    this,
                    true
            );
            getLogger().info("PlayerElytraBoostEvent hooked.");
        } catch (Throwable t) {
            getLogger().info("PlayerElytraBoostEvent missing; using interact detection.");
        }
    }

}
