package net.searchies.deathcount;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

public class DeathLeaderboard {
    // We store this here so we don't have to read files every time someone types /deaths
    // and it can track offline players
    private static Map<String, ArrayList<Integer>> DEATH_CACHE = new HashMap<>();
    private static final Map<String, Integer> TEMP_DEATH_CACHE = new HashMap<>();

    // This method runs when the server starts
    public static void reloadLeaderboard(MinecraftServer server) {
        // Clear old data to prevent duplicates/issues
        DEATH_CACHE.clear();
        TEMP_DEATH_CACHE.clear();

        DeathCount.LOGGER.info("Starting Death Leaderboard scan...");
        DeathCount.LOGGER.info("Please note some stat files take longer to save in Minecraft");

        // world/stats
        File statsDir = server.getSavePath(WorldSavePath.STATS).toFile();

        // Getting all files ending in .json
        File[] statFiles = statsDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (statFiles == null) return; // Empty folder

        for (File file : statFiles) {
            try {
                // Filename is the player's UUID
                String uuidString = file.getName().replace(".json", "");
                UUID uuid = UUID.fromString(uuidString);

                int deaths = parseDeathsFromFile(file);

                // UserCache.json is a file that can find Usernames using UUIDs
                Optional<String> optName = server.getUserCache().getByUuid(uuid).map(GameProfile::getName);;

                // Gets rid of non-player UUID stats
                if (optName.isEmpty()) {
                    continue;
                }

                String name = optName.get();

                // Player has 0 Deaths
                if (deaths == -1) {
                    TEMP_DEATH_CACHE.put(name, 0);
                } else {
                    TEMP_DEATH_CACHE.put(name, deaths);
                }

            } catch (Exception e) {
                DeathCount.LOGGER.error("Failed to parse stats for file: " + file.getName(), e);
            }
        }

        updateDeathCache();
    }

    private static void updateDeathCache() {
        // [0: lastDeaths, 1: currentRank, 2: rowCounter]
        int[] ranks = { -1, 0, 0 };

        // Sort Ranks with the Deaths and Names
        // "Name", [Deaths, Rank]
        DEATH_CACHE = TEMP_DEATH_CACHE.entrySet().stream()
                // Sort Highest to Lowest
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                // Organize Leaderboard
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            int deaths = entry.getValue();
                            ranks[2]++; // Always increase the row counter

                            // If score is different from the last one, update the rank
                            if (deaths != ranks[0]) {
                                ranks[1] = ranks[2]; // Rank becomes the current row number
                                ranks[0] = deaths;       // Update last score seen
                            }
                            // If score is the same, we keep the old rank

                            return new ArrayList<>(Arrays.asList(deaths, ranks[1]));
                        },
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    // Getting data for /deaths list
    // Returns "Name", [Deaths, Rank]
    public static Map<String, ArrayList<Integer>> getTopDeaths(int limit) {
        return DEATH_CACHE.entrySet().stream()
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new)); // need this to keep the map organized
    }

    // Getting player Deaths and Rank
    public static ArrayList<Integer> getPlayerDeaths(String playerName) {
        return DEATH_CACHE.get(playerName);
    }

    // Called when a player dies in-game, so we update Temp Cache, then sort the ranks again
    // might be very taxing on memory, change this if it is
    public static void update(String playerName, int deaths) {
        TEMP_DEATH_CACHE.put(playerName, deaths);
        updateDeathCache();
    }

    // Opens a JSON File and finds the Deaths, in our case the JSON files in world/stats
    private static int parseDeathsFromFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            // Parse JSON using Google JSON Reader
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            if (json.has("stats")) {
                JsonObject stats = json.getAsJsonObject("stats");
                if (stats.has("minecraft:custom")) {
                    JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                    if (custom.has("minecraft:deaths")) {
                        // We found it! Return the number.
                        return custom.get("minecraft:deaths").getAsInt();
                    } else {
                        return -1; // Custom error for no Deaths section
                    }
                }
            }
        } catch (Exception e) {
            // If the file is broken or empty, just ignore it
        }
        return 0; // Default to 0 deaths if not found
    }
}