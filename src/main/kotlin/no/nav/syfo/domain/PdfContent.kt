package no.nav.syfo.domain

import io.ktor.http.*
import io.ktor.http.content.*

class PdfContent(
    val pdf: ByteArray,
    override val contentType: ContentType = ContentType.Application.Pdf,
) : OutgoingContent.ByteArrayContent() {
    override fun bytes(): ByteArray = pdf
}
