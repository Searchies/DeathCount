package net.searchies.deathcount;


import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.searchies.deathcount.command.Deaths;
import net.searchies.deathcount.util.DeathConfig;
import net.searchies.deathcount.util.DeathLeaderboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeathCount implements ModInitializer {
	public static final String MOD_ID = "deathcount";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		DeathConfig.load();

		CommandRegistrationCallback.EVENT.register(Deaths::register);

		ServerLifecycleEvents.SERVER_STARTED.register(DeathLeaderboard::reloadLeaderboard);

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayerEntity player) {
				int currentDeaths = player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));
				String playerName = Deaths.getPlayerName(player.getCommandSource(), null);
				DeathLeaderboard.update(playerName, currentDeaths);
			}
		});
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
