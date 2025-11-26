package com.daytonjwatson.chunkfall.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Consistent, minimal messaging for the cobblestone generator feature.
 */
public final class CobbleGenMessages {

    private static final String PREFIX = "§8[§bCobbleGen§8]§r ";

    private CobbleGenMessages() {
        // Utility
    }

    private static void send(CommandSender sender, String color, String message) {
        sender.sendMessage(PREFIX + color + message + "§r");
    }

    public static void success(CommandSender sender, String message) {
        send(sender, "§a", message);
    }

    public static void info(CommandSender sender, String message) {
        send(sender, "§b", message);
    }

    public static void warning(CommandSender sender, String message) {
        send(sender, "§e", message);
    }

    public static void error(CommandSender sender, String message) {
        send(sender, "§c", message);
    }

    public static void success(Player player, String message) {
        success((CommandSender) player, message);
    }

    public static void info(Player player, String message) {
        info((CommandSender) player, message);
    }

    public static void warning(Player player, String message) {
        warning((CommandSender) player, message);
    }

    public static void error(Player player, String message) {
        error((CommandSender) player, message);
    }
}
