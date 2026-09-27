package com.viedan.mahdwartpro_fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.viedan.mahdwartpro_fragments.databinding.FragmentSendDialogBinding
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import android.widget.Toast
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart

class SendDialogFragment : Fragment() {

    private lateinit var binding: FragmentSendDialogBinding
    private var wtgs: List<String> = emptyList()
    private var date: String = ""
    private var datetoday: String = ""
    private var startDate: String = ""
    private var startTime: String = ""
    private var endDate: String = ""
    private var endTime: String = ""
    private var flurstueck: String = ""
    private var flur: String = ""
    private var gemarkung: String = ""
    private var bewirtschaftungsform: String = ""
    private val testEreignisse: MutableList<List<String>> = mutableListOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wtgs = arguments?.getStringArrayList("wtgs") ?: emptyList()

        date = arguments?.getString("date") ?: ""

        datetoday = java.time.LocalDate.now().toString()

        startDate = "DateUnkown"

        startTime = arguments?.getString("startTime") ?: ""

        endDate = "DateUnkown"

        endTime = arguments?.getString("endTime") ?: ""

        flurstueck = "1"

        flur = "1"

        gemarkung = "1"

        bewirtschaftungsform = "1"

        testEreignisse.add(listOf(flurstueck, flur, gemarkung, bewirtschaftungsform, startDate, startTime, endDate, endTime))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentSendDialogBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.InputTextEmailSubject.setText(
            "Windpark Wasbek-Ehndorf - Mahd-/Ernteereignis"
        )

        binding.InputTextEmailBody.setText(
            buildString {
                appendLine("Sehr geehrte Damen und Herren,")
                appendLine()
                appendLine("im Windpark Wasbek-Ehndorf ist ein Mahd- oder Ernteereignis geplant.")
                appendLine()
                appendLine("Geplantes Datum:")
                appendLine(date)
                appendLine()
                appendLine("Geplante Uhrzeit:")
                appendLine("$startTime - $endTime")
                appendLine()
                appendLine("Folgende Windenergieanlagen sind davon betroffen:")
                appendLine()
                wtgs.forEach {
                    appendLine("• $it")
                }
                appendLine()
                appendLine(
                    "Bitte entnehmen Sie die relevanten Informationen aus dem beiliegnden PDF-Dokument."
                )
                appendLine()
                appendLine("Vielen Dank.")
            }
        )

        binding.buttonSend.setOnClickListener {
            sendTestEmail()
        }

