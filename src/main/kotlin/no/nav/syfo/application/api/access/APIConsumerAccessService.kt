package no.nav.syfo.application.api.access

import com.auth0.jwt.JWT
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.util.JWT_CLAIM_AZP
import no.nav.syfo.util.configuredJacksonMapper

private val mapper = configuredJacksonMapper()

class APIConsumerAccessService(
    azureAppPreAuthorizedApps: String
) {
    private val preAuthorizedClients: List<PreAuthorizedClient> = mapper.readValue(azureAppPreAuthorizedApps)

    fun validateConsumerApplicationAZP(
        token: String,
        authorizedApps: List<String>,
    ) {
        val consumerClientIdAzp: String = JWT.decode(token).claims[JWT_CLAIM_AZP]?.asString()
            ?: throw IllegalArgumentException("Claim AZP was not found in token")
        val preAuthorizedClientIds = preAuthorizedClients
            .filter { authorizedApps.contains(it.getAppnavn()) }
            .map { it.clientId }
        if (!preAuthorizedClientIds.contains(consumerClientIdAzp)) {
            throw ForbiddenAccessSystemConsumer(consumerClientIdAzp = consumerClientIdAzp)
        }
    }
}

data class PreAuthorizedClient(
    val name: String,
    val clientId: String
) {
    fun getAppnavn(): String {
        val split = name.split(":")
        return split[2]
    }
}
