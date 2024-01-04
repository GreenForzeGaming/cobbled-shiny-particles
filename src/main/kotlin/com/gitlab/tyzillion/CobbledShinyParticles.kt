package com.gitlab.tyzillion

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

object CobbledShinyParticles : ModInitializer {
    private val logger = LoggerFactory.getLogger("cobbled-shiny-particles")
	lateinit var server: MinecraftServer

	// A map to keep track of all shiny Pokémon entities and whether the effect has been played
	private val shinyPokemonMap = mutableMapOf<Entity, Boolean>()
	private var tickCounter = 0


	override fun onInitialize() {
		logger.info("Initializing Cobbled Shiny Particles")

		// Register a battle started event
		CobblemonEvents.BATTLE_STARTED_POST.subscribe {
			logger.info("Battle started")
			shinyPokemonMap.keys.forEach { shinyEntity ->
				//I just set the flag to false here so that the effect will play again when the battle starts cuz I couldn't figure out how to make it grab battling entities.
				shinyPokemonMap[shinyEntity] = false
			}
		}

		// Register a Pokémon entity load event
		CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe() { pokemonEntity ->
			if (pokemonEntity.pokemonEntity.pokemon.shiny) {
				// Add the shiny Pokémon entity to the map with a flag indicating the effect has not been played
				shinyPokemonMap[pokemonEntity.pokemonEntity] = false
			}
		}

		// Register a Pokémon entity sent out event
		CobblemonEvents.POKEMON_SENT_POST.subscribe() { pokemonEntity ->
			if (pokemonEntity.pokemon.shiny) {
				// Add the shiny Pokémon entity to the map with a flag indicating the effect has not been played
				shinyPokemonMap[pokemonEntity.pokemonEntity] = false
			}
		}

		// Register a Pokémon entity spawn event
		CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe() { pokemonEntity ->
			if (pokemonEntity.entity.pokemon.shiny) {
				// Add the shiny Pokémon entity to the map with a flag indicating the effect has not been played
				shinyPokemonMap[pokemonEntity.entity] = false
			}
		}

		// Register a server tick event to check player distances
		ServerTickEvents.END_SERVER_TICK.register { server ->
			val maxDistance = 26.0 // Square of the maximum distance for the sound and particles to be played
			val players = server.playerManager.playerList
			val particleTick = 20
			// Increment the tick counter
			tickCounter++

			// Iterate through all shiny Pokémon entities
			shinyPokemonMap.keys.forEach { shinyEntity ->

				// Check if the effect has not been played yet
				if (shinyPokemonMap[shinyEntity] == false) {
					// Iterate through all players
					for (player in players) {
						// Check if the player is within the maximum distance
						if (player.squaredDistanceTo(shinyEntity.pos) <= maxDistance * maxDistance) {
							// Play sound and spawn particles at the current location of the shiny Pokémon for the player
							playStarEffectForPlayer(player, shinyEntity)
							playSparkleEffectForPlayer(player, shinyEntity)
							// Update the flag to indicate the effect has been played
							shinyPokemonMap[shinyEntity] = true
							break // No need to check other players once the effect is played
						}
					}
				}

				if (tickCounter >= particleTick) {
					shinyPokemonMap.keys.forEach { shinyEntity ->
						// Check if the effect has been played
						if (shinyPokemonMap[shinyEntity] == true) {
							// Iterate through all players
							for (player in players) {
								if (player.squaredDistanceTo(shinyEntity.pos) <= maxDistance * maxDistance) {
									playSparkleAmbientForPlayer(player, shinyEntity)
									if (player.squaredDistanceTo(shinyEntity.pos) > maxDistance * maxDistance) {
										break
									}
								}
							}
						}
					}
					tickCounter = 0
				}

				if (shinyPokemonMap[shinyEntity] == true) {
					// Iterate through all players
					for (player in players) {
						if (player.squaredDistanceTo(shinyEntity.pos) > maxDistance * maxDistance) {
							shinyPokemonMap[shinyEntity] = false
							break
						}
					}
				}
			}

			// Remove entries for shiny Pokémon that despawned
			shinyPokemonMap.keys.removeAll { !it.isAlive }
		}
	}

