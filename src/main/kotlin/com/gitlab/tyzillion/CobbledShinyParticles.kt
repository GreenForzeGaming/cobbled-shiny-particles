package com.gitlab.tyzillion

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleStartedPostEvent
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

object CobbledShinyParticles : ModInitializer {
    private val logger = LoggerFactory.getLogger("cobbled-shiny-particles")
	lateinit var server: MinecraftServer

	// A map to keep track of all shiny Pokémon entities and whether the effect has been played
	private val shinyPokemonMap = mutableMapOf<PokemonEntity, Boolean>()
	private val shinedInBattle = mutableMapOf<PokemonEntity, Boolean>()
	private var tickCounter = 0
	const val maxDistance = 26.0 // Square of the maximum distance for the sound and particles to be played

	override fun onInitialize() {
		logger.info("Initializing Cobbled Shiny Particles")

		CobblemonEvents.BATTLE_STARTED_POST.subscribe { event: BattleStartedPostEvent ->
			shinyPokemonMap.keys.forEach { shinyEntity ->
				if (shinyEntity.isBattling && shinedInBattle[shinyEntity] == false) {
					shinyPokemonMap[shinyEntity] = false
				}
			}
		}

		// Register a Pokémon entity load event
		CobblemonEvents.POKEMON_ENTITY_LOAD.subscribe { pokemonEntity ->
			if (pokemonEntity.pokemonEntity.pokemon.shiny) {
				// Add the shiny Pokémon entity to the map with a flag indicating the effect has not been played
				shinyPokemonMap[pokemonEntity.pokemonEntity] = false
			}
		}

		// Register a Pokémon entity sent out event
		CobblemonEvents.POKEMON_SENT_POST.subscribe { pokemonEntity ->
			if (pokemonEntity.pokemon.shiny) {
				// Add the shiny Pokémon entity to the map with a flag indicating the effect has not been played
				shinyPokemonMap[pokemonEntity.pokemonEntity] = false
			}
		}

		// Register a Pokémon entity spawn event
		CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { pokemonEntity ->
			if (pokemonEntity.entity.pokemon.shiny) {
				// Add the shiny Pokémon entity to the map with a flag indicating the effect has not been played
				shinyPokemonMap[pokemonEntity.entity] = false
			}
		}

		// Register a server tick event to check player distances
		ServerTickEvents.END_SERVER_TICK.register { server ->
			val players = server.playerManager.playerList
			val particleTick = 20
			tickCounter++

			if (tickCounter >= particleTick) {
				tickCounter = 0
				shinyPokemonMap.forEach { (shinyEntity) ->
					if (shinyEntity.isAlive) {
						val isWithinRangeOfAnyPlayer = players.any { player ->
							player.squaredDistanceTo(shinyEntity.pos) <= maxDistance * maxDistance
						}
						if (!isWithinRangeOfAnyPlayer) {
							shinyPokemonMap[shinyEntity] = false
						} else {
							playSparkleAmbientForPlayer(shinyEntity)
							shinyPokemonMap[shinyEntity] = true
						}
					}
				}
			} else {
				shinyPokemonMap.keys.filter { it.isAlive }.forEach { shinyEntity ->
					val nearestPlayer = players.minByOrNull { player ->
						player.squaredDistanceTo(shinyEntity.pos)
					}
					val entityCheck = shinyPokemonMap[shinyEntity]?.not() == true

					if (nearestPlayer != null && nearestPlayer.squaredDistanceTo(shinyEntity.pos) <= maxDistance * maxDistance) {
						if (shinyEntity.isBattling && entityCheck) {
							playStarEffectForPlayer(shinyEntity)
							playWildSparkleEffectForPlayer(shinyEntity)
							shinySoundEffectForPlayer(shinyEntity)
							shinedInBattle[shinyEntity] = true
							shinyPokemonMap[shinyEntity] = true
						} else if (shinyEntity.ownerUuid != null && entityCheck) {
							playStarEffectForPlayer(shinyEntity)
							playWildSparkleEffectForPlayer(shinyEntity)
							shinySoundEffectForPlayer(shinyEntity)
							shinedInBattle[shinyEntity] = false
							shinyPokemonMap[shinyEntity] = true
						} else if (entityCheck){
							playWildStarEffectForPlayer(shinyEntity)
							playWildSparkleEffectForPlayer(shinyEntity)
							wildShinySoundEffectForPlayer(shinyEntity)
							shinedInBattle[shinyEntity] = false
							shinyPokemonMap[shinyEntity] = true
						}
					}
				}
			}
			// Clean up the map for despawned entities
			shinyPokemonMap.keys.removeAll { !it.isAlive }
		}
	}

	private fun wildShinySoundEffectForPlayer(pokemonEntity: PokemonEntity) {
		// Define the sound to play
		val soundIdentifier = Identifier("cobbled-shiny-particles", "shiny")
		val soundEvent : SoundEvent = SoundEvent.of(soundIdentifier)
		// Play a sound at the center of the shiny Pokémon's hitbox for the player
		pokemonEntity.playSound(soundEvent, 2f, 1.0f)
	}


	private fun shinySoundEffectForPlayer(pokemonEntity: PokemonEntity) {
		// Define the sound to play
		val soundIdentifier = Identifier("cobbled-shiny-particles", "shiny_owned")
		val soundEvent : SoundEvent = SoundEvent.of(soundIdentifier)
		// Play a sound at the center of the shiny Pokémon's hitbox for the player
		pokemonEntity.playSound(soundEvent, 2f, 1.0f)
	}

	private fun playWildStarEffectForPlayer(shinyEntity: Entity) {
		hitboxDetector(shinyEntity, "wild_stars")
	}

	private fun playWildSparkleEffectForPlayer(shinyEntity: Entity) {
		hitboxDetector(shinyEntity, "wild_sparkles")
	}

	private fun playSparkleAmbientForPlayer(shinyEntity: Entity) {
		hitboxDetector(shinyEntity, "sparkles_ambient")
	}

	private fun playStarEffectForPlayer(shinyEntity: Entity) {
		hitboxDetector(shinyEntity, "stars")
	}

	private fun hitboxDetector(shinyEntity: Entity, particle: String ) {
		val hitboxCenter = shinyEntity.boundingBox.center
		val hitbox = shinyEntity.boundingBox
		val hitboxVolume = (hitbox.maxX - hitbox.minX) * (hitbox.maxY - hitbox.minY) * (hitbox.maxZ - hitbox.minZ)
		val smallVolumeThreshold = 1.0
		val mediumVolumeThreshold = 2.0
		val particleIdentifier = when {
			hitboxVolume <= smallVolumeThreshold -> Identifier("cobblemon:shiny_${particle}_small")
			hitboxVolume <= mediumVolumeThreshold -> Identifier("cobblemon:shiny_${particle}_medium")
			shinyEntity.name.string == "Wailord" -> Identifier("cobblemon:shiny_${particle}_wailord")
			shinyEntity.name.string == "Wishiwashi" -> Identifier("cobblemon:shiny_${particle}_wailord")
			else -> Identifier("cobblemon:shiny_${particle}_large")
		}
		val spawnSnowstormParticlePacket = SpawnSnowstormParticlePacket(particleIdentifier, hitboxCenter)
		spawnSnowstormParticlePacket.sendToAllPlayers()
	}
}
