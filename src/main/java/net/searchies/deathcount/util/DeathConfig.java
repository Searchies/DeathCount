package net.searchies.deathcount.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.searchies.deathcount.DeathCount;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class DeathConfig {
    public static DeathConfig INSTANCE;

    public int leaderboardSize = 10;
    public int operatorLevel = 4;

    public static void load() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve("deathcount.json").toFile();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                INSTANCE = gson.fromJson(reader, DeathConfig.class);
            } catch (IOException e) {
                INSTANCE = new DeathConfig();
            }
        } else {
            INSTANCE = new DeathConfig();
            save();
        }
    }

    public static void save() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve("deathcount.json").toFile();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(gson.toJson(INSTANCE));
        } catch (IOException e) {
            DeathCount.LOGGER.error("Config not saved: ", e);
        }
    }
}