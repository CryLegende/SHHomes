package org.Cry_Legende.sHHomes;

import org.Cry_Legende.sHHomes.network.NetworkHomes;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * /shhomes import : reprend en base partagée les fichiers data/&lt;uuid&gt;.json de ce serveur (mode réseau).
 * À lancer une fois, sur le serveur qui a les homes. Un joueur qui a déjà des homes en base n'est pas touché,
 * la commande peut donc être relancée sans danger.
 */
final class AdminCommand implements CommandExecutor {

    private final SHHomes plugin;
    private final AtomicBoolean running = new AtomicBoolean();

    AdminCommand(SHHomes plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shhomes.admin.import")) {
            plugin.send(sender, "noPerm");
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("import")) {
            plugin.send(sender, "admin.importUsage");
            return true;
        }
        NetworkHomes network = plugin.network();
        if (network == null) {
            plugin.send(sender, "admin.importUnavailable");
            return true;
        }
        if (!running.compareAndSet(false, true)) {
            plugin.send(sender, "admin.importRunning");
            return true;
        }
        plugin.send(sender, "admin.importStart");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                run(sender, network);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    private void run(CommandSender sender, NetworkHomes network) {
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
}
