package com.gitlab.tyzillion

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.BattleStartedPostEvent
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import java.util.*

object CobbledShinyParticles : ModInitializer {
    private val logger = LoggerFactory.getLogger("cobbled-shiny-particles")
	lateinit var server: MinecraftServer

	// A map to keep track of all shiny Pokémon entities and whether the effect has been played
	private val shinyPokemon = mutableSetOf<UUID>()
	private val shinedInBattle = mutableSetOf<UUID>()
	private var tickCounter = 0
	const val maxDistance = 26.0 // Square of the maximum distance for the sound and particles to be played

	override fun onInitialize() {
		logger.info("Initializing Cobbled Shiny Particles")

		CobblemonEvents.BATTLE_STARTED_POST.subscribe { event: BattleStartedPostEvent ->
			event.battle.activePokemon.forEach { pokemon ->
				val entity = pokemon.battlePokemon?.entity ?: return@forEach
				if (entity.isBattling && !this.shinedInBattle.contains(entity.uuid)) {
					this.shinyPokemon.remove(entity.uuid)
				}
			}
		}

		// Remove entity from shiny set when it is unloaded
		ServerEntityEvents.ENTITY_UNLOAD.register { entity, world ->
			if (this.shinyPokemon.contains(entity.uuid)) {
				this.shinyPokemon.remove(entity.uuid)
			}
		}

		// Register a server tick event to check player distances
		ServerTickEvents.END_SERVER_TICK.register { server ->
			val players = server.playerManager.playerList
			val particleTick = 20
			tickCounter++

			if (tickCounter >= particleTick) {
				tickCounter = 0
				server.worlds.forEach { world ->
					world.iterateEntities().forEach { entity ->
						if (entity !is PokemonEntity || !entity.pokemon.shiny) {
							return@forEach
						}
						val isWithinRangeOfAnyPlayer = players.any { player ->
							player.squaredDistanceTo(entity.pos) <= maxDistance * maxDistance
						}
						if (!isWithinRangeOfAnyPlayer) {
							shinyPokemon.remove(entity.uuid)
						} else {
							playSparkleAmbientForPlayer(entity)
							shinyPokemon.add(entity.uuid)
						}
					}
				}
			} else {
				server.worlds.forEach { world ->
					world.iterateEntities().forEach { entity ->
						if (entity !is PokemonEntity || !entity.pokemon.shiny) {
							return@forEach
						}
						val nearestPlayer = players.minByOrNull { player ->
							player.squaredDistanceTo(entity.pos)
						}
						val entityCheck = !shinyPokemon.contains(entity.uuid)

						if (nearestPlayer != null && nearestPlayer.squaredDistanceTo(entity.pos) <= maxDistance * maxDistance) {
							if (entity.isBattling && entityCheck) {
								playShineEffectForPlayer(entity)
								playSparkleEffectForPlayer(entity)
								shinySoundEffectForPlayer(entity)
								shinedInBattle.add(entity.uuid)
								shinyPokemon.add(entity.uuid)
							} else if (entity.ownerUuid != null && entityCheck) {
								playShineEffectForPlayer(entity)
								playSparkleEffectForPlayer(entity)
								shinySoundEffectForPlayer(entity)
								shinedInBattle.remove(entity.uuid)
								shinyPokemon.add(entity.uuid)
							} else if (entityCheck) {
								playWildStarEffectForPlayer(entity)
								playWildSparkleEffectForPlayer(entity)
								wildShinySoundEffectForPlayer(entity)
								shinedInBattle.remove(entity.uuid)
								shinyPokemon.add(entity.uuid)
							}
						}
					}
				}
			}
		}
	}

	private fun wildShinySoundEffectForPlayer(pokemonEntity: PokemonEntity) {
		// Define the sound to play
		val soundIdentifier = Identifier("cobbled-shiny-particles", "shiny")
		val soundEvent : SoundEvent = SoundEvent.of(soundIdentifier)
		// Play a sound at the center of the shiny Pokémon's hitbox for the player
		pokemonEntity.playSound(soundEvent, 2.4f, 1.0f)
	}


	private fun shinySoundEffectForPlayer(pokemonEntity: PokemonEntity) {
		// Define the sound to play
		val soundIdentifier = Identifier("cobbled-shiny-particles", "shiny_owned")
		val soundEvent : SoundEvent = SoundEvent.of(soundIdentifier)
		// Play a sound at the center of the shiny Pokémon's hitbox for the player
		pokemonEntity.playSound(soundEvent, 2.4f, 1.0f)
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

	private fun playShineEffectForPlayer(shinyEntity: Entity) {
		hitboxDetector(shinyEntity, "shine")
	}

	private fun playSparkleEffectForPlayer(shinyEntity: Entity) {
		hitboxDetector(shinyEntity, "sparkle")
	}

	private fun hitboxDetector(entity: Entity, particle: String ) {
		val hitboxCenter = entity.boundingBox.center
		val hitbox = entity.boundingBox
		val hitboxVolume = (hitbox.maxX - hitbox.minX) * (hitbox.maxY - hitbox.minY) * (hitbox.maxZ - hitbox.minZ)
		val smallVolumeThreshold = 1.0
		val mediumVolumeThreshold = 2.0
		val particleIdentifier = when {
			hitboxVolume <= smallVolumeThreshold -> Identifier("cobblemon:shiny_${particle}_small")
			hitboxVolume <= mediumVolumeThreshold -> Identifier("cobblemon:shiny_${particle}_medium")
			entity.name.string == "Wailord" -> Identifier("cobblemon:shiny_${particle}_wailord")
			entity.name.string == "Wishiwashi" -> Identifier("cobblemon:shiny_${particle}_wailord")
			else -> Identifier("cobblemon:shiny_${particle}_large")
		}
		val spawnSnowstormParticlePacket = SpawnSnowstormParticlePacket(particleIdentifier, hitboxCenter)
		spawnSnowstormParticlePacket.sendToPlayersAround(entity.x, entity.y, entity.z, 50.0, entity.world.registryKey)
	}
}
