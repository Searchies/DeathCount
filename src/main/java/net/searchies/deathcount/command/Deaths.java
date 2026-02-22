package net.searchies.deathcount.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.searchies.deathcount.DeathConfig;
import net.searchies.deathcount.DeathLeaderboard;

import java.util.ArrayList;
import java.util.Map;

public class Deaths {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        var root = CommandManager.literal("deaths");

        // /deaths
        root.executes(Deaths::runSelf);

        // /deaths list
        root.then(CommandManager.literal("list")
                .executes(Deaths::runLeaderboard));

        // /deaths reload
        root.then(CommandManager.literal("reload")
                .executes(Deaths::runReload));

        // /deaths <player>
        root.then(CommandManager.argument("target", StringArgumentType.word())
                .suggests((context, builder) -> {
                    // Add tab completion for online players manually
                    return net.minecraft.command.CommandSource.suggestMatching(
                            context.getSource().getServer().getPlayerNames(), builder
                    );
                })
                .executes(Deaths::runPlayer));

        dispatcher.register(root);
    }

    // Checks own deaths
    private static int runSelf(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        try {
            sendDeaths(source, null);

            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Only a player can check their own deaths."));
            return 0;
        }
    }

    // Checks other Player deaths
    private static int runPlayer(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        try {
            String playerName = StringArgumentType.getString(context, "target");
            sendDeaths(source, playerName);

            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Only a player can check their own deaths."));
            return 0;
        }
    }

    // Sends Deaths to the chat
    private static void sendDeaths(ServerCommandSource source, String tempName) {
        try {
            String playerName = getPlayerName(source, tempName);
            try {
                ArrayList<Integer> deathInfo = DeathLeaderboard.getPlayerDeaths(playerName);
                int deaths = deathInfo.get(0);
                int rank = deathInfo.get(1);
                if (deaths == 0) {
                    // They have no "Deaths" in their stat file, so they have 0 Deaths
                    source.sendFeedback(() -> Text.literal("Woah... " + playerName + " might be an Immortal Demon."), false);
                } else {
                    source.sendFeedback(() -> Text.literal(playerName + " has died " + deaths + " times. Rank: #" + rank), false);
                }
            } catch (Exception e) {
                source.sendError(Text.literal(playerName + " does not exist?"));
            }
        } catch (Exception e) {
            if (tempName != null) {
                source.sendError(Text.literal(tempName + " does not exist?"));
            }
        }
    }

    private static int runLeaderboard(CommandContext<ServerCommandSource> context) {
        int limit = DeathConfig.INSTANCE.leaderboardSize;
        var topPlayers = DeathLeaderboard.getTopDeaths(limit);
        ServerCommandSource source = context.getSource();

        source.sendFeedback(() -> Text.literal(" "), false);
        source.sendFeedback(() -> Text.literal("-- Death Leaderboard --").formatted(Formatting.WHITE), false);

        if (topPlayers.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Everyone is immortal... what the freak."), false);
            return 1;
        }

        ServerPlayerEntity player = null;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            // Not a Player
        }

        // Track if the player was found in the Leaderboard
        boolean playerFound = false;

        for (Map.Entry<String, ArrayList<Integer>> entry : topPlayers.entrySet()) {
            String name = entry.getKey();
            int deaths = entry.getValue().get(0);
            int rank = entry.getValue().get(1);

            // Check if this entry belongs to the player running the command
            boolean isSelf = (player != null && name.equals(getPlayerName(source, null)));
            if (isSelf) playerFound = true;

            String prefix = isSelf ? "-> " : "";
            String line = String.format("%s#%d: %s - %d", prefix, rank, name, deaths);

            // Make the top 3 players Red, everyone else White
            Formatting color = (rank <= 3) ? Formatting.RED : Formatting.WHITE;

            // Send the line to the chat
            source.sendFeedback(() -> Text.literal(line).formatted(color), false);
        }

        // Send player's rank who ran the command if they aren't on the Leaderboard
        if (player != null && !playerFound) {
            String name = getPlayerName(source, null);
            ArrayList<Integer> deathInfo = DeathLeaderboard.getPlayerDeaths(name);

            if (deathInfo != null && !deathInfo.isEmpty()) {
                int deaths = deathInfo.get(0);
                int rank = deathInfo.get(1);

                // "-> #15: Search - 12"
                String line = String.format("-> #%d: %s - %d", rank, name, deaths);
                source.sendFeedback(() -> Text.literal(line).formatted(Formatting.WHITE), false);
            }
        }

        return 1;
    }

    // Useful if you manually edited a stat file and want the mod to notice without restarting
    private static int runReload(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        // Check for OP Permission Level
        if (!source.hasPermissionLevel(DeathConfig.INSTANCE.operatorLevel)) {
            source.sendError(Text.literal("You do not have permission to reload the config!"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Reloading stats/configs from files..."), true);

        // Reload Config and Leaderboard
        DeathConfig.load();
        DeathLeaderboard.reloadLeaderboard(source.getServer());

        source.sendFeedback(() -> Text.literal("Reload complete"), true);
        return 1;
    }

    // Get Player Name from UUID to fix conflicts with HarpySMP nickname mod
    // if playerName is null, it's getting someone else's death
    public static String getPlayerName(ServerCommandSource source, String playerName) {
        if (playerName != null) {
            return source.getServer().getUserCache().findByName(playerName).map(GameProfile::getName).get();
        }
        return source.getServer().getUserCache().getByUuid(source.getPlayer().getUuid()).map(GameProfile::getName).get();
    }
}