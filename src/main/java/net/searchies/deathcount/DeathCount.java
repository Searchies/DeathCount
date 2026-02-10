package net.searchies.deathcount;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.searchies.deathcount.command.Deaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeathCount implements ModInitializer {
	public static final String MOD_ID = "deathcount";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		DeathConfig.load();

		CommandRegistrationCallback.EVENT.register(Deaths::register);

		// Wait for SERVER_STARTED to ensure the folder "world/stats" actually exists
		ServerLifecycleEvents.SERVER_STARTED.register(DeathLeaderboard::reloadLeaderboard);

		// Gets called if any Entity dies
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			// Checking if a Player died
			if (entity instanceof ServerPlayerEntity player) {
				int currentDeaths = player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));
				String playerName = Deaths.getPlayerName(player.getCommandSource(), null);
				DeathLeaderboard.update(playerName, currentDeaths);
			}
		});
	}
}