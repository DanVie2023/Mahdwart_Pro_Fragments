package com.viedan.mahdwartpro_fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.makeText
import java.time.format.DateTimeFormatter
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.fragment.app.Fragment

import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.viedan.mahdwartpro_fragments.databinding.FragmentSendDialogBinding

import org.json.JSONArray
import org.json.JSONObject

import java.io.File
import java.io.FileOutputStream
import java.util.Properties

import javax.activation.DataHandler
import javax.activation.FileDataSource

import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport

import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import androidx.core.content.edit
import java.time.LocalDate


class SendDialogFragment : Fragment() {

    private var _binding: FragmentSendDialogBinding? = null
    private val binding get() = _binding!!
    private var wtgs: List<String> = emptyList()
    private var parcels: List<String> = emptyList()
    private var datetoday: String = ""
    private var startDate: String = ""
    private var startTime: String = ""
    private var endDate: String = "DateUnknown"
    private var siteattentand: String = ""
    private var agriculturalType: String = ""
    private var emailadd: String = ""
    private val testEreignisse: MutableList<List<String>> = mutableListOf()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val parcelRegex = Regex("""^G([^-]+)-F([^-]+)-FS([^-]+)$""")

    data class Recipient(
        var name: String,
        var email: String,
        var selected: Boolean = false
    )

    data class ParcelInfo(
        val gemarkung: String,
        val flur: String,
        val flurstueck: String
    )

    private val recipients = mutableListOf<Recipient>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wtgs = arguments?.getStringArrayList("wtgs") ?: emptyList()
        parcels = arguments?.getStringArrayList("parcel") ?: emptyList()
        datetoday = LocalDate.now().format(dateFormatter)
        startDate = arguments?.getString("startDate") ?: ""
        startTime = arguments?.getString("startTime") ?: ""
        //agriculturalType has to be placed BEFORE endDate!
        agriculturalType = arguments?.getString("agriculturalType") ?: ""
        siteattentand = arguments?.getString("siteattentand") ?: ""

        val daysToAdd = when (agriculturalType) {
            "Mahd" -> 3L
            "Ernte" -> 4L
            else -> null
        }
        if (daysToAdd != null) {
            endDate = try {
                LocalDate.parse(startDate.trim(), dateFormatter)
                    .plusDays(daysToAdd)
                    .format(dateFormatter)
            } catch (e: Exception) {
                android.util.Log.e("EndDate", "startDate='$startDate' nicht parsbar", e)
                "DateUnknown"
            }
        }

        parseParcels(parcels).forEach { p ->
            testEreignisse.add(
                listOf(
                    p.flurstueck,
                    p.flur,
                    p.gemarkung,
                    agriculturalType,
                    startDate,
                    startTime,
                    endDate
                )
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSendDialogBinding.inflate(inflater, container, false)
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
                appendLine("im Windpark Wasbek-Ehndorf ist eine $agriculturalType geplant.")
                appendLine()
                appendLine("Geplantes Datum:")
                appendLine(startDate)
                appendLine("Geplante Startzeit:")
                appendLine(startTime)
                appendLine()
                appendLine("Folgende Windenergieanlagen werden bis zum $endDate von einer Stunde vor Sonnenaufgang bis eine Stunde nach Sonnenuntergang abgeschaltet:")
                appendLine()
                wtgs.forEach {
                    appendLine("• WEA $it")
                }
                appendLine()
                appendLine(
                    "Bitte entnehmen Sie die relevanten Informationen aus dem beiliegenden PDF-Dokument."
                )
                appendLine()
                appendLine("Vielen Dank.")
            }
        )

        loadRecipients()
        displayRecipients()

