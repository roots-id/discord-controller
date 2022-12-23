package models
import io.iohk.atala.prism.enterprisesdk.model.ConnectionsResponse
import kotlinx.serialization.Serializable

@Serializable
data class WorkflowState(
    val discordUser: DiscordUser,
    val connectionId: String,
    val recordId: String? = null
)

// TODO: this is the "database"
val workflowStateStorage = mutableListOf<WorkflowState>()
