package org.Cry_Legende.sHHomes;

import org.Cry_Legende.sHHomes.menu.Menu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /home [id | view <joueur>], /sethome [id], /delhome <id> : mêmes usages qu'EasyHome.
 */
final class Commands implements TabExecutor {

    private final SHHomes plugin;

    Commands(SHHomes plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        switch (command.getName()) {
            case "home" -> home(player, args);
            case "sethome" -> {
                if (args.length > 1) {
                    plugin.send(player, "unknownCommand");
                } else {
                    plugin.createHome(player, args.length == 1 ? args[0] : null);
                }
            }
            case "delhome" -> deleteHome(player, args);
            default -> {
            }
        }
        return true;
    }

    private void home(Player player, String[] args) {
        if (args.length == 0) {
            plugin.openHomes(player);
        } else if (args.length == 1) {
            Home home = find(player, args[0]);
            if (home != null) {
                plugin.teleport(player, home);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("view")) {
            if (!player.hasPermission("shhomes.admin.view")) {
                plugin.send(player, "noPerm");
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                plugin.send(player, "admin.unknownPlayer");
            } else if (plugin.store().get(target.getUniqueId()).isEmpty()) {
                plugin.send(player, "admin.noHome", "%joueur%", target.getName());
            } else {
                new Menu.Admin(plugin, player, target).open();
            }
        } else {
            plugin.send(player, "unknownCommand");
        }
    }

    private void deleteHome(Player player, String[] args) {
        if (args.length != 1) {
            plugin.send(player, "unknownCommand");
            return;
        }
        Home home = find(player, args[0]);
        if (home != null) {
            new Menu.Delete(plugin, player, player, home, false).open();
        }
    }

    /**
     * Home du joueur par ID, avec le message d'erreur adapté s'il n'existe pas.
     */
    private Home find(Player player, String id) {
        if (plugin.store().get(player.getUniqueId()).isEmpty()) {
            plugin.send(player, "noHome");
            return null;
        }
        Home home = plugin.store().find(player.getUniqueId(), id);
        if (home == null) {
            plugin.send(player, "unknownHome");
        }
        return home;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player player) || command.getName().equals("sethome")) {
            return out;
        }
        if (args.length == 1) {
            String start = args[0].toLowerCase();
            for (Home home : plugin.store().get(player.getUniqueId())) {
                if (home.getId().toLowerCase().startsWith(start)) {
                    out.add(home.getId());
                }
            }
            if (command.getName().equals("home") && player.hasPermission("shhomes.admin.view") && "view".startsWith(start)) {
                out.add("view");
            }
        } else if (args.length == 2 && command.getName().equals("home") && args[0].equalsIgnoreCase("view")
            && player.hasPermission("shhomes.admin.view")) {
            String start = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(start)) {
                    out.add(p.getName());
                }
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }
}
