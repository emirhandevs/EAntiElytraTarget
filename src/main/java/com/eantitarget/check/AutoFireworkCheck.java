package com.eantitarget.check;

import com.eantitarget.EAntiTargetPlugin;
import com.eantitarget.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AutoFirework:
 * - slot switch macro
 * - hizli fisek
 * - elinde/offhand yokken fisek kullanma (envanter/hotbar packet)
 */
public class AutoFireworkCheck implements Listener {

    private final EAntiTargetPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, PlayerData> dataMap = new ConcurrentHashMap<>();

    public AutoFireworkCheck(EAntiTargetPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        if (!config.isAutoFireworkEnabled()) return;
        Player player = event.getPlayer();
        if (config.isCheckOnlyGliding() && !player.isGliding()) {
            PlayerData d = dataMap.get(player.getUniqueId());
            if (d != null) d.resetSequence();
            return;
        }
        int prev = event.getPreviousSlot();
        int next = event.getNewSlot();
        long now = System.currentTimeMillis();
        PlayerData data = dataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerData());

        if (data.originalSlot != -1 && next == data.originalSlot && data.fireworkUsed) {
            long elapsed = now - data.switchTime;
            if (elapsed > 0 && elapsed <= config.getAutoFireworkMaxMs()) {
                flag(player, data, "slot-switch " + elapsed + "ms");
            }
            data.resetSequence();
            return;
        }
        if (prev != next) {
            data.originalSlot = prev;
            data.targetSlot = next;
            data.switchTime = now;
            data.fireworkUsed = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!config.isAutoFireworkEnabled()) return;
        Player player = event.getPlayer();
        if (config.isCheckOnlyGliding() && !player.isGliding()) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        long now = System.currentTimeMillis();
        PlayerData data = dataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerData());

        boolean mainFw = isFw(player.getInventory().getItemInMainHand());
        boolean offFw = isFw(player.getInventory().getItemInOffHand());
        boolean eventFw = item != null && item.getType() == Material.FIREWORK_ROCKET;

        // Elinde ve offhandde fisek YOKKEN fisek kullanma = envanterden/hotbardan packet
        if (player.isGliding() && eventFw && !mainFw && !offFw) {
            data.ghostStreak++;
            if (data.ghostStreak >= 2) {
                flag(player, data, "inventory-rocket (elinde yok)");
                data.ghostStreak = 0;
            }
            pushTiming(player, data, now);
            return;
        }

        // Hotbarda baska item varken (kilic vb.) fisek event'i geliyorsa
        if (player.isGliding() && eventFw) {
            ItemStack main = player.getInventory().getItemInMainHand();
            if (main != null && main.getType() != Material.AIR && main.getType() != Material.FIREWORK_ROCKET
                    && !offFw && event.getHand() == EquipmentSlot.HAND) {
                // event item fisek ama held degil - tutarsizlik
                data.ghostStreak++;
                if (data.ghostStreak >= 2) {
                    flag(player, data, "ghost-hand rocket while holding " + main.getType().name());
                    data.ghostStreak = 0;
                }
            }
        }

        if (!eventFw) return;

        EquipmentSlot hand = event.getHand() != null ? event.getHand() : EquipmentSlot.HAND;
        if (hand == EquipmentSlot.HAND) {
            int slot = player.getInventory().getHeldItemSlot();
            if (data.originalSlot != -1 && slot == data.targetSlot) {
                data.fireworkUsed = true;
            }
        } else {
            // offhand fisek - hizli kullanim takibi
        }
        data.ghostStreak = Math.max(0, data.ghostStreak - 1);
        pushTiming(player, data, now);
    }

    /** Disaridan (elytra boost) cagrilabilir */
    public void onBoost(Player player) {
        if (!config.isAutoFireworkEnabled()) return;
        if (config.isCheckOnlyGliding() && !player.isGliding()) return;
        PlayerData data = dataMap.computeIfAbsent(player.getUniqueId(), k -> new PlayerData());
        long now = System.currentTimeMillis();
        boolean mainFw = isFw(player.getInventory().getItemInMainHand());
        boolean offFw = isFw(player.getInventory().getItemInOffHand());
        if (!mainFw && !offFw) {
            flag(player, data, "elytra-boost without rocket in hand");
        }
        if (data.lastBoost > 0) {
            long gap = now - data.lastBoost;
            if (gap > 0 && gap < Math.max(50L, config.getAutoFireworkMaxMs())) {
                data.fastStreak++;
                if (data.fastStreak >= 2) {
                    flag(player, data, "boost-gap " + gap + "ms");
                    data.fastStreak = 0;
                }
            }
        }
        data.lastBoost = now;
        pushTiming(player, data, now);
    }

    private void pushTiming(Player player, PlayerData data, long now) {
        data.times.addLast(now);
        while (data.times.size() > 10) data.times.removeFirst();
        while (!data.times.isEmpty() && now - data.times.peekFirst() > 1000L) data.times.removeFirst();
        if (data.times.size() >= 5) {
            long span = data.times.peekLast() - data.times.peekFirst();
            if (span > 0 && span <= 850L) {
                flag(player, data, "rapid-rocket x" + data.times.size() + " " + span + "ms");
                data.times.clear();
            }
        }
        if (data.lastRocket > 0) {
            long gap = now - data.lastRocket;
            try {
                plugin.getBehaviorManager().sampleRocketGap(player.getUniqueId(), gap);
            } catch (Throwable ignored) {}
            if (gap > 0 && gap < Math.max(40L, config.getAutoFireworkMaxMs()) && player.isGliding()) {
                data.fastStreak++;
                if (data.fastStreak >= 3) {
                    flag(player, data, "rocket-gap " + gap + "ms");
                    data.fastStreak = 0;
                }
            } else if (gap >= config.getAutoFireworkMaxMs() * 2L) {
                data.fastStreak = 0;
            }
        }
        data.lastRocket = now;
    }

    private static boolean isFw(ItemStack s) {
        return s != null && s.getType() == Material.FIREWORK_ROCKET;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dataMap.remove(e.getPlayer().getUniqueId());
    }

    private void flag(Player player, PlayerData data, String info) {
        long now = System.currentTimeMillis();
        if (data.lastFlag > 0 && now - data.lastFlag < 180L) return;
        long decayMs = Math.max(1000L, config.getDecayTimeMs());
        if (data.lastFlag > 0) {
            int decay = (int) ((now - data.lastFlag) / decayMs);
            if (decay > 0) data.vl = Math.max(0, data.vl - decay);
        }
        data.vl++;
        data.lastFlag = now;
        try {
            double score = plugin.getBehaviorManager().anomalyScore(player.getUniqueId());
            if (score >= 2.2) {
                data.vl++;
                info = info + " | behavior=" + String.format("%.2f", score);
            }
        } catch (Throwable ignored) {}
        plugin.reportViolation(player, "AutoFirework", info, data.vl, Math.max(1, config.getAutoFireworkThreshold()));
        data.resetSequence();
    }

    public void cleanup() { dataMap.clear(); }

    private static class PlayerData {
        int originalSlot = -1, targetSlot = -1, vl = 0, fastStreak = 0, ghostStreak = 0;
        long switchTime = -1, lastFlag = -1, lastRocket = -1, lastBoost = -1;
        boolean fireworkUsed = false;
        final Deque<Long> times = new ArrayDeque<>();
        void resetSequence() {
            originalSlot = -1;
            targetSlot = -1;
            switchTime = -1;
            fireworkUsed = false;
        }
    }
}