        binding.buttonClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }


    private fun sendTestEmail() {

        Thread {
            try {

                val pdfFile = generateEventPdf(requireContext(), "Today", "Unkown",testEreignisse)
                val emailSubject = binding.InputTextEmailSubject.text.toString()
                val emailBody = binding.InputTextEmailBody.text.toString()

                val properties = Properties()
                properties["mail.smtp.host"] = "smtp.gmail.com"
                properties["mail.smtp.port"] = "587"
                properties["mail.smtp.auth"] = "true"
                properties["mail.smtp.starttls.enable"] = "true"
                properties["mail.smtp.starttls.required"] = "true"
                properties["mail.smtp.ssl.trust"] = "smtp.gmail.com"
                properties["mail.smtp.ssl.protocols"] = "TLSv1.2"

                val session =
                    Session.getInstance(
                        properties,
                        object : Authenticator() {

                            override fun getPasswordAuthentication(): PasswordAuthentication {

                                return PasswordAuthentication(
                                    "Mahdwart@gmail.com",
                                    "ozes qscp asrd lbyz"
                                )
                            }
                        }
                    )

                val message = MimeMessage(session)
                message.setFrom(InternetAddress("Mahdwart@gmail.com"))
                message.setRecipients(
                    Message.RecipientType.TO,
                    "d.vieler@e3-gmbh.de"
                )
                message.subject = emailSubject

                // Textpart
                val textPart = MimeBodyPart()
                textPart.setText(emailBody, "UTF-8")

                // PDF
                val attachmentPart = MimeBodyPart()
                val source = FileDataSource(pdfFile)
                attachmentPart.dataHandler = DataHandler(source)
                attachmentPart.fileName = pdfFile.name

                val multipart = MimeMultipart()
                multipart.addBodyPart(textPart)
                multipart.addBodyPart(attachmentPart)

                message.setContent(multipart)

                Transport.send(message)
                requireActivity()
                    .runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            "E-Mail erfolgreich gesendet",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            } catch (e: Exception) {
                requireActivity()
                    .runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            "Fehler: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }.start()
    }

    private fun generateEventPdf(
        context: Context,
        meldungsdatum: String,
        parkbetreuerin: String,
        ereignisse: MutableList<List<String>>
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create() // A4 bei 72dpi
        var page = document.startPage(pageInfo)
        var canvas = page.canvas


        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val anlagePaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10.5f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val valuePaint = Paint().apply {
            color = Color.BLACK
            textSize = 10.5f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10.5f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val cellPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        // Layout-Constants
        val marginLeft = 51f
        val tableRight = 557f

        // Spaltenbreiten der Ereignistabelle (Summe = tableRight - marginLeft)
        val wFlurstueck = 49f
        val wFlur = 37f
        val wGemarkung = 65f
        val wBewirtschaftung = 80f
        val wBeginnDatum = 73f
        val wstartTime = 61f
        val wEndeDatum = 76f
        val wendTime = 65f

        // colX[i] = linke Kante von Spalte i, colX[8] = rechte Tabellenkante
        val colX = floatArrayOf(
            marginLeft,
            marginLeft + wFlurstueck,
            marginLeft + wFlurstueck + wFlur,
            marginLeft + wFlurstueck + wFlur + wGemarkung,
            marginLeft + wFlurstueck + wFlur + wGemarkung + wBewirtschaftung,
            marginLeft + wFlurstueck + wFlur + wGemarkung + wBewirtschaftung + wBeginnDatum,
            marginLeft + wFlurstueck + wFlur + wGemarkung + wBewirtschaftung + wBeginnDatum + wstartTime,
            marginLeft + wFlurstueck + wFlur + wGemarkung + wBewirtschaftung + wBeginnDatum + wstartTime + wEndeDatum,
            tableRight
        )

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

        /** Zeichnet den kompletten Formularkopf (Titel, Untertitel, Info-Zeile, Tabellenkopf) und
         *  liefert die y-Position zurück, ab der die Datenzeilen beginnen. */
        fun drawHeader(): Float {
            var y = 40f

            // "Anlage 3" oben rechts, über der Box
            canvas.drawText("Anlage 3", tableRight, y, anlagePaint)
            y += 20f

            // Titelzeile
            canvas.drawRect(marginLeft, y, tableRight, y + titleRowH, linePaint)
            centeredText("Windpark Wasbek-Ehndorf", marginLeft, tableRight, y + titleRowH / 2f + 6f, titlePaint)
            y += titleRowH

            // Untertitel
            canvas.drawRect(marginLeft, y, tableRight, y + subtitleRowH, linePaint)
            centeredText("Mitteilung über Mahd- / Ernteereignisse", marginLeft, tableRight, y + subtitleRowH / 2f + 4f, subtitlePaint)
            y += subtitleRowH

            // Info-Zeile: "Datum der Meldung:" | Wert | "Parkbetreuerin: (Vertreterin)" | Wert
            canvas.drawRect(marginLeft, y, tableRight, y + infoRowH, linePaint)
            canvas.drawLine(colX[2], y, colX[2], y + infoRowH, linePaint)
            canvas.drawLine(colX[4], y, colX[4], y + infoRowH, linePaint)
            canvas.drawLine(colX[5], y, colX[5], y + infoRowH, linePaint)

            canvas.drawText("Datum der Meldung:", colX[0] + 4f, y + 21f, labelPaint)
            canvas.drawText(datetoday, colX[2] + 4f, y + 21f, valuePaint)
            canvas.drawText("Parkbetreuerin:", colX[4] + 4f, y + 15f, labelPaint)
            canvas.drawText("(Vertreterin)", colX[4] + 4f, y + 29f, labelPaint)
            canvas.drawText(parkbetreuerin, colX[5] + 4f, y + 21f, valuePaint)
            y += infoRowH

            // Header
            val headerTotalH = headerRow1H + headerRow2H
            canvas.drawRect(marginLeft, y, tableRight, y + headerTotalH, linePaint)
            for (i in 1..3) canvas.drawLine(colX[i], y, colX[i], y + headerTotalH, linePaint) // Spalten 1-4
            canvas.drawLine(colX[4], y, colX[4], y + headerTotalH, linePaint)
            canvas.drawLine(colX[6], y, colX[6], y + headerTotalH, linePaint)
            canvas.drawLine(colX[8], y, colX[8], y + headerTotalH, linePaint)
            // horizontale Trennung nur unter "Mahd-/Erntebeginn" und "Mahd-/Ernteende"
            canvas.drawLine(colX[4], y + headerRow1H, colX[8], y + headerRow1H, linePaint)
            // Trennung Datum/Uhrzeit in der unteren Kopfzeile
            canvas.drawLine(colX[5], y + headerRow1H, colX[5], y + headerTotalH, linePaint)
            canvas.drawLine(colX[7], y + headerRow1H, colX[7], y + headerTotalH, linePaint)

            centeredText("Flurstück", colX[0], colX[1], y + headerTotalH / 2f + 4f, headerPaint)
            centeredText("Flur", colX[1], colX[2], y + headerTotalH / 2f + 4f, headerPaint)
            centeredText("Gemarkung", colX[2], colX[3], y + headerTotalH / 2f + 4f, headerPaint)
            centeredText("Bewirtschaftungs-", colX[3], colX[4], y + headerTotalH / 2f - 1f, headerPaint)
            centeredText("form", colX[3], colX[4], y + headerTotalH / 2f + 12f, headerPaint)

            centeredText("Mahd- / Erntebeginn", colX[4], colX[6], y + headerRow1H / 2f + 4f, headerPaint)
            centeredText("Mahd- / Ernteende", colX[6], colX[8], y + headerRow1H / 2f + 4f, headerPaint)
            centeredText("Datum", colX[4], colX[5], y + headerRow1H + headerRow2H / 2f + 4f, headerPaint)
            centeredText("Uhrzeit", colX[5], colX[6], y + headerRow1H + headerRow2H / 2f + 4f, headerPaint)
            centeredText("Datum", colX[6], colX[7], y + headerRow1H + headerRow2H / 2f + 4f, headerPaint)
            centeredText("Uhrzeit", colX[7], colX[8], y + headerRow1H + headerRow2H / 2f + 4f, headerPaint)

            return y + headerTotalH
        }

        var y = drawHeader()

        // 25 rows, for more is an extra page added
        val minRows = 25
        val rowCount = maxOf(ereignisse.size, minRows)

        for (i in 0 until rowCount) {
            if (y + dataRowH > pageBottomLimit) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = drawHeader() // Heading on every new page
            }

            canvas.drawRect(marginLeft, y, tableRight, y + dataRowH, linePaint)
            for (idx in 1..7) canvas.drawLine(colX[idx], y, colX[idx], y + dataRowH, linePaint)

            if (i < ereignisse.size) {
                val e = ereignisse[i]   // e ist List<String> mit 8 Einträgen
                for (c in 0..7) {
                    centeredText(e[c], colX[c], colX[c + 1], y + dataRowH / 2f + 4f, cellPaint)
                }
            }
            y += dataRowH
        }

        document.finishPage(page)

        // with context.cacheDir saved to temp app storage
        val file = File(context.cacheDir, "Mahdereignis_$date.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return file
    }


}