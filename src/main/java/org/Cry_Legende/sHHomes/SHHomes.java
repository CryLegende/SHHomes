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
import java.util.ArrayList;
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

    /**
     * Issue d'une modification d'ID ou de nom.
     */
    private enum Commit {DONE, ID_TAKEN}

    private HomeStore store;
    private final Map<UUID, PendingEdit> pending = new ConcurrentHashMap<>();
    // null sur un serveur sans dialogues (avant 1.21.6) : tout passe alors par le chat
    private Dialogs dialogs;

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
        enableDialogs();
    }

    @Override
    public void onDisable() {
        if (dialogs != null) {
            dialogs.clear();
        }
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
        if (dialogs != null && dialogs.supported(player)) {
            player.closeInventory();
            if (editDialog(player, type, home, null, null)) {
                return;
            }
        }
        chatEdit(player, type, home);
    }

    /**
     * Saisie dans le chat (clients sans dialogues, ou dialogues désactivés).
     */
    private void chatEdit(Player player, Edit type, Home home) {
        pending.put(player.getUniqueId(), new PendingEdit(type, home));
        send(player, type == Edit.NAME ? "changingName" : "changingID");
        player.closeInventory();
    }

    // ---- Dialogues natifs (clients 1.21.6 et plus) ----

    private void enableDialogs() {
        try {
            Class.forName("io.papermc.paper.event.player.PlayerCustomClickEvent");
            dialogs = new Dialogs(this);
            getServer().getPluginManager().registerEvents(dialogs, this);
        } catch (ClassNotFoundException | LinkageError e) {
            dialogs = null;
            getLogger().info("Dialogues indisponibles sur ce serveur : saisie dans le chat.");
        }
    }

    private String dialogText(String path, String... replacements) {
        return replace(getConfig().getString("dialogues." + path, ""), replacements);
    }

    private List<String> dialogLines(String path, String... replacements) {
        List<String> out = new ArrayList<>();
        for (String line : getConfig().getStringList("dialogues." + path)) {
            out.add(replace(line, replacements));
        }
        return out;
    }

    private static String replace(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    private int dialogMax(Edit type) {
        return Math.max(1, getConfig().getInt("dialogues." + (type == Edit.NAME ? "nom" : "id") + ".longueur-max", 256));
    }

    /**
     * Dialogue de nom ou d'ID. typed = ce que le joueur avait écrit avant une erreur, error = ligne rouge en tête.
     *
     * @return false si le dialogue n'a pas pu être préparé
     */
    private boolean editDialog(Player player, Edit type, Home home, String typed, String error) {
        String section = type == Edit.NAME ? "nom" : "id";
        String[] vars = {"%home%", home.getDisplayName(), "%id%", home.getId()};
        String initial = typed != null ? typed : type == Edit.NAME ? home.getDisplayName() : home.getId();
        Dialogs.Screen screen = new Dialogs.Screen(dialogText(section + ".titre", vars), dialogLines(section + ".corps", vars), error,
            new Dialogs.Field(dialogText(section + ".champ", vars), initial, dialogMax(type)),
            dialogText("boutons.valider"), dialogText("boutons.annuler"));
        return dialogs.show(player, section, screen, (p, confirmed, text) -> onEdited(p, type, home, confirmed, text),
            () -> chatEdit(player, type, home));
    }

    private void onEdited(Player player, Edit type, Home home, boolean confirmed, String text) {
        if (!confirmed) {
            send(player, "changeCancel");
            return;
        }
        // le home a pu être supprimé pendant que la fenêtre était ouverte
        if (!store.get(player.getUniqueId()).contains(home)) {
            send(player, "unknownHome");
            return;
        }
        String message = text == null ? "" : text.trim();
        int max = dialogMax(type);
        String error = null;
        if (message.isEmpty()) {
            error = "vide";
        } else if (message.length() > max) {
            error = "trop-long";
        } else if (message.chars().anyMatch(c -> c < 32 || c == 127)) {
            error = "caracteres";
        }
        if (error != null) {
            editDialog(player, type, home, text, dialogText("erreurs." + (type == Edit.NAME ? "nom-" : "id-") + error, "%max%", String.valueOf(max)));
            return;
        }
        if (commit(player, type, home, message) == Commit.ID_TAKEN) {
            editDialog(player, type, home, text, dialogText("erreurs.id-utilise"));
        }
    }

    /**
     * Demande de confirmation de suppression d'un home, par dialogue si le client le permet.
     *
     * @param gui ouvre le menu de confirmation classique (si l'affichage du dialogue échoue)
     * @return false si le dialogue n'est pas utilisable : l'appelant ouvre alors le menu classique
     */
    public boolean askDelete(Player viewer, Player owner, Home home, boolean admin, Runnable gui) {
        if (dialogs == null || !dialogs.supported(viewer)) {
            return false;
        }
        viewer.closeInventory();
        String[] vars = {"%home%", home.getDisplayName(), "%id%", home.getId(), "%joueur%", owner.getName()};
        Dialogs.Screen screen = new Dialogs.Screen(dialogText("suppression.titre", vars),
            dialogLines(admin ? "suppression.corps-admin" : "suppression.corps", vars), null, null,
            dialogText("suppression.oui"), dialogText("suppression.non"));
        UUID ownerId = owner.getUniqueId();
        return dialogs.show(viewer, "suppression", screen, (p, confirmed, text) -> onDeleteAnswer(p, ownerId, home, admin, confirmed), gui);
    }

    private void onDeleteAnswer(Player viewer, UUID ownerId, Home home, boolean admin, boolean confirmed) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (!confirmed) {
            // même retour que le bouton « Ne pas supprimer le Home » du menu
            if (admin && owner != null) {
                new Menu.Admin(this, viewer, owner).open();
            } else if (!admin) {
                new Menu.Settings(this, viewer, home).open();
            }
            return;
        }
        if (owner == null) {
            send(viewer, "admin.unknownPlayer");
            return;
        }
        // mêmes droits que pour ouvrir le menu : soi-même, ou le staff
        if (admin ? !viewer.hasPermission("shhomes.admin.delete") : !viewer.getUniqueId().equals(ownerId)) {
            send(viewer, "noPerm");
            return;
        }
        playSound(viewer, getConfig().getString("settings.clickSound"));
        if (store.get(ownerId).remove(home)) {
            store.save(ownerId);
            if (admin) {
                send(viewer, "admin.delete", "%home%", home.getDisplayName(), "%joueur%", owner.getName(), "%id%", home.getId());
            } else {
                send(viewer, "homeDeleted", "%home%", home.getDisplayName(), "%id%", home.getId());
            }
        } else {
            send(viewer, "unknownHome");
        }
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
        if (commit(player, edit.type(), home, message) == Commit.ID_TAKEN) {
            send(player, "alreadyID");
        }
    }

    /**
     * Applique le nouveau nom ou le nouvel ID (chemin par le chat et chemin par le dialogue).
     */
    private Commit commit(Player player, Edit type, Home home, String message) {
        if (type == Edit.ID) {
            String id = message.split(" ")[0];
            Home other = store.find(player.getUniqueId(), id);
            if (id.isEmpty() || (other != null && other != home)) {
                return Commit.ID_TAKEN;
            }
            home.setId(id);
            send(player, "IDChanged", "%home%", home.getDisplayName());
        } else {
            home.setDisplayName(message);
            send(player, "nameChanged", "%home%", message, "%id%", home.getId());
        }
        store.save(player.getUniqueId());
        return Commit.DONE;
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
