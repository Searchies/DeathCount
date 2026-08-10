package net.searchies.deathcount.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.util.WorldSavePath;
import net.searchies.deathcount.DeathCount;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

public class DeathLeaderboard {
    private static Map<String, ArrayList<Integer>> DEATH_CACHE = new HashMap<>();
    private static final Map<String, Integer> TEMP_DEATH_CACHE = new HashMap<>();

    public static void reloadLeaderboard(MinecraftServer server) {
        DEATH_CACHE.clear();
        TEMP_DEATH_CACHE.clear();

        File statsDir = server.getSavePath(WorldSavePath.STATS).toFile();

        File[] statFiles = statsDir.listFiles((dir, name) -> name.endsWith(".json"));

        if (statFiles == null) return;

        for (File file : statFiles) {
            try {
                String uuidString = file.getName().replace(".json", "");
                UUID uuid = UUID.fromString(uuidString);

                int deaths = parseDeathsFromFile(file);

                Optional<String> optName = server.getApiServices().nameToIdCache().getByUuid(uuid).map(PlayerConfigEntry::name);;

                if (optName.isEmpty()) continue;

                String name = optName.get();

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

        // "Name", [Deaths, Rank]
        DEATH_CACHE = TEMP_DEATH_CACHE.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            int deaths = entry.getValue();
                            ranks[2]++;


                            if (deaths != ranks[0]) {
                                ranks[1] = ranks[2];
                                ranks[0] = deaths;
                            }

                            return new ArrayList<>(Arrays.asList(deaths, ranks[1]));
                        },
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    // Returns "Name", [Deaths, Rank]
    public static Map<String, ArrayList<Integer>> getTopDeaths(int limit) {
        return DEATH_CACHE.entrySet().stream()
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    public static ArrayList<Integer> getPlayerDeaths(String playerName) {
        return DEATH_CACHE.get(playerName);
    }

    public static void update(String playerName, int deaths) {
        TEMP_DEATH_CACHE.put(playerName, deaths);
        updateDeathCache();
    }

    private static int parseDeathsFromFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            if (json.has("stats")) {
                JsonObject stats = json.getAsJsonObject("stats");
                if (stats.has("minecraft:custom")) {
                    JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                    if (custom.has("minecraft:deaths")) {
                        return custom.get("minecraft:deaths").getAsInt();
                    } else {
                        return -1;
                    }
                }
            }
        } catch (Exception e) {

        }
        return 0;
    }
}