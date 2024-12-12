package no.nav.syfo.util

import io.ktor.server.routing.*
import net.logstash.logback.argument.StructuredArguments

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
const val NAV_PERSONIDENT_HEADER = "nav-personident"

fun bearerHeader(token: String) = "Bearer $token"

fun RoutingContext.getCallId(): String {
    return this.call.getCallId()
}
fun callIdArgument(callId: String) = StructuredArguments.keyValue("callId", callId)!!
