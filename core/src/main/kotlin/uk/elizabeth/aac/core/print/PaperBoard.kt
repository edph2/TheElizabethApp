package uk.elizabeth.aac.core.print

import uk.elizabeth.aac.core.model.KeyboardLayout
import uk.elizabeth.aac.core.model.PhraseBoard

/**
 * A printable paper communication board: the quick replies, every phrase, and an alphabet
 * to point at. It is the back-up for when the tablet is flat, broken or out of reach.
 * Produced as self-contained HTML (no external files), to print or save as PDF.
 */
object PaperBoard {
    fun html(board: PhraseBoard, layout: KeyboardLayout, title: String = "My communication board"): String = buildString {
        append("<!DOCTYPE html><html lang=\"en-GB\"><head><meta charset=\"utf-8\"><title>")
        append(esc(title))
        append("</title><style>")
        append(
            """
            @page { size: A4 landscape; margin: 12mm; }
            body { font-family: sans-serif; color: #000; }
            h1 { font-size: 20pt; margin: 0 0 4mm; }
            h2 { font-size: 15pt; margin: 6mm 0 2mm; page-break-after: avoid; }
            .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 3mm; }
            .cell { border: 0.6mm solid #000; border-radius: 3mm; padding: 4mm; font-size: 15pt; min-height: 14mm;
                    display: flex; align-items: center; justify-content: center; text-align: center; page-break-inside: avoid; }
            .quick .cell { font-size: 22pt; font-weight: bold; }
            .letters { display: grid; gap: 2mm; }
            .letters .cell { font-size: 24pt; font-weight: bold; min-height: 16mm; padding: 2mm; }
            .note { font-size: 11pt; margin-top: 6mm; }
            """.trimIndent(),
        )
        append("</style></head><body>")
        append("<h1>").append(esc(title)).append("</h1>")
        append("<p class=\"note\">Point to a phrase or spell a word. Please be patient, and check you have understood.</p>")
        append("<div class=\"grid quick\">")
        board.quickReplies.forEach { append("<div class=\"cell\">").append(esc(it.text)).append("</div>") }
        append("</div>")
        for (category in board.categories) {
            if (category.phrases.isEmpty()) continue
            append("<h2>").append(esc(category.label)).append("</h2><div class=\"grid\">")
            category.phrases.forEach { append("<div class=\"cell\">").append(esc(it.text)).append("</div>") }
            append("</div>")
        }
        append("<h2>Letters</h2>")
        for (row in layout.rows + listOf("0123456789")) {
            append("<div class=\"letters\" style=\"grid-template-columns: repeat(${row.length}, 1fr); margin-bottom: 2mm\">")
            row.forEach { append("<div class=\"cell\">").append(esc(it.uppercaseChar().toString())).append("</div>") }
            append("</div>")
        }
        append("<div class=\"grid\" style=\"margin-top: 3mm\"><div class=\"cell\">Start again</div><div class=\"cell\">New word</div>")
        append("<div class=\"cell\">Finished</div><div class=\"cell\">I made a mistake</div></div>")
        append("</body></html>")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
