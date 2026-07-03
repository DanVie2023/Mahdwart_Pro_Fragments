package com.viedan.mahdwartpro_fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.makeText
import com.viedan.mahdwartpro_fragments.databinding.FragmentSendDialogBinding
import java.io.File
import java.io.FileOutputStream

class SendDialogFragment : Fragment() {

    private lateinit var binding: FragmentSendDialogBinding
    private var wtgs: List<String> = emptyList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wtgs = arguments?.getStringArrayList("wtgs") ?: emptyList()

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSendDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.InputTextEmailBody.setText(
            wtgs.joinToString("\n")
        )

        binding.InputTextEmailSubject.setText(
            wtgs.joinToString("\n")
        )

        binding.buttonClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.buttonSend.setOnClickListener {
            val recipient = binding.listEmail.text.toString()
            val subject = binding.InputTextEmailSubject.text.toString()
            val message = binding.InputTextEmailBody.text.toString()

            val mIntent = Intent(Intent.ACTION_SEND)

            mIntent.data = Uri.parse("mailto:")
            mIntent.type = "text/plain"
            mIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            mIntent.putExtra(Intent.EXTRA_SUBJECT, subject)
            mIntent.putExtra(Intent.EXTRA_TEXT, message)

            try {
                startActivity(mIntent)
            } catch (e: ActivityNotFoundException){
                Toast.makeText(requireContext(), "Keine E-Mail-App installiert.",Toast.LENGTH_LONG).show()
            }
        }
    }

    //create a pdf-file
    private fun createPdf(){

        val pdfDocument = PdfDocument()

        val pageinfo = PdfDocument.PageInfo.Builder(
            595, //A4 with 72dpi
            842,
            1
        ).create()

        //Drawing Area
        val page = pdfDocument.startPage(pageinfo)

        val canvas = page.canvas
        val paint = Paint()

        paint.textSize = 18f

        canvas.drawText("First PDF-Line", 50f, 50f, paint)
        canvas.drawText("Test", 50f, 50f, paint)

        pdfDocument.finishPage(page)

        // Save File to "Android/data/<Package Name>/files/MyPDF.pdf
        val file = File(requireContext().getExternalFilesDir(null), "MyPDF.pdf")

        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

    }
}