package utils

import io.iohk.atala.prism.enterprisesdk.ConnectClientJVM
import io.iohk.atala.prism.enterprisesdk.IssueClientJVM
import kotlinx.coroutines.runBlocking

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

fun waitForConnection(connectClient: ConnectClientJVM, connectionId: String) {
    var connection = runBlocking { connectClient.getConnectionById(connectionId) }

    while (connection.theirDid.isNullOrEmpty()) {
        println("Waiting for connection: $connectionId\n state:${connection.state}")
        Thread.sleep(1000)
        connection = runBlocking { connectClient.getConnectionById(connectionId) }
    }
    println("Connected: $connectionId\n state:${connection.state}")
}

fun waitForCredentialOffer(issueClient: IssueClientJVM) {
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

fun acceptAllOffers(issueClient: IssueClientJVM) {
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
