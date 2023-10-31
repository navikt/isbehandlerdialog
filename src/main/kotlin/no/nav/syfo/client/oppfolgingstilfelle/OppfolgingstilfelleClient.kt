package no.nav.syfo.client.oppfolgingstilfelle

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory
import java.util.UUID

class OppfolgingstilfelleClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault()
) {
    private val personOppfolgingstilfelleSystemUrl: String =
        "${clientEnvironment.baseUrl}$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_SYSTEM_PERSON_PATH"

    suspend fun getOppfolgingstilfelle(
        personIdent: PersonIdent,
    ): Oppfolgingstilfelle? {
        val callId = UUID.randomUUID().toString()
        val azureToken = azureAdClient.getSystemToken(clientEnvironment.clientId)?.accessToken
            ?: throw RuntimeException("Failed to get azure token")

        return try {
            val response: HttpResponse = httpClient.get(personOppfolgingstilfelleSystemUrl) {
                header(HttpHeaders.Authorization, bearerHeader(azureToken))
                header(NAV_CALL_ID_HEADER, callId)
                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                accept(ContentType.Application.Json)
            }
            response.body<OppfolgingstilfellePersonDTO>().toLatestOppfolgingstilfelle().also {
                COUNT_CALL_OPPFOLGINGSTILFELLE_PERSON_SUCCESS.increment()
            }
        } catch (responseException: ResponseException) {
            log.error(
                "Error while requesting OppfolgingstilfellePerson from Isoppfolgingstilfelle with {}, {}",
                StructuredArguments.keyValue("statusCode", responseException.response.status.value),
                callIdArgument(callId),
            )
            COUNT_CALL_OPPFOLGINGSTILFELLE_PERSON_FAIL.increment()
            null
        }
    }

    companion object {
        const val ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_SYSTEM_PERSON_PATH =
            "/api/system/v1/oppfolgingstilfelle/personident"

        private val log = LoggerFactory.getLogger(OppfolgingstilfelleClient::class.java)
    }
}
