package models

import kotlinx.serialization.Serializable

@Serializable
data class DiscordUser(
    val identifier: String,
    val name: String,
    val discriminator: String,
    val user: String,
    val created_at: String,
    val email: String? = null
)

