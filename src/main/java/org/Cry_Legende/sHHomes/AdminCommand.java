package org.Cry_Legende.sHHomes;

import org.Cry_Legende.sHHomes.network.NetworkHomes;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * /shhomes reload | import | list &lt;joueur&gt;.
 * <p>
 * import reprend en base partagée les fichiers data/&lt;uuid&gt;.json de ce serveur (mode réseau). À lancer une fois,
 * sur le serveur qui a les homes. Un joueur qui a déjà des homes en base n'est pas touché, la commande peut donc être
 * relancée sans danger.
 */
final class AdminCommand implements TabExecutor {

    /** Sous-commandes, dans l'ordre d'affichage de la ligne d'usage. */
    private static final List<String> SUBCOMMANDS = List.of("reload", "import", "list");

    private final SHHomes plugin;
    private final AtomicBoolean running = new AtomicBoolean();

    AdminCommand(SHHomes plugin) {
        this.plugin = plugin;
    }

    private static String permission(String sub) {
        return "shhomes.admin." + sub;
    }

    /** Sous-commandes que l'expéditeur a le droit d'utiliser. */
    private static List<String> allowed(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (sender.hasPermission(permission(sub))) {
                names.add(sub);
            }
        }
        return names;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> allowed = allowed(sender);
        if (allowed.isEmpty()) {
            plugin.send(sender, "noPerm");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!allowed.contains(sub)) {
            plugin.send(sender, "admin.usage", "%subcommands%", String.join("|", allowed));
            return true;
        }
        switch (sub) {
            case "reload" -> reload(sender);
            case "import" -> importHomes(sender, args);
            default -> list(sender, args);
        }
        return true;
    }

    private void reload(CommandSender sender) {
        try {
            plugin.applyFiles(plugin.readFiles());
        } catch (SHHomes.ConfigException e) {
            plugin.getLogger().log(Level.SEVERE, "Rechargement annulé, " + e.getFile() + " illisible : " + e.getMessage());
            plugin.send(sender, "admin.reloadFailed", "%file%", e.getFile(), "%error%", String.valueOf(e.getMessage()));
            return;
        }
        plugin.send(sender, "admin.reloaded");
    }

    private void importHomes(CommandSender sender, String[] args) {
        if (args.length != 1) {
            plugin.send(sender, "admin.importUsage");
            return;
        }
        NetworkHomes network = plugin.network();
        if (network == null) {
            plugin.send(sender, "admin.importUnavailable");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            plugin.send(sender, "admin.importRunning");
            return;
        }
        plugin.send(sender, "admin.importStart");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                runImport(sender, network);
            } finally {
                running.set(false);
            }
        });
    }

    private void runImport(CommandSender sender, NetworkHomes network) {
        File[] files = plugin.store().directory().listFiles((d, n) -> n.endsWith(".json"));
        int players = 0;
        int homes = 0;
        int already = 0;
        int unreadable = 0;
        int errors = 0;
        if (files != null) {
            for (File file : files) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - ".json".length()));
                } catch (IllegalArgumentException notAPlayer) {
                    continue;
                }
                List<Home> list = plugin.store().readFile(file);
                if (list == null) {
                    unreadable++;
                    continue;
                }
                if (list.isEmpty()) {
                    continue;
                }
                try {
                    if (network.importHomes(uuid, list)) {
                        players++;
                        homes += list.size();
                    } else {
                        already++;
                    }
                } catch (SQLException e) {
                    errors++;
                    plugin.getLogger().log(Level.SEVERE, "Reprise des homes de " + uuid + " impossible : " + e.getMessage());
                }
            }
        }
        plugin.send(sender, "admin.importDone", "%joueurs%", String.valueOf(players), "%homes%", String.valueOf(homes),
            "%deja%", String.valueOf(already), "%illisibles%", String.valueOf(unreadable), "%erreurs%", String.valueOf(errors));
    }

    /**
     * /shhomes list &lt;joueur&gt; : homes d'un joueur, connecté ou non. Le joueur est retrouvé par son pseudo s'il est
     * connecté, sinon par l'UUID « hors ligne » du pseudo exact tapé, celui de tout le réseau (jamais de recherche Mojang).
     */
    private void list(CommandSender sender, String[] args) {
        if (args.length != 2) {
            plugin.send(sender, "admin.listUsage");
            return;
        }
        Player online = Bukkit.getPlayerExact(args[1]);
        String name = online != null ? online.getName() : args[1];
        UUID uuid = online != null ? online.getUniqueId()
            : UUID.nameUUIDFromBytes(("OfflinePlayer:" + args[1]).getBytes(StandardCharsets.UTF_8));
        NetworkHomes network = plugin.network();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Home> homes;
            try {
                if (network != null) {
                    homes = network.peek(uuid);
                } else {
                    File file = new File(plugin.store().directory(), uuid + ".json");
                    homes = file.isFile() ? plugin.store().readFile(file) : new ArrayList<>();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Homes de " + uuid + " non lus en base : " + e.getMessage());
                plugin.send(sender, "admin.listError", "%joueur%", name);
                return;
            }
            if (homes == null) {
                plugin.send(sender, "admin.listError", "%joueur%", name);
                return;
            }
            if (homes.isEmpty()) {
                plugin.send(sender, "admin.noHome", "%joueur%", name);
                return;
            }
            plugin.send(sender, "admin.listHeader", "%joueur%", name, "%nombre%", String.valueOf(homes.size()));
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            for (Home home : homes) {
                String last = home.getLastUse() == 0L ? "-" : format.format(new Date(home.getLastUse()));
                plugin.send(sender, "admin.listLine", "%id%", home.getId(), "%home%", home.getDisplayName(),
                    "%monde%", String.valueOf(home.getWorld()), "%x%", String.valueOf((int) home.getX()),
                    "%y%", String.valueOf((int) home.getY()), "%z%", String.valueOf((int) home.getZ()), "%dernier%", last);
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> allowed = allowed(sender);
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return allowed.stream().filter(name -> name.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list") && allowed.contains("list")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
