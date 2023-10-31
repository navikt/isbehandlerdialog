package no.nav.syfo.client.oppfolgingstilfelle

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.util.*
import org.slf4j.LoggerFactory
import java.util.UUID

class OppfolgingstilfelleClient(
    private val azureAdV2Client: AzureAdClient,
    private val isoppfolgingstilfelleClientId: String,
    isoppfolgingstilfelleBaseUrl: String,
) {
    private val personOppfolgingstilfelleUrl: String =
        "$isoppfolgingstilfelleBaseUrl$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH"
    private val personOppfolgingstilfelleSystemUrl: String =
        "$isoppfolgingstilfelleBaseUrl$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_SYSTEM_PERSON_PATH"
    private val oppfolgingstilfelleNLUrl: String =
        "$isoppfolgingstilfelleBaseUrl$ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_NARMESTELEDER_PATH"
    private val CACHE_OPPFOLGINGSTILFELLE_NL_KEY_PREFIX = "oppfolgingstilfelle-nl-"

    private val httpClient = httpClientDefault()

    suspend fun oppfolgingstilfellePerson(
        personIdent: PersonIdent,
        token: String,
        callId: String? = null,
    ): Oppfolgingstilfelle? =
        oppfolgingstilfelle(
            personIdent = personIdent,
            path = personOppfolgingstilfelleUrl,
            token = token,
            callId = callId,
        )

    suspend fun oppfolgingstilfelleSystem(
        personIdent: PersonIdent,
    ): Oppfolgingstilfelle? =
        oppfolgingstilfelle(
            personIdent = personIdent,
            path = personOppfolgingstilfelleSystemUrl,
        )

    private suspend fun oppfolgingstilfelle(
        personIdent: PersonIdent,
        path: String,
        token: String? = null,
        callId: String? = null,
    ): Oppfolgingstilfelle? {
        val callIdToUse = callId ?: UUID.randomUUID().toString()
        return try {
            val response: HttpResponse = httpClient.get(path) {
                header(HttpHeaders.Authorization, bearerHeader(getAzureAccessToken(token)))
                header(NAV_CALL_ID_HEADER, callIdToUse)
                header(NAV_PERSONIDENT_HEADER, personIdent.value)
                accept(ContentType.Application.Json)
            }
            val oppfolgingstilfellePerson = response.body<OppfolgingstilfellePersonDTO>()
                .toLatestOppfolgingstilfelle()
            COUNT_CALL_OPPFOLGINGSTILFELLE_PERSON_SUCCESS.increment()
            oppfolgingstilfellePerson
        } catch (responseException: ResponseException) {
            log.error(
                "Error while requesting OppfolgingstilfellePerson from Isoppfolgingstilfelle with {}, {}",
                StructuredArguments.keyValue("statusCode", responseException.response.status.value),
                callIdArgument(callIdToUse),
            )
            COUNT_CALL_OPPFOLGINGSTILFELLE_PERSON_FAIL.increment()
            null
        }
    }

    suspend fun oppfolgingstilfelleTokenx(
        arbeidstakerPersonIdentNumber: PersonIdent,
        narmesteLederPersonIdentNumber: PersonIdent,
        tokenx: String,
        virksomhetsnummer: Virksomhetsnummer,
        callId: String,
    ): List<Oppfolgingstilfelle>? {
        val oppfolgingstilfelleNLCacheKey =
            "$CACHE_OPPFOLGINGSTILFELLE_NL_KEY_PREFIX$narmesteLederPersonIdentNumber-$arbeidstakerPersonIdentNumber-$virksomhetsnummer"

        val cachedOppfolgingstilfelle = cache.getListObject<Oppfolgingstilfelle>(oppfolgingstilfelleNLCacheKey)

        return if (cachedOppfolgingstilfelle != null) {
            cachedOppfolgingstilfelle
        } else {
            val exchangedToken = tokendingsClient.getOnBehalfOfToken(
                scopeClientId = isoppfolgingstilfelleClientId,
                token = tokenx,
            ).accessToken

            try {
                val response: HttpResponse = httpClient.get(oppfolgingstilfelleNLUrl) {
                    header(HttpHeaders.Authorization, bearerHeader(exchangedToken))
                    header(NAV_CALL_ID_HEADER, callId)
                    header(NAV_PERSONIDENT_HEADER, arbeidstakerPersonIdentNumber.value)
                    header(NAV_VIRKSOMHETSNUMMER, virksomhetsnummer.value)
                    accept(ContentType.Application.Json)
                }
                val parsedResponse = response.body<List<OppfolgingstilfelleDTO>>().toOppfolgingstilfelle()
                cache.setObject(
                    oppfolgingstilfelleNLCacheKey,
                    parsedResponse,
                    CACHE_NARMESTE_LEDER_EXPIRE_SECONDS
                )
                parsedResponse
            } catch (responseException: ResponseException) {
                log.error(
                    "Error while requesting four months date from Isoppfolgingstilfelle with {}, {}",
                    StructuredArguments.keyValue("statusCode", responseException.response.status.value),
                    callIdArgument(callId),
                )
                null
            }
        }
    }

    private suspend fun getAzureAccessToken(token: String? = null) =
        if (token != null) {
            azureAdV2Client.getOnBehalfOfToken(
                scopeClientId = isoppfolgingstilfelleClientId,
                token = token,
            )
        } else {
            azureAdV2Client.getSystemToken(isoppfolgingstilfelleClientId)
        }?.accessToken ?: throw RuntimeException("Failed to get azure token")

    companion object {
        const val ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_PERSON_PATH =
            "/api/internad/v1/oppfolgingstilfelle/personident"
        const val ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_SYSTEM_PERSON_PATH =
            "/api/system/v1/oppfolgingstilfelle/personident"
        const val ISOPPFOLGINGSTILFELLE_OPPFOLGINGSTILFELLE_NARMESTELEDER_PATH =
            "/api/v1/narmesteleder/oppfolgingstilfelle"

        private val log = LoggerFactory.getLogger(OppfolgingstilfelleClient::class.java)
        const val CACHE_NARMESTE_LEDER_EXPIRE_SECONDS = 3600L
    }
}
