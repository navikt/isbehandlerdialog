package no.nav.syfo.application

import no.nav.syfo.infrastructure.client.padm2.VedleggDTO

interface IPadm2Client {
    suspend fun hentVedlegg(msgId: String): List<VedleggDTO>
}
