package org.Cry_Legende.sHHomes;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.Cry_Legende.sHHomes.menu.Menu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class SHHomes extends JavaPlugin implements Listener {

    private static final Material[] BEDS = {Material.RED_BED, Material.ORANGE_BED, Material.YELLOW_BED, Material.LIME_BED,
        Material.GREEN_BED, Material.CYAN_BED, Material.LIGHT_BLUE_BED, Material.BLUE_BED, Material.PURPLE_BED,
        Material.MAGENTA_BED, Material.PINK_BED, Material.BROWN_BED, Material.BLACK_BED};

    /**
     * Saisie en attente dans le chat : nouveau nom ou nouvel ID d'un home.
     */
    public enum Edit {NAME, ID}

    private record PendingEdit(Edit type, Home home) {
    }

    private HomeStore store;
    private final Map<UUID, PendingEdit> pending = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        migrateFromEasyHome();
        saveDefaultConfig();
        store = new HomeStore(this, new File(getDataFolder(), "data"));
        Bukkit.getOnlinePlayers().forEach(p -> store.get(p.getUniqueId()));

        Commands commands = new Commands(this);
        for (String name : List.of("home", "sethome", "delhome")) {
            getCommand(name).setExecutor(commands);
            getCommand(name).setTabCompleter(commands);
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                p.closeInventory();
            }
        });
        if (store != null) {
            store.shutdown();
        }
    }

    // Premier démarrage : reprend la config et les homes d'EasyHome (les originaux restent en place)
    private void migrateFromEasyHome() {
        File old = new File(getDataFolder().getParentFile(), "EasyHome");
        File config = new File(getDataFolder(), "config.yml");
        File data = new File(getDataFolder(), "data");
        try {
            getDataFolder().mkdirs();
            File oldConfig = new File(old, "config.yml");
            if (!config.exists() && oldConfig.exists()) {
                Files.copy(oldConfig.toPath(), config.toPath());
                getLogger().info("Configuration reprise d'EasyHome.");
            }
            File[] oldFiles = new File(old, "data").listFiles((d, n) -> n.endsWith(".json"));
            if (!data.exists() && oldFiles != null) {
                data.mkdirs();
                for (File f : oldFiles) {
                    Files.copy(f.toPath(), new File(data, f.getName()).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
                getLogger().info(oldFiles.length + " fichiers de homes repris d'EasyHome.");
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Reprise des données d'EasyHome impossible", e);
        }
    }

    public HomeStore store() {
        return store;
    }

    // ---- Groupes (permission shhomes.group.<nom>) ----

    public int maxHomes(Player player) {
        ConfigurationSection groups = getConfig().getConfigurationSection("groups");
        int max = getConfig().getInt("groups.default.maxHomes");
        for (String name : groups.getKeys(false)) {
            if (player.hasPermission("shhomes.group." + name.toLowerCase())) {
                max = Math.max(max, groups.getInt(name + ".maxHomes"));
            }
        }
        return max;
    }

    public long cooldown(Player player) {
        ConfigurationSection groups = getConfig().getConfigurationSection("groups");
        long cooldown = getConfig().getLong("groups.default.cooldown");
        for (String name : groups.getKeys(false)) {
            if (player.hasPermission("shhomes.group." + name.toLowerCase())) {
                cooldown = Math.min(cooldown, groups.getLong(name + ".cooldown"));
            }
        }
        return cooldown;
    }

    // ---- Île du joueur ----

    private static Dimension normal() {
        for (Dimension d : Dimension.values()) {
            if (d.getEnvironment() == World.Environment.NORMAL) {
                return d;
            }
        }
        return Dimension.getByName("NORMAL");
    }

    /**
     * Même règle qu'EasyHome : monde normal de l'île, à moins de getIslandSize() du centre.
     */
    public static boolean onOwnIsland(UUID player, String world, double x, double z) {
        SuperiorPlayer sp = SuperiorSkyblockAPI.getPlayer(player);
        Island island = sp == null ? null : sp.getIsland();
        if (island == null || world == null) {
            return false;
        }
        Dimension dimension = normal();
        World islandWorld = SuperiorSkyblockAPI.getIslandsWorld(island, dimension);
        if (islandWorld == null || !islandWorld.getName().equalsIgnoreCase(world)) {
            return false;
        }
        Location center = island.getCenter(dimension);
        int size = island.getIslandSize();
        return x < center.getX() + size && x > center.getX() - size && z < center.getZ() + size && z > center.getZ() - size;
    }

    public static boolean onOwnIsland(Player player, Location loc) {
        return loc.getWorld() != null && onOwnIsland(player.getUniqueId(), loc.getWorld().getName(), loc.getX(), loc.getZ());
    }

    // ---- Actions partagées par les commandes et les menus ----

    public void teleport(Player player, Home home) {
        if (!onOwnIsland(player.getUniqueId(), home.getWorld(), home.getX(), home.getZ())) {
            store.get(player.getUniqueId()).remove(home);
            store.save(player.getUniqueId());
            getLogger().info("Home " + home.getId() + " de " + player.getName() + " supprimé (hors de son île) : "
                + home.getWorld() + " " + (int) home.getX() + " " + (int) home.getY() + " " + (int) home.getZ());
            send(player, "notValidHome");
            return;
        }
        long wait = home.getLastUse() + cooldown(player) * 1000L - System.currentTimeMillis();
        if (home.getLastUse() != 0L && wait > 0) {
            send(player, "needToWait", "%time%", String.valueOf(wait / 1000L));
            return;
        }
        Location location = home.getLocation();
        if (location == null) {
            send(player, "notValidHome");
            return;
        }
        home.setLastUse(System.currentTimeMillis());
        store.save(player.getUniqueId());
        teleportTo(player, location);
        send(player, "teleportComplete", "%home%", home.getDisplayName(), "%id%", home.getId());
    }

    public void teleportTo(Player player, Location location) {
        player.teleportAsync(location).thenAccept(ok -> {
            if (ok) {
                playSound(player, getConfig().getString("settings.teleportSound"));
            }
        });
    }

    /**
     * Crée un home à la position du joueur ; id null = premier "homeN" libre.
     */
    public void createHome(Player player, String id) {
        List<Home> homes = store.get(player.getUniqueId());
        if (homes.size() >= maxHomes(player)) {
            send(player, "reachedMaxHomes");
            return;
        }
        boolean chosen = id != null;
        if (!chosen) {
            int i = homes.size() + 1;
            while (store.find(player.getUniqueId(), "home" + i) != null) {
                i++;
            }
            id = "home" + i;
        } else if (store.find(player.getUniqueId(), id) != null) {
            send(player, "alreadyID");
            return;
        }
        if (!onOwnIsland(player, player.getLocation())) {
            send(player, "notOnIsland");
            return;
        }
        String pattern = getConfig().getString("menus.home.items.homes.set.name", "&6Home &e#%id%");
        String bed = BEDS[ThreadLocalRandom.current().nextInt(BEDS.length)].name();
        homes.add(new Home(player.getLocation(), pattern.replace("%id%", id), id, bed));
        store.save(player.getUniqueId());
        if (chosen) {
            send(player, "homeCreated", "%id%", id);
        } else {
            send(player, "homeCreated", "%home%", pattern, "%id%", id);
        }
    }

    public void startEdit(Player player, Edit type, Home home) {
        pending.put(player.getUniqueId(), new PendingEdit(type, home));
        send(player, type == Edit.NAME ? "changingName" : "changingID");
        player.closeInventory();
    }

    // ---- Messages et sons ----

    @SuppressWarnings("deprecation")
    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    public void send(Player player, String path, String... replacements) {
        String message = getConfig().getString("messages." + path);
        if (message == null || message.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        player.sendMessage(color(message));
    }

    @SuppressWarnings("deprecation")
    public void playSound(Player player, String name) {
        if (name == null) {
            return;
        }
        try {
            player.playSound(player.getLocation(), Sound.valueOf(name), 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Son inconnu dans la config : " + name);
        }
    }

    // ---- Événements ----

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        store.get(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        store.unload(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingEdit edit = pending.remove(player.getUniqueId());
        if (edit == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(this, () -> applyEdit(player, edit, message));
    }

    private void applyEdit(Player player, PendingEdit edit, String message) {
        Home home = edit.home();
        if (message.equalsIgnoreCase(getConfig().getString("settings.cancelWord", "annuler"))) {
            send(player, "changeCancel");
            return;
        }
        if (!store.get(player.getUniqueId()).contains(home)) {
            return;
        }
        if (edit.type() == Edit.ID) {
            String id = message.split(" ")[0];
            Home other = store.find(player.getUniqueId(), id);
            if (id.isEmpty() || (other != null && other != home)) {
                send(player, "alreadyID");
                return;
            }
            home.setId(id);
            send(player, "IDChanged", "%home%", home.getDisplayName());
        } else {
            home.setDisplayName(message);
            send(player, "nameChanged", "%home%", message, "%id%", home.getId());
        }
        store.save(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory() && event.getWhoClicked() instanceof Player) {
                menu.click(event);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    public void openHomes(Player player) {
        new Menu.Homes(this, player).open();
    }
}
