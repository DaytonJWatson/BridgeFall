package com.daytonjwatson.chunkfall.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageUtil {

    private static final String PREFIX = "§6[ChunkFall]§r ";

    private MessageUtil() {
        // Utility class
    }

    // Core low-level helpers
    private static void send(CommandSender sender, String colorCode, String message) {
        sender.sendMessage(PREFIX + colorCode + message + "§r");
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

    // Overloads for Player specifically (optional, but convenient)

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
