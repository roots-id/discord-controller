package routes

import com.example.models.Customer
import com.example.models.customerStorage
import io.iohk.atala.prism.enterprisesdk.ConnectClientJVM
import io.iohk.atala.prism.enterprisesdk.model.ConnectionsRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import models.*
import utils.*

fun Route.invitation() {
    val issuerCC = ConnectClientJVM(Constants.ISSUER_AGENT)

    post("/invitation") {
        println("Received invitation request")
        val discordUser = call.receive<DiscordUser>()

        val invitationRequest = ConnectionsRequest(discordUser.user)
        val invitationResponse = runBlocking { issuerCC.createConnection(invitationRequest) }
        println("--> Issuer connection: ${invitationResponse.connectionId}")
        val workflowState = WorkflowState(discordUser, invitationResponse.connectionId)
        workflowStateStorage.add(workflowState)

        call.respond(invitationResponse)
    }
}

fun Route.workflow() {
    get("/workflow") {
        if (workflowStateStorage.isNotEmpty()) {
            call.respond(workflowStateStorage)
        } else {
            call.respondText("No workflows found", status = HttpStatusCode.OK)
        }
    }
}
fun Route.customerRouting() {
    route("/customer") {
        get {
            if (discordUserStorage.isNotEmpty()) {
                call.respond(discordUserStorage)
            } else {
                call.respondText("No users found", status = HttpStatusCode.OK)
            }
        }
        get("{id?}") {
            val id = call.parameters["id"] ?: return@get call.respondText(
                "Missing id",
                status = HttpStatusCode.BadRequest
            )
            val customer =
                discordUserStorage.find { it.identifier == id } ?: return@get call.respondText(
                    "No user with id $id",
                    status = HttpStatusCode.NotFound
                )
            call.respond(customer)
        }
        post {
            try {
                val customer = call.receive<Customer>()
                customerStorage.add(customer)
                call.respondText("User stored correctly", status = HttpStatusCode.Created)
            } catch (e: Exception) {
                call.respondText("Error: ${e.cause?.message}", status = HttpStatusCode.BadRequest)
            }
        }
        delete("{id?}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            if (discordUserStorage.removeIf { it.identifier == id }) {
                call.respondText("User removed correctly", status = HttpStatusCode.Accepted)
            } else {
                call.respondText("Not Found", status = HttpStatusCode.NotFound)
            }
        }
    }
}
