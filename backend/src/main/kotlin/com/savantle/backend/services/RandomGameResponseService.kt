package com.savantle.backend.services

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class RandomGameResponseService(private val randomGameService: RandomGameService) {
    companion object {
        private const val MAX_PLAYER_NAME_LENGTH = 100
        private val UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }

    fun createRandomGame(): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(randomGameService.createRandomGame())
        } catch (e: IllegalStateException) {
            ResponseEntity.status(503).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to create random game"))
        }
    }

    fun getRandomGameScreenshot(gameId: String): ResponseEntity<ByteArray> {
        if (!UUID_REGEX.matches(gameId)) return ResponseEntity.badRequest().build()
        return try {
            val bytes = randomGameService.getScreenshot(gameId)
            ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    fun validateRandomGuess(
        gameId: String,
        playerName: String,
        guessNumber: Int,
    ): ResponseEntity<Any> {
        if (!UUID_REGEX.matches(gameId)) return ResponseEntity.badRequest().body(mapOf("error" to "Invalid game ID"))
        if (playerName.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to "Player name required"))
        if (playerName.length > MAX_PLAYER_NAME_LENGTH) return ResponseEntity.badRequest().body(mapOf("error" to "Player name too long"))
        if (guessNumber < 1 || guessNumber > 5) return ResponseEntity.badRequest().body(mapOf("error" to "Invalid guess number"))
        return try {
            ResponseEntity.ok(randomGameService.validateGuess(gameId, playerName, guessNumber))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to process guess"))
        }
    }
}
