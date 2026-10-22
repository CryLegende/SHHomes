package org.Cry_Legende.sHHomes.menu;

import org.Cry_Legende.sHHomes.Home;
import org.Cry_Legende.sHHomes.SHHomes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Menus des homes, décrits dans la section "menus" de la config (mêmes clés qu'EasyHome).
 * Chaque case cliquable garde son action : plus de comparaison de nom/matériau au clic.
 */
public abstract class Menu implements InventoryHolder {

    protected final SHHomes plugin;
    protected final Player viewer;
    private final String key;
    protected Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(SHHomes plugin, Player viewer, String key) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.key = key;
    }

    protected ConfigurationSection config() {
        return plugin.getConfig().getConfigurationSection("menus." + key);
    }

    protected String title() {
        return config().getString("name", "");
    }

    /**
     * Éléments remplis par fill() plutôt que par leurs "slots" de config.
     */
    protected Set<String> dynamic() {
        return Set.of();
    }

    protected abstract void fill();

    /**
     * Action d'un élément fixe de la config (changeName, delete, return...).
     */
    protected Consumer<InventoryClickEvent> button(String id) {
        return null;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, config().getInt("size", 6) * 9, SHHomes.color(title()));
        ConfigurationSection items = config().getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection it = items.getConfigurationSection(id);
                if (id.equals("filler") || dynamic().contains(id) || it == null || it.getString("slots") == null) {
                    continue;
                }
                ItemStack stack = item(it.getString("material"), it.getString("name"), it.getStringList("lore"), 1);
                for (int slot : slots(it.getString("slots"))) {
                    set(slot, stack, button(id));
                }
            }
        }
        fill();
        ConfigurationSection f = items == null ? null : items.getConfigurationSection("filler");
        ItemStack filler = f == null ? null : item(f.getString("material"), f.getString("name"), f.getStringList("lore"), 1);
        if (filler != null) {
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, filler);
                }
            }
        }
        viewer.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void click(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    protected void set(int slot, ItemStack stack, Consumer<InventoryClickEvent> action) {
        if (stack == null || slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, stack);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    protected void clickSound() {
        plugin.playSound(viewer, plugin.getConfig().getString("settings.clickSound"));
    }

    /**
     * "11-15,20-24,38" -> [11..15, 20..24, 38]
     */
    protected static List<Integer> slots(String spec) {
        List<Integer> out = new ArrayList<>();
        if (spec == null) {
            return out;
        }
        for (String part : spec.split(",")) {
            String[] range = part.trim().split("-");
            try {
                int from = Integer.parseInt(range[0].trim());
                int to = range.length > 1 ? Integer.parseInt(range[1].trim()) : from;
                for (int i = from; i <= to; i++) {
                    out.add(i);
                }
            } catch (NumberFormatException ignored) {
                // case mal écrite dans la config : ignorée
            }
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    protected static ItemStack item(String material, String name, List<String> lore, int amount) {
        Material type = material == null ? null : Material.matchMaterial(material);
        if (type == null || !type.isItem() || type.isAir()) {
            return null;
        }
        ItemStack stack = new ItemStack(type, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.setDisplayName(SHHomes.color(name));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(SHHomes::color).toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    protected static List<String> replace(List<String> lines, String... pairs) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                line = line.replace(pairs[i], pairs[i + 1]);
            }
            out.add(line);
        }
        return out;
    }

    protected ItemStack homeItem(Home home, List<String> lore, int amount) {
        ItemStack stack = item(home.getMaterial(), home.getDisplayName(), lore, amount);
        return stack != null ? stack : item("RED_BED", home.getDisplayName(), lore, amount);
    }

    /**
     * Cases vides et verrouillées de la grille des homes (communes aux deux menus de homes).
     */
    protected void emptyOrLocked(int slot, int index, int max, Consumer<InventoryClickEvent> onEmpty) {
        if (index < max) {
            set(slot, item("WHITE_BED", config().getString("items.homes.empty.name"),
                config().getStringList("items.homes.empty.lore"), index + 1), onEmpty);
        } else {
            set(slot, item(config().getString("items.homes.locked.material", "BARRIER"), config().getString("items.homes.locked.name"),
                config().getStringList("items.homes.locked.lore"), 1), null);
        }
    }

    // ---------------------------------------------------------------- Menu des homes du joueur

    public static final class Homes extends Menu {

        public Homes(SHHomes plugin, Player viewer) {
            super(plugin, viewer, "home");
        }

        @Override
        protected Set<String> dynamic() {
            return Set.of("homes");
        }

        @Override
        protected void fill() {
            List<Home> homes = plugin.store().get(viewer.getUniqueId());
            int max = plugin.maxHomes(viewer);
            List<Integer> slots = slots(config().getString("items.homes.slots"));
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.get(i);
                if (i < homes.size()) {
                    Home home = homes.get(i);
                    List<String> lore = replace(config().getStringList("items.homes.set.lore"), "%name%", home.getDisplayName(), "%id%", home.getId());
                    set(slot, homeItem(home, lore, i + 1), e -> {
                        if (e.getClick() == ClickType.LEFT) {
                            clickSound();
                            viewer.closeInventory();
                            plugin.teleport(viewer, home);
                        } else if (e.getClick() == ClickType.RIGHT) {
                            clickSound();
                            new Settings(plugin, viewer, home).open();
                        }
                    });
                } else {
                    emptyOrLocked(slot, i, max, e -> {
                        clickSound();
                        viewer.closeInventory();
                        plugin.createHome(viewer, null);
                    });
                }
            }
        }
    }

    // ---------------------------------------------------------------- Paramètres d'un home

    public static final class Settings extends Menu {

        private final Home home;

        public Settings(SHHomes plugin, Player viewer, Home home) {
            super(plugin, viewer, "settings");
            this.home = home;
        }

        @Override
        protected String title() {
            return super.title().replace("%home%", home.getDisplayName()).replace("%id%", home.getId());
        }

        @Override
        protected void fill() {
        }

        private boolean stillExists() {
            if (plugin.store().get(viewer.getUniqueId()).contains(home)) {
                return true;
            }
            viewer.closeInventory();
            return false;
        }

        @Override
        protected Consumer<InventoryClickEvent> button(String id) {
            return switch (id) {
                case "changeName" -> e -> {
                    if (stillExists()) {
                        clickSound();
                        plugin.startEdit(viewer, SHHomes.Edit.NAME, home);
                    }
                };
                case "changeID" -> e -> {
                    if (stillExists()) {
                        clickSound();
                        plugin.startEdit(viewer, SHHomes.Edit.ID, home);
                    }
                };
                case "changeMaterial" -> e -> {
                    if (stillExists()) {
                        clickSound();
                        new Materials(plugin, viewer, home).open();
                    }
                };
                case "changePosition" -> e -> {
                    if (!stillExists()) {
                        return;
                    }
                    clickSound();
                    Location here = viewer.getLocation();
                    if (SHHomes.onOwnIsland(viewer, here)) {
                        home.setLocation(here);
                        plugin.store().save(viewer.getUniqueId());
                        plugin.send(viewer, "positionChanged", "%home%", home.getDisplayName(), "%id%", home.getId());
                    } else {
                        plugin.send(viewer, "notOnIsland");
                    }
                    viewer.closeInventory();
                };
                case "deleteHome" -> e -> {
                    if (stillExists()) {
                        clickSound();
                        new Delete(plugin, viewer, viewer, home, false).open();
                    }
                };
                case "return" -> e -> {
                    clickSound();
                    new Homes(plugin, viewer).open();
                };
                default -> null;
            };
        }
    }

    // ---------------------------------------------------------------- Choix de l'item d'un home

    public static final class Materials extends Menu {

        private final Home home;

        public Materials(SHHomes plugin, Player viewer, Home home) {
            super(plugin, viewer, "materialSelection");
            this.home = home;
        }

        @Override
        protected Set<String> dynamic() {
            return Set.of("selectable");
        }

        @Override
        protected void fill() {
            List<Integer> slots = slots(config().getString("items.selectable.slots"));
            List<String> materials = config().getStringList("items.selectable.materials");
            List<String> lore = config().getStringList("items.selectable.lore");
            for (int i = 0; i < slots.size() && i < materials.size(); i++) {
                String material = materials.get(i);
                set(slots.get(i), item(material, null, lore, 1), e -> {
                    if (!plugin.store().get(viewer.getUniqueId()).contains(home)) {
                        viewer.closeInventory();
                        return;
                    }
                    clickSound();
                    home.setMaterial(material.toUpperCase());
                    plugin.store().save(viewer.getUniqueId());
                    plugin.send(viewer, "materialChanged", "%home%", home.getDisplayName(), "%id%", home.getId());
                    viewer.closeInventory();
                });
            }
        }

        @Override
        protected Consumer<InventoryClickEvent> button(String id) {
            return "return".equals(id) ? e -> {
                clickSound();
                new Settings(plugin, viewer, home).open();
            } : null;
        }
    }

    // ---------------------------------------------------------------- Confirmation de suppression

    public static final class Delete extends Menu {

        private final Player owner;
        private final Home home;
        private final boolean admin;

        public Delete(SHHomes plugin, Player viewer, Player owner, Home home, boolean admin) {
            super(plugin, viewer, "deleteHome");
            this.owner = owner;
            this.home = home;
            this.admin = admin;
        }

        @Override
        protected String title() {
            return super.title().replace("%joueur%", owner.getName());
        }

        /**
         * Clients 1.21.6 et plus : confirmation par dialogue ; sinon (ou dialogues désactivés) le menu ci-dessous.
         */
        @Override
        public void open() {
            if (!plugin.askDelete(viewer, owner, home, admin, () -> super.open())) {
                super.open();
            }
        }

        @Override
        protected void fill() {
        }

        @Override
        protected Consumer<InventoryClickEvent> button(String id) {
            return switch (id) {
                case "delete" -> e -> {
                    clickSound();
                    if (plugin.store().get(owner.getUniqueId()).remove(home)) {
                        plugin.store().save(owner.getUniqueId());
                        if (admin) {
                            plugin.send(viewer, "admin.delete", "%home%", home.getDisplayName(), "%joueur%", owner.getName(), "%id%", home.getId());
                        } else {
                            plugin.send(viewer, "homeDeleted", "%home%", home.getDisplayName(), "%id%", home.getId());
                        }
                    }
                    viewer.closeInventory();
                };
                case "save" -> e -> {
                    clickSound();
                    if (admin) {
                        new Admin(plugin, viewer, owner).open();
                    } else {
                        new Settings(plugin, viewer, home).open();
                    }
                };
                default -> null;
            };
        }
    }

    // ---------------------------------------------------------------- Homes d'un autre joueur (staff)

    public static final class Admin extends Menu {

        private final Player target;

        public Admin(SHHomes plugin, Player viewer, Player target) {
            super(plugin, viewer, "adminHome");
            this.target = target;
        }

        @Override
        protected String title() {
            return super.title().replace("%joueur%", target.getName());
        }

        @Override
        protected Set<String> dynamic() {
            return Set.of("homes");
        }

        @Override
        protected void fill() {
            List<Home> homes = plugin.store().get(target.getUniqueId());
            int max = plugin.maxHomes(target);
            List<Integer> slots = slots(config().getString("items.homes.slots"));
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.get(i);
                if (i >= homes.size()) {
                    emptyOrLocked(slot, i, max, null);
                    continue;
                }
                Home home = homes.get(i);
                String time = home.getLastUse() == 0L ? "✖" : String.valueOf((System.currentTimeMillis() - home.getLastUse()) / 60000L);
                List<String> lore = replace(config().getStringList("items.homes.set.lore"),
                    "%name%", home.getDisplayName(), "%id%", home.getId(), "%time%", time);
                set(slot, homeItem(home, lore, i + 1), e -> {
                    if (e.getClick() == ClickType.LEFT) {
                        clickSound();
                        viewer.closeInventory();
                        Location location = home.getLocation();
                        if (!viewer.hasPermission("shhomes.admin.tp")) {
                            plugin.send(viewer, "noPerm");
                        } else if (location != null) {
                            plugin.teleportTo(viewer, location);
                            plugin.send(viewer, "admin.teleport", "%home%", home.getDisplayName(), "%id%", home.getId(), "%joueur%", target.getName());
                        }
                    } else if (e.getClick() == ClickType.RIGHT) {
                        clickSound();
                        if (viewer.hasPermission("shhomes.admin.delete")) {
                            new Delete(plugin, viewer, target, home, true).open();
                        } else {
                            plugin.send(viewer, "noPerm");
                            viewer.closeInventory();
                        }
                    }
                });
            }
        }
    }
}
