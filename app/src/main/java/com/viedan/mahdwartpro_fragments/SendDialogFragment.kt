package com.viedan.mahdwartpro_fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
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
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.viedan.mahdwartpro_fragments.databinding.FragmentSendDialogBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

class SendDialogFragment : Fragment() {

    private var _binding: FragmentSendDialogBinding? = null
    private val binding get() = _binding!!
    private var wtgs: List<String> = emptyList()
    private var parcels: List<String> = emptyList()
    private var datetoday: String = ""
    private var startDate: String = ""
    private var startTime: String = ""
    private var endDate: String = "DateUnknown"
    private var siteattendant: String = ""
    private var agriculturalType: String = ""
    private val recipients = mutableListOf<Recipient>()
    private var projectContactsJson: String = "[]"

    private val testEreignisse: MutableList<List<String>> = mutableListOf()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val parcelRegex = Regex(
        """^G\s*([^-]+?)\s*-\s*F\s*([^-]+?)\s*-\s*FS\s*(.+?)$""",
        RegexOption.IGNORE_CASE
    )

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

    private companion object {
        const val SMTP_USER = "Mahdwart@gmail.com"
        const val SMTP_PASSWORD = "ozes qscp asrd lbyz"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wtgs = arguments?.getStringArrayList("wtgs") ?: emptyList()
        parcels = arguments?.getStringArrayList("parcel") ?: emptyList()
        datetoday = LocalDate.now().format(dateFormatter)
        startDate = arguments?.getString("startDate") ?: ""
        startTime = arguments?.getString("startTime") ?: ""
        //agriculturalType has to be placed BEFORE endDate!
        agriculturalType = arguments?.getString("agriculturalType") ?: ""
        siteattendant = arguments?.getString("siteattendant") ?: ""
        projectContactsJson = arguments?.getString("contacts") ?: "[]"

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
            getString(R.string.SDFkt_Mailtitle)
        )

        binding.InputTextEmailBody.setText(
            buildString {
                appendLine(getString(R.string.SDFkt_Mailtext_Line1))
                appendLine()
                appendLine(getString(R.string.SDFkt_Mailtext_Line2, agriculturalType))
                appendLine()
                appendLine(getString(R.string.SDFkt_Mailtext_Line3, startDate))
                appendLine(getString(R.string.SDFkt_Mailtext_Line4, startTime))
                appendLine()
                appendLine(getString(R.string.SDFkt_Mailtext_Line5, endDate))
                appendLine()
                wtgs.forEach {
                    appendLine(getString(R.string.SDFkt_Mailtext_Line6, it))
                }
                appendLine()
                appendLine(getString(R.string.SDFkt_Mailtext_Line7))
                appendLine()
                appendLine(getString(R.string.SDFkt_Mailtext_Line8))
            }
        )

        loadRecipients()
        displayRecipients()

