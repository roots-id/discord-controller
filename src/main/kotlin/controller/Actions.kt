package controller

import kotlinx.coroutines.runBlocking
import models.DiscordUser
import models.Task
import org.openapitools.client.apis.ConnectionsManagementApi
import org.openapitools.client.apis.DIDRegistrarApi
import org.openapitools.client.apis.IssueCredentialsProtocolApi
import org.openapitools.client.models.*

fun printConnections(connectionApi: ConnectionsManagementApi) {
    runBlocking {
        for (connection in connectionApi.getConnections().contents) {
            println("\nConnectionId: ${connection.connectionId}")
            println("MyDID:          ${connection.myDid}")
            println("TheirDID:       ${connection.theirDid}")
            println("Label:          ${connection.label}")
            println("State:          ${connection.state}")
            println("created:        ${connection.createdAt}")
            println("updated:        ${connection.updatedAt}")
            println("kind:           ${connection.kind}")
            println("self:           ${connection.self}")
        }
    }
}

// Function to split a string after the first occurrence of "="
fun String.substringAfter(delimiter: String): String {
    val index = indexOf(delimiter)
    return if (index == -1) this else substring(index + delimiter.length)
}

fun waitForConnection(connectionApi: ConnectionsManagementApi, connectionId: String) {
    var connection = runBlocking { connectionApi.getConnection(connectionId) }

    while (connection.theirDid.isNullOrEmpty()) {
        println("Waiting for connection: $connectionId\n state:${connection.state}")
        Thread.sleep(1000)
        connection = runBlocking { connectionApi.getConnection(connectionId) }
    }
    println("Connected: $connectionId\n state:${connection.state}")
}

fun waitForCredentialOffer(issueApi: IssueCredentialsProtocolApi) {
    while (true) {
        println("Waiting for credential offers")
        for (offer in runBlocking { issueApi.getCredentialRecords().contents }) {
            if (offer.role == IssueCredentialRecord.Role.holder &&
                offer.protocolState == IssueCredentialRecord.ProtocolState.offerReceived
            ) {
                return
            }
        }
        Thread.sleep(1000)
    }
}

// fun acceptAllOffers(issueApi: IssueCredentialsProtocolApi) {
//    println("--> Accept all offers...")
//    runBlocking {
//        for (offer in issueApi.getCredentialRecords().contents) {
//            println("\nOfferId: ${offer.recordId} state: ${offer.protocolState}")
//            if (offer.role == IssueCredentialRecord.Role.holder &&
//                offer.protocolState == IssueCredentialRecord.ProtocolState.offerReceived
//            ) {
//                issueApi.acceptCredentialOffer(offer.recordId)
//            }
//        }
//    }
// }

// fun registerDiscordSchema(schemaApi: SchemaRegistryApi) {
//    val schema = VerificationCredentialSchemaInput(
//        "discord",
//        "1.0",
//        UUID.randomUUID(),
//        "Discord user schema",
//        listOf("identifier", "name", "discriminator", "user", "created_at"),
//        OffsetDateTime.now()
//    )
//    val response = runBlocking {
//        schemaApi.createSchema(schema)
//    }
// }

fun createInvitation(connectionApi: ConnectionsManagementApi, discordUser: DiscordUser): Connection {
    val request = CreateConnectionRequest(discordUser.user)
    val connection = runBlocking { connectionApi.createConnection(request) }
    println("--> Issuer connection: ${connection.connectionId}")
    return connection
}

fun createPrismDid(didRegistrarApi: DIDRegistrarApi): String {
    val keysTemplate = listOf<ManagedDIDKeyTemplate>(
        ManagedDIDKeyTemplate("key1", ManagedDIDKeyTemplate.Purpose.assertionMethod),
        ManagedDIDKeyTemplate("key2", ManagedDIDKeyTemplate.Purpose.authentication)
    )
    val services = listOf<Service>()
    val docTemplate = CreateManagedDidRequestDocumentTemplate(keysTemplate, services)
    val managedDidRequest = CreateManagedDidRequest(docTemplate)
    val did = runBlocking { didRegistrarApi.createManagedDid(managedDidRequest) }
    return did.longFormDid
}

fun issueCredential(issueApi: IssueCredentialsProtocolApi, task: Task): IssueCredentialRecord {
    val claims = mutableMapOf<String, String>()
    claims.put("identifier", task.discordUser.identifier)
    claims.put("name", task.discordUser.name)
    claims.put("discriminator", task.discordUser.discriminator)
    claims.put("user", task.discordUser.user)
    claims.put("created_at", task.discordUser.created_at)

    val request = CreateIssueCredentialRecordRequest(
        claims,
        task.theirDid!!,
        task.connectionId,
        automaticIssuance = true
    )
    val credentialRecord = runBlocking { issueApi.createCredentialOffer(request) }
    println("--> Issued credential: ${credentialRecord.recordId}")
    return credentialRecord
}
