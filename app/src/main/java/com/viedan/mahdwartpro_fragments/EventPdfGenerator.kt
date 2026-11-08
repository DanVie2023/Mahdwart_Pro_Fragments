package com.viedan.mahdwartpro_fragments

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class EventPdfGenerator(private val context: Context) {

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

        // Layout-constants
        val marginLeft = 51f
        val tableRight = 557f

        // width (Summe = tableRight - marginLeft = 506)
        val wFlurstueck = 55f
        val wFlur = 40f
        val wGemarkung = 75f
        val wBewirtschaftung = 90f
        val wBeginnDatum = 80f
        val wStartTime = 66f

        // colX[i] = linke Kante der Spalte i, colX[7] = rechte Kante der Tabelle
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

        val titleRowH = 34f
        val subtitleRowH = 26f
        val infoRowH = 37f
        val headerRow1H = 36f
        val headerRow2H = 20f
        val dataRowH = 19.7f
        val pageBottomLimit = 792 - 40f

        fun centeredText(text: String, left: Float, right: Float, baselineY: Float, paint: Paint) {
            canvas.drawText(text, (left + right) / 2f, baselineY, paint)
        }

        fun drawHeader(): Float {
            canvas.drawColor(pdfBackground)
            var y = 40f

            // Titelzeile
            canvas.drawRect(marginLeft, y, tableRight, y + titleRowH, linePaint)
            centeredText(
                "Windpark Wasbek-Ehndorf",
                marginLeft, tableRight,
                y + titleRowH / 2f + 6f,
                titlePaint
            )
            y += titleRowH

            // Untertitel
            canvas.drawRect(marginLeft, y, tableRight, y + subtitleRowH, linePaint)
            centeredText(
                "Mitteilung über Mahd- / Ernteereignisse",
                marginLeft, tableRight,
                y + subtitleRowH / 2f + 4f,
                subtitlePaint
            )
            y += subtitleRowH

            // Infozeile: Label | Wert | Label | Wert
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

            // Tabellenkopf
            val headerTotalH = headerRow1H + headerRow2H
            canvas.drawRect(marginLeft, y, tableRight, y + headerTotalH, linePaint)

            // Senkrechte Trennlinien über die ganze Kopfhöhe
            for (i in 1..4) canvas.drawLine(colX[i], y, colX[i], y + headerTotalH, linePaint)
            canvas.drawLine(colX[6], y, colX[6], y + headerTotalH, linePaint)

            // Waagerechte Linie nur unter "Mahd- / Erntebeginn"
            canvas.drawLine(colX[4], y + headerRow1H, colX[6], y + headerRow1H, linePaint)
            // Trennlinie Datum/Uhrzeit
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

        // Eine Zeile pro Eintrag; bei voller Seite wird automatisch eine neue begonnen
        for (e in events) {
            if (y + dataRowH > pageBottomLimit) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = drawHeader() // Kopf auf jeder neuen Seite wiederholen
            }

            canvas.drawRect(marginLeft, y, tableRight, y + dataRowH, linePaint)
            for (idx in 1 until columnCount) {
                canvas.drawLine(colX[idx], y, colX[idx], y + dataRowH, linePaint)
            }

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

        // Im temporären Cache-Verzeichnis der App speichern
        val file = File(context.cacheDir, "Mahdereignis_$fileDate.pdf")
        try {
            FileOutputStream(file).use { out -> document.writeTo(out) }
        } finally {
            document.close()
        }

        return file
    }
}
