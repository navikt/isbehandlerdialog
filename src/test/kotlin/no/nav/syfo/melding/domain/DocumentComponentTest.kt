package no.nav.syfo.melding.domain

import no.nav.syfo.domain.DocumentComponentDTO
import no.nav.syfo.domain.DocumentComponentType
import no.nav.syfo.domain.serialize
import no.nav.syfo.infrastructure.client.pdfgen.sanitizeForPdfGen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DocumentComponentTest {

    @Test
    fun `returns a string with each text followed by blank line`() {
        val documentComponentDTOS = listOf(
            DocumentComponentDTO(
                type = DocumentComponentType.HEADER_H1,
                title = null,
                texts = listOf("Dialogmelding"),
            ),
            DocumentComponentDTO(
                type = DocumentComponentType.PARAGRAPH,
                title = null,
                texts = listOf("Fritekst her"),
            ),
            DocumentComponentDTO(
                type = DocumentComponentType.PARAGRAPH,
                key = "Standardtekst",
                title = null,
                texts = listOf("Dette er en standardtekst"),
            ),
        )

        assertEquals(
            """
                Dialogmelding
                
                Fritekst her
                
                Dette er en standardtekst
                
                
            """.trimIndent(),
            documentComponentDTOS.serialize()
        )
    }

    @Test
    fun `removes illegal characters`() {
        val documentWithIllegalChar = listOf(
            DocumentComponentDTO(
                type = DocumentComponentType.PARAGRAPH,
                title = "tittel",
                texts = listOf("text1\u0002dsa", "text2"),
            )
        )
        val expectedDocument = listOf(
            DocumentComponentDTO(
                type = DocumentComponentType.PARAGRAPH,
                title = "tittel",
                texts = listOf("text1dsa", "text2"),
            )
        )
        assertEquals(expectedDocument, documentWithIllegalChar.sanitizeForPdfGen())
    }
}
