package routes

import controller.createInvitation
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import models.*
import org.openapitools.client.apis.ConnectionsManagementApi
import org.openapitools.client.apis.IssueCredentialsProtocolApi
import org.openapitools.client.models.Connection.State
import org.openapitools.client.models.CreateIssueCredentialRecordRequest
import org.openapitools.client.models.IssueCredentialRecord
import utils.Constants
import utils.createHttpClient
import java.util.*

val key = System.getenv(Constants.ISSUER_APIKEY) ?: ""
val url = System.getenv(Constants.ISSUER_URL) ?: Constants.ISSUER_URL_DEFAULT
val client = createHttpClient(key)
val connectionApi = ConnectionsManagementApi(url, client)
val issueCredentialApi = IssueCredentialsProtocolApi(url, client)

var pollingJob: Job? = null

fun Route.invitation() {
    post("/invitation") {
        println("Received invitation request")
        val discordUser = call.receive<DiscordUser>()
        val connection = createInvitation(connectionApi, discordUser)
        val task = Task(UUID.randomUUID().toString(), discordUser, connection.connectionId.toString())
        taskStorage.add(task)
        print(task)
        call.respond(connection)
    }
}

fun Route.task() {
    get("/task") {
        if (taskStorage.isNotEmpty()) {
            call.respond(taskStorage)
        } else {
            call.respondText("No tasks found", status = HttpStatusCode.OK)
        }
    }
}

fun issueCredential(task: Task) {
    val connection = connectionApi.getConnection(task.connectionId)
    // Issuer creates a credential offer
    val claims = mutableMapOf<String, String>()
    claims.put("identifier", task.discordUser.identifier)
    claims.put("name", task.discordUser.name)
    claims.put("discriminator", task.discordUser.discriminator)
    claims.put("user", task.discordUser.user)
    claims.put("created_at", task.discordUser.created_at)

    val credentialOfferRequest =
        CreateIssueCredentialRecordRequest(
            subjectId = connection.theirDid!!,
            claims = claims,
            automaticIssuance = true,
            awaitConfirmation = false
        )
    try {
        val credentialOffer = runBlocking { issueCredentialApi.createCredentialOffer(credentialOfferRequest) }
        task.credentialRecordId = credentialOffer.recordId
        task.state = TaskState.CREDENTIAL_OFFER_SENT
        println("--> Credential record id: ${credentialOffer.recordId}")
    } catch (e: Throwable) {
        println("Credential offer failed")
        println(e.message)
    }
}

fun startPolling(interval: Long) {
    pollingJob = GlobalScope.launch {
        while (true) {
            // Perform API polling here
            println("Polling... ${Date()}")

            // loop through all tasks and select the ones that are in state "InvitationGenerated"
            // or "ConnectionResponseSent"
            val tasks = taskStorage.filter {
                it.state == TaskState.INVITATION_GENERATED ||
                    it.state == TaskState.CONNECTION_STABLISHED ||
                    it.state == TaskState.CREDENTIAL_OFFER_SENT
            }
            // for each one of the tasks, use the connectionId to check the status of the connection in the agent
            // and update the task state accordingly
            tasks.forEach { task ->
                when (task.state) {
                    TaskState.INVITATION_GENERATED -> {
                        val connection = connectionApi.getConnection(task.connectionId)
                        if (connection.state == State.connectionResponseSent) {
                            task.state = TaskState.CONNECTION_STABLISHED
                            println("${task.taskId} - Connection established")
                        }
                    }
                    TaskState.CONNECTION_STABLISHED -> {
                        println("${task.taskId} - Credential offer sent")
                        issueCredential(task)
                    }
                    TaskState.CREDENTIAL_OFFER_SENT -> {
                        val credential = issueCredentialApi.getCredentialRecord(task.credentialRecordId!!)
                        if (credential.protocolState == IssueCredentialRecord.ProtocolState.credentialSent) {
                            task.state = TaskState.CREDENTIAL_ISSUED
                            task.jwtCredential = credential.jwtCredential!!
                            println("${task.taskId} - Credential issued")
                        }
                    }
                }
            }
            // Sleep for the specified interval
            delay(interval)
        }
    }
}

fun stopPolling() {
    pollingJob?.cancel()
}

fun Route.polling() {
    route("/polling") {
        post("/start/{interval?}") {
            val interval = call.parameters["interval"]?.toLong() ?: 5000L
            // if job is already running, throw an error
            if (pollingJob?.isActive == true) {
                call.respondText("Polling already running", status = HttpStatusCode.BadRequest)
            }
            // if interval is less than 5000, throw an error
            else if (interval < 5000) {
                call.respondText("Interval must be greater than 5000", status = HttpStatusCode.BadRequest)
            } else {
                startPolling(interval)
                call.respondText("Polling started", status = HttpStatusCode.OK)
            }
        }
        post("/stop") {
            // if job is not running, throw an error
            if (pollingJob?.isActive == false) {
                call.respondText("Polling not running", status = HttpStatusCode.BadRequest)
            } else {
                stopPolling()
                call.respondText("Polling stopped", status = HttpStatusCode.OK)
            }
        }
    }
}

// fun Route.customerRouting() {
//    route("/customer") {
//        get {
//            if (discordUserStorage.isNotEmpty()) {
//                call.respond(discordUserStorage)
//            } else {
//                call.respondText("No users found", status = HttpStatusCode.OK)
//            }
//        }
//        get("{id?}") {
//            val id = call.parameters["id"] ?: return@get call.respondText(
//                "Missing id",
//                status = HttpStatusCode.BadRequest
//            )
//            val customer =
//                discordUserStorage.find { it.identifier == id } ?: return@get call.respondText(
//                    "No user with id $id",
//                    status = HttpStatusCode.NotFound
//                )
//            call.respond(customer)
//        }
//        post {
//            try {
//                val customer = call.receive<Customer>()
//                customerStorage.add(customer)
//                call.respondText("User stored correctly", status = HttpStatusCode.Created)
//            } catch (e: Exception) {
//                call.respondText("Error: ${e.cause?.message}", status = HttpStatusCode.BadRequest)
//            }
//        }
//        delete("{id?}") {
//            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
//            if (discordUserStorage.removeIf { it.identifier == id }) {
//                call.respondText("User removed correctly", status = HttpStatusCode.Accepted)
//            } else {
//                call.respondText("Not Found", status = HttpStatusCode.NotFound)
//            }
//        }
//    }
// }
