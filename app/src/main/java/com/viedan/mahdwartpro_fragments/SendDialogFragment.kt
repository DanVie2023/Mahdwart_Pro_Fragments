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
    private var startTime: String = ""
    private var endTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wtgs = arguments?.getStringArrayList("wtgs") ?: emptyList()

        date = arguments?.getString("date") ?: ""

        startTime = arguments?.getString("startTime") ?: ""

        endTime = arguments?.getString("endTime") ?: ""
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

                val pdfFile = generateEventPdf(requireContext())
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
                    "a.bohdan@e3-gmbh.de, d.vieler@e3-gmbh.de"
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

    private fun generateEventPdf(context: Context): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 bei 72dpi
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isFakeBoldText = true
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val marginLeft = 40f
        val col1Width = 150f
        val col2Width = 350f
        val tableWidth = col1Width + col2Width
        val rowHeight = 25f
        val pageBottomLimit = 800f

        var y = 50f
        canvas.drawText("Windpark Wasbek-Ehndorf - Mahd-/Ernteereignis", marginLeft, y, titlePaint)
        y += 40f

        fun drawRow(col1: String, col2: String, bold: Boolean = false) {
            canvas.drawRect(marginLeft, y, marginLeft + tableWidth, y + rowHeight, linePaint)
            canvas.drawLine(marginLeft + col1Width, y, marginLeft + col1Width, y + rowHeight, linePaint)
            canvas.drawText(col1, marginLeft + 5, y + 17, if (bold) headerPaint else textPaint)
            canvas.drawText(col2, marginLeft + col1Width + 5, y + 17, if (bold) headerPaint else textPaint)
            y += rowHeight
        }

        drawRow("Feld", "Wert", bold = true)
        drawRow("Datum", date)
        drawRow("Uhrzeit", "$startTime - $endTime")

        y += 20f
        canvas.drawText("Betroffene Windenergieanlagen:", marginLeft, y, titlePaint.apply { textSize = 13f })
        y += 20f

        wtgs.forEach { wtg ->
            // Neue Seite, falls kein Platz mehr ist
            if (y + rowHeight > pageBottomLimit) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
            canvas.drawRect(marginLeft, y, marginLeft + tableWidth, y + rowHeight, linePaint)
            canvas.drawText(wtg, marginLeft + 5, y + 17, textPaint)
            y += rowHeight
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