package no.nav.syfo.testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.client.dialogmelding.BehandlerDTO
import no.nav.syfo.testhelper.UserConstants

val mockBehandlerDTO = BehandlerDTO(
    behandlerRef = UserConstants.BEHANDLER_REF,
    kategori = "LE",
    fnr = null,
    hprId = UserConstants.HPRID,
    fornavn = "Fornavn",
    mellomnavn = null,
    etternavn = "Etternavn",
    orgnummer = null,
    kontor = null,
    adresse = null,
    postnummer = null,
    poststed = null,
    telefon = null,
)

fun MockRequestHandleScope.getBehandlerResponse(request: HttpRequestData): HttpResponseData =
    respond(mockBehandlerDTO)
