package no.nav.syfo.melding.domain

import no.nav.syfo.client.pdfgen.sanitizeForPdfGen
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DocumentComponentSpek : Spek({
    describe("serialize") {
        it("returns a string with each text followed by blank line") {
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

            documentComponentDTOS.serialize() shouldBeEqualTo """
                Dialogmelding
                
                Fritekst her
                
                Dette er en standardtekst
                
                
            """.trimIndent()
        }
    }
    describe("sanitizeForPdfGen") {
        it("removes illegal characters") {
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
            documentWithIllegalChar.sanitizeForPdfGen() shouldBeEqualTo expectedDocument
        }
    }
})
