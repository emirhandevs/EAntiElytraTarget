package com.eantitarget.animation;

import com.eantitarget.EAntiTargetPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KickAnimationManager {

    private final EAntiTargetPlugin plugin;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<Llama>> llamas = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final Set<UUID> animating = ConcurrentHashMap.newKeySet();

    public KickAnimationManager(EAntiTargetPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isFrozen(UUID id) {
        return frozen.contains(id);
    }

    public boolean isAnimating(UUID id) {
        return animating.contains(id);
    }

    public void start(Player player, String checkName, String info, String punishment) {
        UUID id = player.getUniqueId();
        if (animating.contains(id)) return;
        animating.add(id);
        frozen.add(id);

        // Hareket / elytra / ucus engeli
        player.setGliding(false);
        player.setVelocity(new Vector(0, 0, 0));
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setAllowFlight(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 100, 128, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false, false));

        Location center = player.getLocation().clone();

        // 6 lama daire
        List<Llama> list = new ArrayList<>();
        org.bukkit.Color color = org.bukkit.Color.fromRGB(0x00, 0xFF, 0x40);
        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2 / 6) * i;
            Location loc = center.clone().add(Math.cos(angle) * 2.2, 0, Math.sin(angle) * 2.2);
            loc.setY(center.getY());
            Llama llama = (Llama) player.getWorld().spawnEntity(loc, EntityType.LLAMA);
            llama.setAI(false);
            llama.setGravity(false);
            llama.setSilent(true);
            llama.setInvulnerable(true);
            llama.setCollidable(false);
            llama.setRemoveWhenFarAway(false);

            llama.setColor(Llama.Color.CREAMY);
            list.add(llama);
        }
        llamas.put(id, list);

        // Title 1 (0-2s)
        String t1 = plugin.getConfigManager().msg("animation-title-1");
        String s1 = plugin.getConfigManager().msg("animation-subtitle-1");
        sendTitle(player, t1, s1);

        // Title 2 at 2s
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            String t2 = plugin.getConfigManager().msg("animation-title-2");
            String s2 = plugin.getConfigManager().msg("animation-subtitle-2");
            sendTitle(player, t2, s2);
        }, 40L);

        // Dondur + lama dondur (4 sn = 80 tick)
        final double[] rot = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cleanup(id);
                return;
            }
            // oyuncuyu sabitle
            Location freeze = center.clone();
            freeze.setYaw(player.getLocation().getYaw());
            freeze.setPitch(player.getLocation().getPitch());
            player.teleport(freeze);
            player.setVelocity(new Vector(0, 0, 0));
            player.setGliding(false);

            rot[0] += 0.12; // yavas donus
            List<Llama> ls = llamas.get(id);
            if (ls != null) {
                for (int i = 0; i < ls.size(); i++) {
                    Llama llama = ls.get(i);
                    if (llama == null || llama.isDead()) continue;
                    double angle = rot[0] + (Math.PI * 2 / 6) * i;
                    Location loc = center.clone().add(Math.cos(angle) * 2.2, 0, Math.sin(angle) * 2.2);
                    loc.setY(center.getY());
                    // lamayi oyuncuya baktir
                    Vector dir = center.toVector().subtract(loc.toVector());
                    loc.setDirection(dir);
                    llama.teleport(loc);
                }
            }
        }, 1L, 1L);
        tasks.put(id, task);

        // 4 sn sonra tukruk + ceza
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<Llama> ls = llamas.get(id);
            if (ls != null && player.isOnline()) {
                for (Llama llama : ls) {
                    if (llama == null || llama.isDead()) continue;
                    // tukruk efekti
                    try {
                        player.getWorld().spawnParticle(org.bukkit.Particle.SPIT, llama.getEyeLocation(), 12, 0.25, 0.25, 0.25, 0.08);
                    } catch (Throwable t) {
                        // alternatif: particle
                        player.getWorld().spawnParticle(org.bukkit.Particle.SPIT, llama.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.05);
                    }
                    // oyuncuya bakip "saldiri"
                    llama.setTarget(player);
                }
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_LLAMA_SPIT, 1f, 1f);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                cleanup(id);
                if (player.isOnline()) {
                    plugin.executePunishment(player, checkName, info, punishment);
                }
            }, 10L);
        }, 80L);
    }

    private void sendTitle(Player player, String title, String subtitle) {
        Component t = LegacyComponentSerializer.legacyAmpersand().deserialize(title == null ? "" : title);
        Component s = LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle == null ? "" : subtitle);
        player.showTitle(Title.title(t, s, Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(200))));
    }

    public void cleanup(UUID id) {
        animating.remove(id);
        frozen.remove(id);
        BukkitTask task = tasks.remove(id);
        if (task != null) task.cancel();
        List<Llama> ls = llamas.remove(id);
        if (ls != null) {
            for (Llama l : ls) {
                if (l != null && !l.isDead()) l.remove();
            }
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline()) {
            p.setWalkSpeed(0.2f);
            p.setFlySpeed(0.1f);
            p.removePotionEffect(PotionEffectType.SLOW);
            p.removePotionEffect(PotionEffectType.JUMP);
        }
    }

    public void cleanupAll() {
        for (UUID id : new HashSet<>(animating)) cleanup(id);
    }
}
