package routes

import controller.createInvitation
import controller.createPrismDid
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.*
import org.openapitools.client.apis.ConnectionsManagementApi
import org.openapitools.client.apis.DIDRegistrarApi
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
val didRegistrarApi = DIDRegistrarApi(url, client)
var pollingJob: Job? = null

// TODO: this is the "database"
val taskStorage = mutableListOf<Task>()
var issuingDid: String? = null

fun Route.invitation() {
    post("/invitation") {
        println("Received invitation request")
        val discordUser = call.receive<DiscordUser>()
        val connection = createInvitation(connectionApi, discordUser)
        val task = Task(
            UUID.randomUUID().toString(),
            discordUser,
            connection.connectionId.toString(),
            connection.invitation.invitationUrl
        )
        taskStorage.add(task)
        print(task)
        call.respond(connection)
    }
}

fun Route.task() {
    post("/task") {
        call.respond(taskStorage)
    }
}

suspend fun agentInfo(): String {
    val client = HttpClient()
    val response: HttpResponse = client.get("$url/_system/health") {
        header("apiKey", key)
    }
    println("Response status: ${response.status}")
    val responseBody = response.bodyAsText()
    val version = Json.parseToJsonElement(responseBody).jsonObject["version"]!!.jsonPrimitive.content
    client.close()
    return version
}
fun Route.info() {
    post("/info") {
        call.respond("Connected to PRISM Agent v${agentInfo()} at $url")
    }
}

fun Route.issuingDid() {
    post("/create_issuing_did") {
        if (issuingDid == null) {
            issuingDid = createPrismDid(didRegistrarApi)
        }
        call.respond("Issuing did: $issuingDid")
    }
}

fun issueCredential(task: Task) {
    // Issuer creates a credential offer
    val claims = mutableMapOf<String, String>()
    claims.put("identifier", task.discordUser.identifier)
    claims.put("name", task.discordUser.name)
    claims.put("discriminator", task.discordUser.discriminator)
    claims.put("user", task.discordUser.user)
    claims.put("created_at", task.discordUser.created_at)
    claims.put("email", task.discordUser.email ?: "")

    val credentialOfferRequest =
        CreateIssueCredentialRecordRequest(
            issuingDID = issuingDid!!,
            connectionId = task.connectionId,
            claims = claims,
            automaticIssuance = true
        )
    try {
        val credentialOffer = runBlocking { issueCredentialApi.createCredentialOffer(credentialOfferRequest) }
        task.credentialRecordId = UUID.fromString(credentialOffer.recordId)
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
