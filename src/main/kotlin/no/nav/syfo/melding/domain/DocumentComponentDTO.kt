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

fun List<DocumentComponentDTO>.serialize(): String {
    return buildString {
        this@serialize.forEach { documentComponentDTO ->
            documentComponentDTO.title?.let {
                appendLine(it)
            }
            documentComponentDTO.texts.forEach {
                appendLine(it)
            }
            appendLine()
        }
    }
}
