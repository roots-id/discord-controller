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

val issuerAgent = "http://localhost:8080/prism-agent"
val holderAgent = "http://localhost:8090/prism-agent"
val verifierAgent = "http://localhost:9000/prism-agent"

val holderCC = ConnectClientJVM(holderAgent)
val holderIC = IssueClientJVM(holderAgent)

val issuerCC = ConnectClientJVM(issuerAgent)
val issuerIC = IssueClientJVM(issuerAgent)
val issuerMC = ManageClientJVM(issuerAgent)

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

fun printConnections(client: ConnectClientJVM) {
    runBlocking {
        for (connection in client.getConnections().contents) {
            println("\nConnectionId: ${connection.connectionId}")
            println("MyDID:        ${connection.myDid}")
            println("TheirDID:     ${connection.theirDid}")
            println("Label:        ${connection.label}")
            println("State:        ${connection.state}")
            println("created:      ${connection.createdAt}")
            println("updated:      ${connection.updatedAt}")
            println("kind:         ${connection.kind}")
            println("self:         ${connection.self}")
        }
    }
}

// Function to split a string after the first occurrence of "="
fun String.substringAfter(delimiter: String): String {
    val index = indexOf(delimiter)
    return if (index == -1) this else substring(index + delimiter.length)
}

private fun waitForConnection(connectClient: ConnectClientJVM, connectionId: String) {
    var connection = runBlocking { connectClient.getConnectionById(connectionId) }

    while (connection.theirDid.isNullOrEmpty()) {
        println("Waiting for connection: $connectionId\n state:${connection.state}")
        Thread.sleep(1000)
        connection = runBlocking { connectClient.getConnectionById(connectionId) }
    }
    println("Connected: $connectionId\n state:${connection.state}")
}

private fun waitForCredentialOffer(issueClient: IssueClientJVM) {
    var wait = true
    while (wait) {
        println("Waiting for credential offers")
        for (offer in runBlocking { issueClient.getCredentialRecords().items }) {
            if (offer.role == "Holder" && offer.protocolState == "OfferReceived") {
                wait = false
                return
            }
        }
        Thread.sleep(1000)
    }
}

private fun acceptAllOffers(issueClient: IssueClientJVM) {
    println("--> Accept all offers...")
    runBlocking {
        for (offer in issueClient.getCredentialRecords().items) {
            println("\nOfferId: ${offer.recordId} state: ${offer.protocolState}")
            if (offer.role == "Holder" && offer.protocolState == "OfferReceived") {
                issueClient.acceptOffer(offer.recordId)
            }
        }
    }
}
