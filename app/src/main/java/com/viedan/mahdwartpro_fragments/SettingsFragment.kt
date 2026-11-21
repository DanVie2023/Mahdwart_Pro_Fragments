package com.viedan.mahdwartpro_fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.viedan.mahdwartpro_fragments.databinding.FragmentSettingsBinding

/**
 * Settings screen of the app.
 *
 * Currently only a placeholder: it inflates its layout ([FragmentSettingsBinding]) but
 * does not contain any logic yet.
 */
class SettingsFragment : Fragment() {

    private lateinit var binding: FragmentSettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: Set up the settings UI (listeners, stored values) here once it
    }

}