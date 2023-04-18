package no.nav.syfo.client.padm2

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.util.*

class Padm2Client(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
) {
    private val httpClient = httpClientDefault()
    private val hentVedleggUrl = "${clientEnvironment.baseUrl}$HENT_VEDLEGG_PATH"

    suspend fun hentVedlegg(
        msgId: String,
    ): List<VedleggDTO> {
        val systemToken = azureAdClient.getSystemToken(
            scopeClientId = clientEnvironment.clientId,
        )?.accessToken ?: throw RuntimeException("Failed to fetch vedlegg: Failed to get system token")

        return try {
            val response = httpClient.get("$hentVedleggUrl/$msgId") {
                header(HttpHeaders.Authorization, bearerHeader(systemToken))
                accept(ContentType.Application.Json)
            }
            if (response.status == HttpStatusCode.NoContent) {
                emptyList()
            } else {
                response.body<List<VedleggDTO>>()
            }
        } catch (e: ResponseException) {
            throw RuntimeException("Could not fetch vedlegg for msgId=$msgId", e)
        }
    }

    companion object {
        const val HENT_VEDLEGG_PATH = "/api/system/v1/vedlegg"
    }
}
