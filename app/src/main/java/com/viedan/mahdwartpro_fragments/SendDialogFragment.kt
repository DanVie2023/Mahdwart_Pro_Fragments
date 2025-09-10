package com.viedan.mahdwartpro_fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.viedan.mahdwartpro_fragments.databinding.FragmentSendDialogBinding

class SendDialogFragment : Fragment() {
    private lateinit var binding: FragmentSendDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentSendDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

}