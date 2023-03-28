package no.nav.syfo.melding.domain

data class DocumentComponentDTO(
    val type: DocumentComponentType,
    val key: String? = null,
    val title: String?,
    val texts: List<String>,
)

enum class DocumentComponentType {
    HEADER_H1,
    HEADER_H2,
    PARAGRAPH,
    LINK,
}
