package app.morrow.ui.about

import app.morrow.ui.about.LegalBlock.Bullet
import app.morrow.ui.about.LegalBlock.Heading
import app.morrow.ui.about.LegalBlock.Paragraph
import kotlin.test.Test
import kotlin.test.assertEquals

class LegalBlocksTest {

    @Test
    fun headingsBulletsAndParagraphsSplitAndTheTitleIsLeftOut() {
        val text = """
            # Privacy policy

            Morrow keeps
            your alarms.

            ## Location
            - only while open
            - never in the background
            Nothing else.
        """.trimIndent()
        assertEquals(
            listOf(
                Paragraph("Morrow keeps your alarms."),
                Heading("Location"),
                Bullet("only while open"),
                Bullet("never in the background"),
                Paragraph("Nothing else."),
            ),
            legalBlocks(text),
        )
    }

    @Test
    fun aHardWrappedLicenceReflows() {
        val text = "\r\n   Licensed under the Apache License,\r\n   Version 2.0.\r\n\r\n   END OF TERMS\r\n"
        assertEquals(listOf(Paragraph("Licensed under the Apache License, Version 2.0."), Paragraph("END OF TERMS")), legalBlocks(text))
    }
}
