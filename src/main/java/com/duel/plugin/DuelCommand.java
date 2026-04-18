package com.duel.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DuelCommand implements CommandExecutor, TabCompleter {

    private final DuelManager duelManager;

    public DuelCommand(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "accept" -> duelManager.acceptDuelRequest(player);
            case "deny", "decline", "no" -> duelManager.denyDuelRequest(player);
            default -> {
                // Treat the argument as a player name to challenge
                String targetName = args[0];
                Player target = Bukkit.getPlayerExact(targetName);

                if (target == null) {
                    player.sendMessage(Component.text("Player '" + targetName + "' is not online.", NamedTextColor.RED));
                    return true;
                }

                duelManager.sendDuelRequest(player, target);
            }
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        player.sendMessage(Component.text("⚔ Duel Commands", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/duel <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Challenge a player to a duel", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/duel accept", NamedTextColor.GREEN)
                .append(Component.text(" - Accept a pending duel request", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/duel deny", NamedTextColor.RED)
                .append(Component.text(" - Deny a pending duel request", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Add subcommands
            for (String sub : List.of("accept", "deny")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }

            // Add online player names
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player p && online.equals(p)) continue;
                if (online.getName().toLowerCase().startsWith(partial)) {
                    completions.add(online.getName());
                }
            }
        }

        return completions;
    }
}
