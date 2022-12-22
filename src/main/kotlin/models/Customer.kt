package models

import kotlinx.serialization.Serializable

@Serializable
data class Customer(val id: String, val firstName: String, val lastName: String, val email: String)

// TODO: this is the "database"
val customerStorage = mutableListOf<Customer>()