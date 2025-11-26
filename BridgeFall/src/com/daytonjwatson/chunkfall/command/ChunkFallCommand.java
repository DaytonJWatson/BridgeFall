package com.daytonjwatson.chunkfall.command;

import com.daytonjwatson.chunkfall.ChunkFallPlugin;
import com.daytonjwatson.chunkfall.logic.LimboManager;
import com.daytonjwatson.chunkfall.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChunkFallCommand implements CommandExecutor, TabCompleter {

    private final ChunkFallPlugin plugin;

    public ChunkFallCommand(ChunkFallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "info" -> handleInfo(sender);
            case "limbo" -> handleLimbo(sender, args);
            case "anchors" -> handleAnchors(sender, args);
            default -> MessageUtil.error(sender, "Unknown subcommand. Use /" + label + " help.");
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.info(sender, "ChunkFall commands:");
        MessageUtil.info(sender, "/chunkfall info - Show configuration summary.");
        if (sender.hasPermission("chunkfall.limbo")) {
            MessageUtil.info(sender, "/chunkfall limbo [player] - Send yourself or another player to Limbo.");
        }
        if (sender.hasPermission("chunkfall.anchors")) {
            MessageUtil.info(sender, "/chunkfall anchors [count] - Spawn respawn anchors in Limbo.");
        }
    }

    private void handleInfo(CommandSender sender) {
        MessageUtil.info(sender, "Target world: " + plugin.getChunkFallConfig().getTargetWorldName());
        MessageUtil.info(sender, "Region size: " + plugin.getChunkFallConfig().getRegionSizeChunks() + " chunks");
        MessageUtil.info(sender, "Cobble generator: " + (plugin.getChunkFallConfig().isCobbleGeneratorEnabled() ? "enabled" : "disabled"));
        MessageUtil.info(sender, "Limbo world: " + plugin.getChunkFallConfig().getLimboWorldName());
    }

    private void handleLimbo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chunkfall.limbo")) {
            MessageUtil.error(sender, "You do not have permission to use this command.");
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                MessageUtil.error(sender, "Player not found.");
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                MessageUtil.error(sender, "Console must specify a player.");
                return;
            }
            target = player;
        }

        LimboManager limboManager = plugin.getLimboManager();
        if (limboManager == null) {
            MessageUtil.error(sender, "Limbo manager not ready.");
            return;
        }

        limboManager.sendPlayerToLimbo(target);
        MessageUtil.success(sender, "Sent " + target.getName() + " to Limbo.");
    }

    private void handleAnchors(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chunkfall.anchors")) {
            MessageUtil.error(sender, "You do not have permission to use this command.");
            return;
        }

        int count = plugin.getChunkFallConfig().getLimboAnchorsPerEntry();
        if (args.length >= 2) {
            try {
                count = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ex) {
                MessageUtil.error(sender, "Count must be a number.");
                return;
            }
        }

        LimboManager limboManager = plugin.getLimboManager();
        if (limboManager == null) {
            MessageUtil.error(sender, "Limbo manager not ready.");
            return;
        }

        World limbo = limboManager.getOrCreateLimboWorld();
        if (limbo == null) {
            MessageUtil.error(sender, "Limbo world could not be loaded.");
            return;
        }

        limboManager.spawnRandomAnchors(limbo, count);
        MessageUtil.success(sender, "Spawned " + count + " respawn anchors in Limbo.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("info"));
            if (sender.hasPermission("chunkfall.limbo")) {
                base.add("limbo");
            }
            if (sender.hasPermission("chunkfall.anchors")) {
                base.add("anchors");
            }
            return base;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("limbo")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }

        return List.of();
    }
}
