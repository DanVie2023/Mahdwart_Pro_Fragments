package com.viedan.mahdwartpro_fragments

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * Generates the PDF form "Mitteilung über Mahd- / Ernteereignisse" (notification of
 * mowing / harvesting events) for the wind farm Wasbek-Ehndorf.
 *
 * The PDF is drawn manually on an A4 canvas using [PdfDocument]. It consists of a form
 * header (title, subtitle, info row, table header) followed by one table row per event.
 * If the rows do not fit on one page, additional pages are added and the header is
 * repeated on each of them.
 *
 * Usage: val file = EventPdfGenerator(context).generate(...)
 *
 * This class performs blocking file I/O and must therefore not be called on the main thread.
 *
 * @property context Used to resolve color resources and to locate the cache directory.
 *   Pass the application context to avoid holding a reference to a UI component.
 */
class EventPdfGenerator(private val context: Context) {

    /**
     * Creates the PDF and writes it to the app's cache directory.
     *
     * Each entry in [events] becomes one table row and must contain 7 values in this order:
     * 1. Flurstück (parcel number)
     * 2. Flur (district)
     * 3. Gemarkung (cadastral area)
     * 4. Bewirtschaftungsform (type of farming, e.g. "Mahd" or "Ernte")
     * 5. Start date (dd.MM.yyyy)
     * 6. Start time (HH:mm)
     * 7. End date of the measure (dd.MM.yyyy)
     *
     * Missing values are rendered as empty cells; additional values are ignored.
     *
     * @param reportDate Date of the notification, shown in the info row.
     * @param parkAttendant Name of the park attendant (or deputy), shown in the info row.
     * @param events Table rows, one list of 7 strings per event.
     * @param fileDate Date used to build the file name (`Mahdereignis_<fileDate>.pdf`).
     * @return The generated PDF file inside [Context.getCacheDir]. An existing file with
     *   the same name is overwritten.
     * @throws java.io.IOException If the file cannot be written.
     */
    fun generate(
        reportDate: String,
        parkAttendant: String,
        events: List<List<String>>,
        fileDate: String
    ): File {
        val document = PdfDocument()
        val pdfBackground = ContextCompat.getColor(context, R.color.white)
        val pdfTextColor = ContextCompat.getColor(context, R.color.bg)
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create() // A4 bei 72 dpi
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        //----- Paints -----
        val titlePaint = Paint().apply {
            color = pdfTextColor
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint().apply {
            color = pdfTextColor
            textSize = 12f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint().apply {
            color = pdfTextColor
            textSize = 10.5f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val valuePaint = Paint().apply {
            color = pdfTextColor
            textSize = 10.5f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val headerPaint = Paint().apply {
            color = pdfTextColor
            textSize = 10.5f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val cellPaint = Paint().apply {
            color = pdfTextColor
            textSize = 10f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint().apply {
            color = pdfTextColor
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        // ---- Layout-constants -----
        val marginLeft = 51f
        val tableRight = 557f

        // width (Sum = tableRight - marginLeft = 506)
        val wFlurstueck = 55f
        val wFlur = 40f
        val wGemarkung = 75f
        val wBewirtschaftung = 90f
        val wBeginnDatum = 80f
        val wStartTime = 66f

        val colX = floatArrayOf(
            marginLeft,
            marginLeft + wFlurstueck,
            marginLeft + wFlurstueck + wFlur,
            marginLeft + wFlurstueck + wFlur + wGemarkung,
            marginLeft + wFlurstueck + wFlur + wGemarkung + wBewirtschaftung,
            marginLeft + wFlurstueck + wFlur + wGemarkung + wBewirtschaftung + wBeginnDatum,
            marginLeft + wFlurstueck + wFlur + wGemarkung + wBewirtschaftung + wBeginnDatum + wStartTime,
            tableRight
        )
        val columnCount = colX.size - 1 // 7

        // Row heights
        val titleRowH = 34f
        val subtitleRowH = 26f
        val infoRowH = 37f
        val headerRow1H = 36f
        val headerRow2H = 20f
        val dataRowH = 19.7f
        val pageBottomLimit = 792 - 40f

        /**
         * Draws [text] horizontally centered between [left] and [right] at the given
         * [baselineY]. Uses the current value of [canvas], so it also works after a page break.
         */
        fun centeredText(text: String, left: Float, right: Float, baselineY: Float, paint: Paint) {
            canvas.drawText(text, (left + right) / 2f, baselineY, paint)
        }

        /**
         * Draws the complete form header (title, subtitle, info row and table header)
         * on the current page.
         *
         * @return The y position where the first data row starts.
         */
        fun drawHeader(): Float {
            canvas.drawColor(pdfBackground)
            var y = 40f

            // title row
            canvas.drawRect(marginLeft, y, tableRight, y + titleRowH, linePaint)
            centeredText(
                "Windpark Wasbek-Ehndorf",
                marginLeft, tableRight,
                y + titleRowH / 2f + 6f,
                titlePaint
            )
            y += titleRowH

            // subtitle row
            canvas.drawRect(marginLeft, y, tableRight, y + subtitleRowH, linePaint)
            centeredText(
                "Mitteilung über Mahd- / Ernteereignisse",
                marginLeft, tableRight,
                y + subtitleRowH / 2f + 4f,
                subtitlePaint
            )
            y += subtitleRowH

            // infoline: Label | Wert | Label | Wert
            canvas.drawRect(marginLeft, y, tableRight, y + infoRowH, linePaint)
            canvas.drawLine(colX[2], y, colX[2], y + infoRowH, linePaint)
            canvas.drawLine(colX[4], y, colX[4], y + infoRowH, linePaint)
            canvas.drawLine(colX[5], y, colX[5], y + infoRowH, linePaint)

            canvas.drawText("Datum der", colX[0] + 4f, y + 15f, labelPaint)
            canvas.drawText("Meldung:", colX[0] + 4f, y + 29f, labelPaint)
            canvas.drawText(reportDate, colX[2] + 4f, y + 21f, valuePaint)
            canvas.drawText("Parkbetreuerin:", colX[4] + 4f, y + 15f, labelPaint)
            canvas.drawText("(Vertreterin)", colX[4] + 4f, y + 29f, labelPaint)
            canvas.drawText(parkAttendant, colX[5] + 4f, y + 21f, valuePaint)
            y += infoRowH

            // tablehead
            val headerTotalH = headerRow1H + headerRow2H
            canvas.drawRect(marginLeft, y, tableRight, y + headerTotalH, linePaint)

            // Vertical separators across the full header height
            for (i in 1..4) canvas.drawLine(colX[i], y, colX[i], y + headerTotalH, linePaint)
            canvas.drawLine(colX[6], y, colX[6], y + headerTotalH, linePaint)

            // Horizontal separator only below "Mahd- / Erntebeginn"
            canvas.drawLine(colX[4], y + headerRow1H, colX[6], y + headerRow1H, linePaint)
            // Separator between "Datum" and "Uhrzeit"
            canvas.drawLine(colX[5], y + headerRow1H, colX[5], y + headerTotalH, linePaint)

            val midY = y + headerTotalH / 2f
            centeredText("Flurstück", colX[0], colX[1], midY + 4f, headerPaint)
            centeredText("Flur", colX[1], colX[2], midY + 4f, headerPaint)
            centeredText("Gemarkung", colX[2], colX[3], midY + 4f, headerPaint)
            centeredText("Bewirtschaftungs-", colX[3], colX[4], midY - 1f, headerPaint)
            centeredText("form", colX[3], colX[4], midY + 12f, headerPaint)

            centeredText(
                "Mahd- / Erntebeginn",
                colX[4], colX[6],
                y + headerRow1H / 2f + 4f,
                headerPaint
            )
            centeredText(
                "Datum",
                colX[4], colX[5],
                y + headerRow1H + headerRow2H / 2f + 4f,
                headerPaint
            )
            centeredText(
                "Uhrzeit",
                colX[5], colX[6],
                y + headerRow1H + headerRow2H / 2f + 4f,
                headerPaint
            )

            // "Maßnahmenende": eine Spalte über die volle Kopfhöhe
            centeredText("Maßnahmenende", colX[6], colX[7], midY + 4f, headerPaint)

            return y + headerTotalH
        }

        var y = drawHeader()

        // --- Data rows ---------------------------------------------------------------------
        // One row per event. When the current page is full, a new page is started
        // and the header is repeated.
        for (e in events) {
            if (y + dataRowH > pageBottomLimit) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = drawHeader()
            }

            canvas.drawRect(marginLeft, y, tableRight, y + dataRowH, linePaint)
            for (idx in 1 until columnCount) {
                canvas.drawLine(colX[idx], y, colX[idx], y + dataRowH, linePaint)
            }

            // Cell contents, centered in their column; missing values become empty cells
            for (c in 0 until columnCount) {
                centeredText(
                    e.getOrElse(c) { "" },
                    colX[c],
                    colX[c + 1],
                    y + dataRowH / 2f + 4f,
                    cellPaint
                )
            }
            y += dataRowH
        }

        document.finishPage(page)

        // Write to the app's temporary cache directory
        val file = File(context.cacheDir, "Mahdereignis_$fileDate.pdf")
        try {
            FileOutputStream(file).use { out -> document.writeTo(out) }
        } finally {
            document.close()
        }

        return file
    }
}