	private fun playStarEffectForPlayer(player: ServerPlayerEntity, shinyEntity: Entity) {
		// Calculate the center of the shiny Pokémon's hitbox
		val hitboxCenter = shinyEntity.boundingBox.center

		val hitbox = shinyEntity.boundingBox
		val hitboxVolume = (hitbox.maxX - hitbox.minX) * (hitbox.maxY - hitbox.minY) * (hitbox.maxZ - hitbox.minZ)

		// Define thresholds for different hitbox sizes (these values are examples)
		val smallVolumeThreshold = 1.0
		val mediumVolumeThreshold = 2.0

		// Determine which particle effect to play based on the hitbox size
		val particleIdentifier = when {
			hitboxVolume <= smallVolumeThreshold -> Identifier("cobblemon:shiny_stars_small")
			hitboxVolume <= mediumVolumeThreshold -> Identifier("cobblemon:shiny_stars_medium")
			shinyEntity.name.string == "Wailord" -> Identifier("cobblemon:shiny_stars_wailord")
			shinyEntity.name.string == "Wishiwashi" -> Identifier("cobblemon:shiny_stars_wailord")
			else -> Identifier("cobblemon:shiny_stars_large")
		}

		// Play a sound at the center of the shiny Pokémon's hitbox for the player
		player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 3.0f, 3.0f)
		player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 3.0f, 1.0f)
		player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.AMBIENT, 3.0f, 2.0f)
		player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.AMBIENT, 3.0f, 3.0f)
		player.playSound(SoundEvents.BLOCK_AMETHYST_CLUSTER_STEP, SoundCategory.AMBIENT, 3.0f, 5.0f)

		// Spawn the chosen particle effect at the center of the shiny Pokémon's hitbox for the player
		val spawnSnowstormParticlePacket = SpawnSnowstormParticlePacket(particleIdentifier, hitboxCenter)
		spawnSnowstormParticlePacket.sendToAllPlayers()
	}

	private fun playSparkleEffectForPlayer(player: ServerPlayerEntity, shinyEntity: Entity) {
		// Calculate the center of the shiny Pokémon's hitbox
		val hitboxCenter = shinyEntity.boundingBox.center

		val hitbox = shinyEntity.boundingBox
		val hitboxVolume = (hitbox.maxX - hitbox.minX) * (hitbox.maxY - hitbox.minY) * (hitbox.maxZ - hitbox.minZ)

		// Define thresholds for different hitbox sizes (these values are examples)
		val smallVolumeThreshold = 1.0
		val mediumVolumeThreshold = 2.0

		// Determine which particle effect to play based on the hitbox size
		val particleIdentifier = when {
			hitboxVolume <= smallVolumeThreshold -> Identifier("cobblemon:shiny_sparkles_small")
			hitboxVolume <= mediumVolumeThreshold -> Identifier("cobblemon:shiny_sparkles_medium")
			shinyEntity.name.string == "Wailord" -> Identifier("cobblemon:shiny_sparkles_wailord")
			shinyEntity.name.string == "Wishiwashi" -> Identifier("cobblemon:shiny_sparkles_wailord")
			else -> Identifier("cobblemon:shiny_sparkles_large")
		}

		// Spawn the chosen particle effect at the center of the shiny Pokémon's hitbox for the player
		val spawnSnowstormParticlePacket = SpawnSnowstormParticlePacket(particleIdentifier, hitboxCenter)
		spawnSnowstormParticlePacket.sendToAllPlayers()
	}

	private fun playSparkleAmbientForPlayer(player: ServerPlayerEntity, shinyEntity: Entity) {
		// Calculate the center of the shiny Pokémon's hitbox
		val hitboxCenter = shinyEntity.boundingBox.center

		val hitbox = shinyEntity.boundingBox
		val hitboxVolume = (hitbox.maxX - hitbox.minX) * (hitbox.maxY - hitbox.minY) * (hitbox.maxZ - hitbox.minZ)

		// Define thresholds for different hitbox sizes (these values are examples)
		val smallVolumeThreshold = 1.0
		val mediumVolumeThreshold = 2.0

		// Determine which particle effect to play based on the hitbox size
		val particleIdentifier = when {
			hitboxVolume <= smallVolumeThreshold -> Identifier("cobblemon:shiny_sparkles_ambient_small")
			hitboxVolume <= mediumVolumeThreshold -> Identifier("cobblemon:shiny_sparkles_ambient_medium")
			shinyEntity.name.string == "Wailord" -> Identifier("cobblemon:shiny_sparkles_ambient_wailord")
			shinyEntity.name.string == "Wishiwashi" -> Identifier("cobblemon:shiny_sparkles_ambient_wailord")
			else -> Identifier("cobblemon:shiny_sparkles_ambient_large")
		}

		// Spawn the chosen particle effect at the center of the shiny Pokémon's hitbox for the player
		val spawnSnowstormParticlePacket = SpawnSnowstormParticlePacket(particleIdentifier, hitboxCenter)
		spawnSnowstormParticlePacket.sendToAllPlayers()
	}
}