package models
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Task(
    val taskId: String,
    val discordUser: DiscordUser,
    val connectionId: String,
    var invitation: String? = null,
    @Contextual
    var credentialRecordId: UUID? = null,
    var state: String = TaskState.INVITATION_GENERATED,
    var theirDid: String? = null,
    var jwtCredential: String? = null
)

object TaskState {
    // Issuer generated an invitation but has not been accepted by the user
    const val INVITATION_GENERATED = "InvitationGenerated"

    // Issuer has received a connection request from the user, the connection is stablished
    const val CONNECTION_STABLISHED = "ConnectionStablished"
    const val CREDENTIAL_OFFER_SENT = "CredentialOfferSent"
    const val CREDENTIAL_ISSUED = "CredentialIssued"
}


