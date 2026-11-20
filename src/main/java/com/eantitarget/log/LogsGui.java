package com.eantitarget.log;

import com.eantitarget.EAntiTargetPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LogsGui implements Listener {

    public static final int PAGE_SIZE = 28;
    public static final int MAX_PAGE = 20;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.of("Europe/Istanbul"));
    private static final int[] CONTENT = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    private final EAntiTargetPlugin plugin;
    public LogsGui(EAntiTargetPlugin plugin) { this.plugin = plugin; }

    public void open(Player player, int page) {
        page = Math.max(1, Math.min(MAX_PAGE, page));
        List<LogEntry> all = plugin.getLogManager().all();
        int totalPages = Math.max(1, Math.min(MAX_PAGE, (int) Math.ceil(Math.max(1, all.size()) / (double) PAGE_SIZE)));
        if (all.isEmpty()) totalPages = 1;
        if (page > totalPages) page = totalPages;

        Holder holder = new Holder(page);
        Inventory inv = Bukkit.createInventory(holder, 54, color("&8Logs &7" + page + "/" + totalPages));
        holder.inv = inv;

        ItemStack glass = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack accent = pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int s : new int[]{1,7,9,17,18,26,27,35,36,44,46,52}) inv.setItem(s, glass);
        for (int s : new int[]{0,8,45,53}) inv.setItem(s, accent);

        int start = (page - 1) * PAGE_SIZE;
        for (int i = 0; i < CONTENT.length; i++) {
            int idx = start + i;
            if (idx >= all.size()) break;
            inv.setItem(CONTENT[i], headItem(all.get(idx)));
        }

        inv.setItem(4, btn(Material.BOOK, "&bTotal Logs", List.of("&f" + all.size() + " entries")));
        inv.setItem(48, btn(Material.ARROW, "&aPrevious", List.of("&7Sayfa " + page)));
        inv.setItem(49, btn(Material.BARRIER, "&cClose", List.of("&7Close")));
        inv.setItem(50, btn(Material.ARROW, "&aNext", List.of("&7Sayfa " + page)));
        player.openInventory(inv);
    }

    private ItemStack headItem(LogEntry e) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            try { meta.setOwningPlayer(Bukkit.getOfflinePlayer(e.uuid)); } catch (Throwable ignored) {}
            boolean online = Bukkit.getPlayer(e.uuid) != null;
            meta.displayName(color("&e" + e.name + (online ? " &aOnline" : " &7Offline")));
            List<Component> lore = new ArrayList<>();
            lore.add(color("&7Check: &f" + e.check));
            lore.add(color("&7Info: &f" + e.info));
            lore.add(color("&7VL: &f" + e.vl + "/" + e.threshold));
            lore.add(color("&7Date: &f" + FMT.format(Instant.ofEpochMilli(e.time))));
            lore.add(color(" "));
            lore.add(color("&cLeft-click to delete"));
            lore.add(color("&8id:" + e.id));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pane(Material mat, String name) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        if (m != null) { m.displayName(color(name)); i.setItemMeta(m); }
        return i;
    }

    private ItemStack btn(Material mat, String name, List<String> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        if (m != null) {
            m.displayName(color(name));
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(color(s));
            m.lore(l);
            i.setItemMeta(m);
        }
        return i;
    }

    private Component color(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s == null ? "" : s);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission("eantitarget.admin")) return;
        int slot = e.getRawSlot();
        if (slot == 48) { open(p, holder.page - 1); return; }
        if (slot == 50) { open(p, holder.page + 1); return; }
        if (slot == 49) { p.closeInventory(); return; }
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType() != Material.PLAYER_HEAD || !cur.hasItemMeta()) return;
        ItemMeta meta = cur.getItemMeta();
        if (meta == null || meta.lore() == null) return;
        String id = null;
        for (Component c : meta.lore()) {
            String s = PlainTextComponentSerializer.plainText().serialize(c);
            if (s.startsWith("id:")) id = s.substring(3).trim();
        }
        if (id != null && plugin.getLogManager().remove(id)) {
            p.sendMessage(color(plugin.getConfigManager().msg("log-deleted")));
            open(p, holder.page);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    public static class Holder implements InventoryHolder {
        public final int page;
        public Inventory inv;
        public Holder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inv; }
    }
}
