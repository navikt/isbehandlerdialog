package no.nav.syfo.melding.kafka.legeerklaring

import java.util.Base64

data class LegeerklaringVedleggDTO(
    val vedlegg: Vedlegg,
)

fun LegeerklaringVedleggDTO.getBytes() =
    Base64.getMimeDecoder().decode(vedlegg.content.content)

data class Vedlegg(
    val content: Content,
    val type: String,
    val description: String,
)

data class Content(
    val contentType: String,
    val content: String,
)
