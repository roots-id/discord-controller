package plugins

import routes.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        authenticate("auth-bearer") {
            invitation()
            workflow()
            customerRouting()
        }
    }
}