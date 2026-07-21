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

    private fun sendTestEmail() {

        Thread {
            try {

                val emailSubject = binding.InputTextEmailSubject.text.toString()
                val emailBody = binding.InputTextEmailBody.text.toString()

                val properties = Properties()
                properties["mail.smtp.host"] = "smtp.gmail.com"
                properties["mail.smtp.port"] = "587"
                properties["mail.smtp.auth"] = "true"
                properties["mail.smtp.starttls.enable"] = "true"

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

                message.setFrom(
                    InternetAddress(
                        "Mahdwart@gmail.com"
                    )
                )

                message.setRecipients(
                    Message.RecipientType.TO,
                    "a.bohdan@e3-gmbh.de, d.vieler@e3-gmbh.de"
                )

                message.subject = emailSubject

                message.setText(
                    emailBody,
                    "UTF-8"
                )

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
}