        binding.buttonAddRecipient.setOnClickListener { showRecipientDialog() }
        binding.buttonSend.setOnClickListener { sendEmail() }
        binding.buttonClose.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                "reset_selection",
                Bundle.EMPTY
            )
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadRecipients() {

        val prefs = requireContext().getSharedPreferences(
            "recipients",
            Context.MODE_PRIVATE
        )

        val savedJson = prefs.getString("list", "[]") ?: "[]"

        recipients.clear()

        fun addRecipientIfMissing(
            name: String,
            email: String,
            selected: Boolean
        ) {
            val cleanName = name.trim()
            val cleanEmail = email.trim()

            if (cleanEmail.isEmpty()) return

            val existingIndex = recipients.indexOfFirst {
                it.email.trim().equals(cleanEmail, ignoreCase = true)
            }

            if (existingIndex == -1) {

                recipients.add(
                    Recipient(
                        name = cleanName,
                        email = cleanEmail,
                        selected = selected
                    )
                )

            } else {

                if (recipients[existingIndex].name.isBlank() && cleanName.isNotBlank()) {
                    recipients[existingIndex].name = cleanName
                }

                recipients[existingIndex].selected =
                    recipients[existingIndex].selected || selected
            }
        }

        try {
            val savedArray = JSONArray(savedJson)

            for (i in 0 until savedArray.length()) {

                val obj = savedArray.getJSONObject(i)

                addRecipientIfMissing(
                    name = obj.optString("name"),
                    email = obj.optString("email"),
                    selected = obj.optBoolean("selected", false)
                )
            }

        } catch (e: Exception) {
            android.util.Log.e("Recipients", "Error loading saved recipients", e)
        }

        val excludedProjectEmails = prefs
            .getStringSet("excluded_project_emails", emptySet())
            ?.map { it.trim().lowercase() }
            ?.toSet()
            ?: emptySet()

        try {
            val projectArray = JSONArray(projectContactsJson)

            for (i in 0 until projectArray.length()) {

                val obj = projectArray.getJSONObject(i)

                val name = obj.optString("name").trim()
                val address = obj.optString("address").trim()

                if (address.lowercase() in excludedProjectEmails) {
                    continue
                }

                addRecipientIfMissing(
                    name = name,
                    email = address,
                    selected = false
                )
            }

        } catch (e: Exception) {
            android.util.Log.e("Recipients", "Error loading GeoJSON contacts", e)
        }

        saveRecipients()
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

                    setOnCheckedChangeListener { _, checked ->
                        recipient.selected = checked
                        saveRecipients()
                    }
                }

            val text =
                TextView(requireContext()).apply {

                    text = "${recipient.name}\n${recipient.email}"
                    textSize = 15f

                    setTextColor(Color.rgb(30, 41, 59))

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
    }

    private fun isGeoJsonRecipient(email: String): Boolean {

        return try {
            val array = JSONArray(projectContactsJson)

            (0 until array.length()).any { i ->
                array.getJSONObject(i)
                    .optString("address")
                    .trim()
                    .equals(email.trim(), ignoreCase = true)
            }

        } catch (e: Exception) {
            false
        }
    }

    private fun rememberDeletedProjectRecipient(email: String) {

        if (!isGeoJsonRecipient(email)) return

        val prefs = requireContext().getSharedPreferences(
            "recipients",
            Context.MODE_PRIVATE
        )

        val excluded = prefs
            .getStringSet("excluded_project_emails", emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()

        excluded.add(email.trim().lowercase())

        prefs.edit {
            putStringSet("excluded_project_emails", excluded)
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

                val name = nameInput.text?.toString()?.trim() ?: ""
                val email = emailInput.text?.toString()?.trim() ?: ""

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

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    makeText(
                        requireContext(),
                        getString(R.string.error_invalid_email),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val duplicateIndex = recipients.indexOfFirst {
                    it.email.trim().equals(email, ignoreCase = true)
                }

                if (duplicateIndex != -1 && duplicateIndex != editIndex) {
                    makeText(
                        requireContext(),
                        "Diese E-Mail-Adresse ist bereits in der Liste.",
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

                    val oldEmail = recipients[editIndex].email

                    if (!oldEmail.equals(email, ignoreCase = true)) {
                        rememberDeletedProjectRecipient(oldEmail)
                    }

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

        val recipient = recipients[index]

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.recipient_delete)
            .setMessage("${recipient.name}\n${recipient.email}")
            .setNegativeButton(R.string.recipient_cancel, null)
            .setPositiveButton(R.string.recipient_delete) { _, _ ->

                rememberDeletedProjectRecipient(recipient.email)

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

        // Block sending when no connection
        if (!NetworkUtils.isOnline(requireContext())) {
            makeText(
                requireContext(),
                R.string.error_no_internet,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val appContext = requireContext().applicationContext
        val emailSubject = binding.InputTextEmailSubject.text.toString()
        val emailBody = binding.InputTextEmailBody.text.toString()

        // Block Send-Button while sending
        setSending(true)

        viewLifecycleOwner.lifecycleScope.launch {

            // PDF and SMTP-sending exculded from Main-Thread
            val result: Result<Unit> = withContext(Dispatchers.IO) {
                try {
                    sendMail(appContext, selectedRecipients, emailSubject, emailBody)
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            setSending(false)

            result
                .onSuccess {

                    makeText(
                        requireContext(),
                        "E-Mail erfolgreich gesendet",
                        Toast.LENGTH_LONG
                    ).show()

                    // Reset MahdwartFragment selection
                    parentFragmentManager.setFragmentResult(
                        "reset_selection",
                        Bundle.EMPTY
                    )

                    // Close Dialog after successfull sending
                    parentFragmentManager.popBackStack()
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
    private fun sendMail(
        appContext: Context,
        to: List<String>,
        subject: String,
        body: String
    ) {
        // Generate PDF
        val pdfFile = EventPdfGenerator(appContext).generate(
            reportDate = datetoday,
            parkAttendant = siteattendant,
            events = testEreignisse,
            fileDate = startDate
        )

        // SMTP-Konfiguration
        val properties = Properties()
        properties["mail.smtp.host"] = "smtp.gmail.com"
        properties["mail.smtp.port"] = "587"
        properties["mail.smtp.auth"] = "true"
        properties["mail.smtp.starttls.enable"] = "true"
        properties["mail.smtp.starttls.required"] = "true"
        properties["mail.smtp.ssl.trust"] = "smtp.gmail.com"
        properties["mail.smtp.ssl.protocols"] = "TLSv1.2"
        // Timeouts, for long sending with slow connection
        properties["mail.smtp.connectiontimeout"] = "10000"
        properties["mail.smtp.timeout"] = "15000"
        properties["mail.smtp.writetimeout"] = "15000"

        val session = Session.getInstance(
            properties,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(SMTP_USER, SMTP_PASSWORD)
                }
            }
        )

        val message = MimeMessage(session)
        message.setFrom(InternetAddress(SMTP_USER))
        message.setRecipients(Message.RecipientType.TO, to.joinToString(","))
        message.subject = subject

        val textPart = MimeBodyPart()
        textPart.setText(body, "UTF-8")

        val attachmentPart = MimeBodyPart()
        attachmentPart.dataHandler = DataHandler(FileDataSource(pdfFile))
        attachmentPart.fileName = pdfFile.name

        val multipart = MimeMultipart()
        multipart.addBodyPart(textPart)
        multipart.addBodyPart(attachmentPart)
        message.setContent(multipart)

        Transport.send(message)
    }

    private fun setSending(sending: Boolean) {
        _binding?.buttonSend?.apply {
            isEnabled = !sending
            alpha = if (sending) 0.5f else 1f
        }
    }

    private fun parseParcels(entries: List<String>): List<ParcelInfo> {
        return entries
            .asSequence()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { part ->
                val match = parcelRegex.matchEntire(part)
                if (match != null) {
                    val (g, f, fs) = match.destructured
                    ParcelInfo(gemarkung = g, flur = f, flurstueck = fs)
                } else {
                    android.util.Log.w("Parcel", "Ungültiges Format: '$part'")
                    ParcelInfo(gemarkung = "?", flur = "?", flurstueck = part)
                }
            }
            .distinct()
            .toList()
    }
}