        binding.buttonAddRecipient.setOnClickListener { showRecipientDialog() }
        binding.buttonSend.setOnClickListener { sendEmail() }
        binding.buttonClose.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadRecipients() {

        val prefs =
            requireContext().getSharedPreferences(
                "recipients",
                Context.MODE_PRIVATE
            )

        val json =
            prefs.getString(/* key = */ "list", /* defValue = */ "[]")
                ?: "[]"

        recipients.clear()

        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {

                val obj =
                    array.getJSONObject(i)

                recipients.add(
                    Recipient(
                        name = obj.getString("name"),

                        email = obj.getString("email"),

                        selected = obj.optBoolean(
                            "selected",
                            false
                        )
                    )
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            recipients.clear()
        }
    }

    private fun saveRecipients() {

        val array = JSONArray()

        recipients.forEach {

            val obj = JSONObject()

            obj.put("name", it.name)
            obj.put("email", it.email)
            obj.put("selected", it.selected)

            array.put(obj)
        }

        requireContext()
            .getSharedPreferences(
                "recipients",
                Context.MODE_PRIVATE
            )
            .edit {
                putString(
                    "list",
                    array.toString()
                )
            }
    }

    @SuppressLint("SetTextI18n")
    private fun displayRecipients() {

        binding.listEmail.removeAllViews()


        recipients.forEachIndexed { index, recipient ->
            val row =
                LinearLayout(requireContext()).apply {

                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    setPadding(0, 8, 0, 8)

                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                }

            val checkBox =
                CheckBox(requireContext()).apply {

                    isChecked = recipient.selected

                    setOnCheckedChangeListener { _,
                                                 checked ->

                        recipient.selected =
                            checked

                        saveRecipients()
                        updateSelectedCount()
                    }
                }

            val text =
                TextView(requireContext()).apply {

                    text = "${recipient.name}\n${recipient.email}"
                    textSize = 15f

                    setTextColor(
                        Color.rgb(
                            30,
                            41,
                            59
                        )
                    )

                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                }

            val gap = (5 * resources.displayMetrics.density).toInt()

            val editButton = MaterialButton(requireContext()).apply {

                layoutParams = LinearLayout.LayoutParams(
                    (33 * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt()
                )

                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0

                setPadding(13, 0, 0, 0)

                cornerRadius = (10 * resources.displayMetrics.density).toInt()

                setIconResource(R.drawable.pencil)
                iconSize = (20 * resources.displayMetrics.density).toInt()

                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START

                setOnClickListener {
                    showRecipientDialog(index)
                }
            }

            val deleteButton = MaterialButton(requireContext()).apply {

                layoutParams = LinearLayout.LayoutParams(
                    (33 * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginStart = gap
                }

                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0

                setPadding(13, 0, 0, 0)

                cornerRadius = (10 * resources.displayMetrics.density).toInt()

                setIconResource(R.drawable.delete)
                iconSize = (20 * resources.displayMetrics.density).toInt()

                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START

                setOnClickListener {
                    showDeleteConfirmation(index)
                }
            }

            row.addView(checkBox)
            row.addView(text)
            row.addView(editButton)
            row.addView(deleteButton)

            binding.listEmail.addView(row)
        }

        updateSelectedCount()
    }

    private fun updateSelectedCount() {

        val count =
            recipients.count {
                it.selected
            }
    }

    private fun showRecipientDialog(
        editIndex: Int? = null
    ) {

        val dialogView =
            layoutInflater.inflate(
                R.layout.dialog_recipient,
                null
            )

        val nameInput =
            dialogView.findViewById<TextInputEditText>(
                R.id.inputRecipientName
            )

        val emailInput =
            dialogView.findViewById<TextInputEditText>(
                R.id.inputRecipientEmail
            )

        if (editIndex != null) {

            val recipient = recipients[editIndex]

            nameInput.setText(recipient.name)
            emailInput.setText(recipient.email)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (editIndex == null)
                    getString(R.string.recipient_new)
                else
                    getString(R.string.recipient_edit)
            )

            .setView(dialogView)

            .setNegativeButton(
                getString(R.string.recipient_cancel),
                null
            )

            .setPositiveButton(
                getString(R.string.recipient_save)
            ) { _, _ ->

                val name =
                    nameInput.text
                        ?.toString()
                        ?.trim()
                        ?: ""

                val email =
                    emailInput.text
                        ?.toString()
                        ?.trim()
                        ?: ""

                if (name.isEmpty()) {

                    makeText(
                        requireContext(),
                        getString(R.string.error_name_required),
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                if (email.isEmpty()) {

                    makeText(
                        requireContext(),
                        getString(R.string.error_email_required),
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                if (!android.util.Patterns.EMAIL_ADDRESS
                        .matcher(email)
                        .matches()
                ) {

                    makeText(
                        requireContext(),
                        getString(R.string.error_invalid_email),
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                if (editIndex == null) {

                    recipients.add(
                        Recipient(
                            name = name,
                            email = email,
                            selected = false
                        )
                    )

                } else {

                    recipients[editIndex].name = name
                    recipients[editIndex].email = email
                }


                saveRecipients()
                displayRecipients()
            }
            .show()
    }

    private fun showDeleteConfirmation(
        index: Int
    ) {

        val recipient =
            recipients[index]

        MaterialAlertDialogBuilder(
            requireContext()
        )

            .setTitle(R.string.recipient_delete)
            .setMessage("${recipient.name}\n${recipient.email}")
            .setNegativeButton(R.string.recipient_cancel, null)
            .setPositiveButton(R.string.recipient_delete) { _, _ ->

                recipients.removeAt(index)
                saveRecipients()
                displayRecipients()
            }
            .show()
    }

    private fun sendEmail() {

        val selectedRecipients = recipients
            .filter { it.selected }
            .map { it.email }

        if (selectedRecipients.isEmpty()) {
            makeText(
                requireContext(),
                R.string.error_no_recipient_selected,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Read everything that needs the UI or a Context BEFORE switching to the background thread
        val appContext = requireContext().applicationContext
        val emailSubject = binding.InputTextEmailSubject.text.toString()
        val emailBody = binding.InputTextEmailBody.text.toString()

        // Disable the send button to prevent double sends
        setSending(true)

        // The coroutine is cancelled automatically when the view is destroyed
        viewLifecycleOwner.lifecycleScope.launch {

            // Run PDF generation and SMTP sending off the main thread
            val result = withContext(Dispatchers.IO) {
                runCatching {

                    // Generate the PDF attachment
                    val pdfFile = generateEventPdf(appContext, datetoday, siteattentand, testEreignisse)

                    // SMTP configuration (Gmail, STARTTLS on port 587)
                    val properties = Properties()
                    properties["mail.smtp.host"] = "smtp.gmail.com"
                    properties["mail.smtp.port"] = "587"
                    properties["mail.smtp.auth"] = "true"
                    properties["mail.smtp.starttls.enable"] = "true"
                    properties["mail.smtp.starttls.required"] = "true"
                    properties["mail.smtp.ssl.trust"] = "smtp.gmail.com"
                    properties["mail.smtp.ssl.protocols"] = "TLSv1.2"

                    // Authenticate with the sender account
                    val session = Session.getInstance(
                        properties,
                        object : Authenticator() {
                            override fun getPasswordAuthentication(): PasswordAuthentication {
                                return PasswordAuthentication(
                                    "Mahdwart@gmail.com",
                                    "ozes qscp asrd lbyz" // do not keep credentials in source code
                                )
                            }
                        }
                    )

                    // Build the message header
                    val message = MimeMessage(session)
                    message.setFrom(InternetAddress("Mahdwart@gmail.com"))
                    message.setRecipients(
                        Message.RecipientType.TO,
                        selectedRecipients.joinToString(",")
                    )
                    message.subject = emailSubject

                    // Text part of the mail
                    val textPart = MimeBodyPart()
                    textPart.setText(emailBody, "UTF-8")

                    // PDF attachment part
                    val attachmentPart = MimeBodyPart()
                    attachmentPart.dataHandler = DataHandler(FileDataSource(pdfFile))
                    attachmentPart.fileName = pdfFile.name

                    // Combine text and attachment
                    val multipart = MimeMultipart()
                    multipart.addBodyPart(textPart)
                    multipart.addBodyPart(attachmentPart)
                    message.setContent(multipart)

                    // Send (blocking call, therefore on the IO dispatcher)
                    Transport.send(message)
                }
            }

            // Back on the main thread. If the view was destroyed in the meantime,
            // the coroutine has been cancelled and the code below does not run.

            // Re-enable the send button, also after an error
            setSending(false)

            // Show the result to the user
            result
                .onSuccess {
                    makeText(requireContext(), "E-Mail erfolgreich gesendet", Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    makeText(
                        requireContext(),
                        getString(R.string.email_send_error, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun setSending(sending: Boolean) {
        // Disable the button and dim it while a send is in progress
        binding.buttonSend.isEnabled = !sending
        binding.buttonSend.alpha = if (sending) 0.5f else 1f
    }

    private fun generateEventPdf(
        context: Context,
        meldungsdatum: String,
        parkbetreuerin: String,
        ereignisse: MutableList<List<String>>
    ): File {
        val document = PdfDocument()
        val pdfBackground = ContextCompat.getColor(context, R.color.light_yellow)
        val pdfTextColor = ContextCompat.getColor(context, R.color.purple)
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create() // A4 at 72 dpi
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

        // Layout constants
        val marginLeft = 51f
        val tableRight = 557f

        // Column widths (sum = tableRight - marginLeft = 506)
        val wFlurstueck = 55f
        val wFlur = 40f
        val wGemarkung = 75f
        val wBewirtschaftung = 90f
        val wBeginnDatum = 80f
        val wStartTime = 66f
        // "Maßnahmenende" gets the remainder: 506 - 406 = 100

        // colX[i] = left edge of column i, colX[7] = right edge of the table
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

        /** Draws the complete form header (title, subtitle, info row, table header)
         *  and returns the y position where the data rows start. */
        fun drawHeader(): Float {
            canvas.drawColor(pdfBackground)
            var y = 40f

            // Title row
            canvas.drawRect(marginLeft, y, tableRight, y + titleRowH, linePaint)
            centeredText(
                "Windpark Wasbek-Ehndorf",
                marginLeft, tableRight,
                y + titleRowH / 2f + 6f,
                titlePaint
            )
            y += titleRowH

            // Subtitle row
            canvas.drawRect(marginLeft, y, tableRight, y + subtitleRowH, linePaint)
            centeredText(
                "Mitteilung über Mahd- / Ernteereignisse",
                marginLeft, tableRight,
                y + subtitleRowH / 2f + 4f,
                subtitlePaint
            )
            y += subtitleRowH

            // Info row: label | value | label | value
            canvas.drawRect(marginLeft, y, tableRight, y + infoRowH, linePaint)
            canvas.drawLine(colX[2], y, colX[2], y + infoRowH, linePaint)
            canvas.drawLine(colX[4], y, colX[4], y + infoRowH, linePaint)
            canvas.drawLine(colX[5], y, colX[5], y + infoRowH, linePaint)

            canvas.drawText("Datum der", colX[0] + 4f, y + 15f, labelPaint)
            canvas.drawText("Meldung:", colX[0] + 4f, y + 29f, labelPaint)
            canvas.drawText(datetoday, colX[2] + 4f, y + 21f, valuePaint)
            canvas.drawText("Parkbetreuerin:", colX[4] + 4f, y + 15f, labelPaint)
            canvas.drawText("(Vertreterin)", colX[4] + 4f, y + 29f, labelPaint)
            canvas.drawText(parkbetreuerin, colX[5] + 4f, y + 21f, valuePaint) // = siteattentand
            y += infoRowH

            // Table header
            val headerTotalH = headerRow1H + headerRow2H
            canvas.drawRect(marginLeft, y, tableRight, y + headerTotalH, linePaint)

            // Vertical separators across the full header height
            for (i in 1..4) canvas.drawLine(colX[i], y, colX[i], y + headerTotalH, linePaint)
            canvas.drawLine(colX[6], y, colX[6], y + headerTotalH, linePaint)

            // Horizontal separator only below "Mahd- / Erntebeginn"
            canvas.drawLine(colX[4], y + headerRow1H, colX[6], y + headerRow1H, linePaint)
            // Date/time separator below "Mahd- / Erntebeginn"
            canvas.drawLine(colX[5], y + headerRow1H, colX[5], y + headerTotalH, linePaint)

            val midY = y + headerTotalH / 2f
            centeredText("Flurstück", colX[0], colX[1], midY + 4f, headerPaint)
            centeredText("Flur", colX[1], colX[2], midY + 4f, headerPaint)
            centeredText("Gemarkung", colX[2], colX[3], midY + 4f, headerPaint)
            centeredText("Bewirtschaftungs-", colX[3], colX[4], midY - 1f, headerPaint)
            centeredText("form", colX[3], colX[4], midY + 12f, headerPaint)

            centeredText(
                "Mahd- / Erntebeginn",
                colX[4],
                colX[6],
                y + headerRow1H / 2f + 4f,
                headerPaint
            )
            centeredText(
                "Datum",
                colX[4],
                colX[5],
                y + headerRow1H + headerRow2H / 2f + 4f,
                headerPaint
            )
            centeredText(
                "Uhrzeit",
                colX[5],
                colX[6],
                y + headerRow1H + headerRow2H / 2f + 4f,
                headerPaint
            )

            // "Maßnahmenende": single column spanning the full header height
            centeredText("Maßnahmenende", colX[6], colX[7], midY + 4f, headerPaint)

            return y + headerTotalH
        }

        var y = drawHeader()

        // One row per entry; a new page is added automatically when the current one is full
        for (e in ereignisse) { // e = List<String> with 7 entries
            if (y + dataRowH > pageBottomLimit) {
                document.finishPage(page)
                page = document.startPage(pageInfo)
                canvas = page.canvas
                y = drawHeader() // Repeat the header on every new page
            }

            canvas.drawRect(marginLeft, y, tableRight, y + dataRowH, linePaint)
            for (idx in 1 until columnCount) canvas.drawLine(
                colX[idx],
                y,
                colX[idx],
                y + dataRowH,
                linePaint
            )

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

        // Saved to the app's temporary cache directory
        val file = File(context.cacheDir, "Mahdereignis_$startDate.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return file
    }

    private fun parseParcels(entries: List<String>): List<ParcelInfo> {
        return entries
            .flatMap { it.split(",") }   // "A,B,C" -> A, B, C
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { part ->
                val match = parcelRegex.matchEntire(part)
                if (match == null) {
                    android.util.Log.w("Parcel", "Ungültiges Format: '$part'")
                    null
                } else {
                    val (g, f, fs) = match.destructured
                    ParcelInfo(gemarkung = g, flur = f, flurstueck = fs)
                }
            }
            .distinct()                  // doppelte Einträge entfernen
    }


}