package plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import routes.*

fun Application.configureRouting() {
    routing {
        authenticate("auth-bearer") {
            invitation()
            task()
            polling()
        }
    }
}
