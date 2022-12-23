import io.iohk.atala.prism.enterprisesdk.ConnectClientJVM
import io.iohk.atala.prism.enterprisesdk.IssueClientJVM
import io.iohk.atala.prism.enterprisesdk.ManageClientJVM
import io.iohk.atala.prism.enterprisesdk.model.ConnectionInvitationsRequest
import io.iohk.atala.prism.enterprisesdk.model.ConnectionsRequest
import io.iohk.atala.prism.enterprisesdk.model.CreateCredentialOfferRequest
import io.iohk.atala.prism.enterprisesdk.model.CreateCredentialSchemaRequest
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import utils.*

val holderCC = ConnectClientJVM(Constants.HOLDER_AGENT)
val holderIC = IssueClientJVM(Constants.HOLDER_AGENT)

val issuerCC = ConnectClientJVM(Constants.ISSUER_AGENT)
val issuerIC = IssueClientJVM(Constants.ISSUER_AGENT)
val issuerMC = ManageClientJVM(Constants.ISSUER_AGENT)

class CreateSchema : Subcommand("schema", "Create a schema") {
    override fun execute() {
        // Issuer creates a schema
        val schemaRequest = CreateCredentialSchemaRequest(
            "Discord-credential",
            "0.1",
            "Discord social credential",
            arrayOf("id", "name", "discriminator", "user", "created_at"),
            arrayOf()
        )
        val schemaResponse = runBlocking { issuerMC.createSchema(schemaRequest) }
        println("--> Schema id: ${schemaResponse.id}")
    }
}

class CreateConnection : Subcommand("connect", "Create a connection") {
    override fun execute() {
        // Issuer creates an invitation
        val invitationRequest = ConnectionsRequest("test")
        val invitationResponse = runBlocking { issuerCC.createConnection(invitationRequest) }
        println("--> Issuer connection: ${invitationResponse.connectionId}")

        // Holder accepts the connection invitation
        val invitation = invitationResponse.invitation.invitationUrl.substringAfter("=")
        val acceptInvitationRequest = ConnectionInvitationsRequest(invitation)
        val acceptInvitationResponse = runBlocking { holderCC.connectionInvitations(acceptInvitationRequest) }
        println("--> Holder connection: ${acceptInvitationResponse.connectionId}")

        waitForConnection(issuerCC, invitationResponse.connectionId)

        val connection = runBlocking { issuerCC.getConnectionById(invitationResponse.connectionId) }
        val theirDid = connection.theirDid
        println("\n--> Their DID: ${connection.theirDid} ${connection.state}\n")
    }
}

class IssueCredential : Subcommand("issue", "Issue a credential") {
    private val subjectId by argument(ArgType.String, "Holder DID", "Holder DID uri")
    override fun execute() {
        // Issuer creates a credential offer
        val claims = mutableMapOf<String, String>()
        claims.put("id", "393839282")
        claims.put("name", "Essbante")
        claims.put("discriminator", "6926")
        claims.put("user", "Essbante#6926")
        claims.put("created_at", "Mar 08 2021")

        val credentialOfferRequest =
            CreateCredentialOfferRequest(
                description = "Discord credential",
                schemaId = null,
                subjectId = subjectId!!,
                validityPeriod = 3600,
                claims = claims,
                automaticIssuance = true,
                awaitConfirmation = true
            )
        try {
            val credentialOffer = runBlocking { issuerIC.createCredentialOffer(credentialOfferRequest) }
            println("--> Credential record id: ${credentialOffer.recordId}")
        } catch (e: Throwable) {
            println("Credential offer failed")
            println(e.message)
        }

        // Holder waits for the credential offer
        waitForCredentialOffer(holderIC)

        // Holder accepts the credential offer
        acceptAllOffers(holderIC)
    }
}

fun main(args: Array<String>) {
    val parser = ArgParser("discord-controller")
    parser.subcommands(CreateSchema(), CreateConnection(), IssueCredential())
    parser.parse(args)
}
