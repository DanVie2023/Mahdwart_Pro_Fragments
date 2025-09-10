package com.viedan.mahdwartpro_fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputBinding
import com.viedan.mahdwartpro_fragments.databinding.FragmentMahdwartBinding
// OSM (osmdroid) Imports
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import androidx.preference.PreferenceManager


class MahdwartFragment : Fragment() {

    private lateinit var binding: FragmentMahdwartBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMahdwartBinding.inflate(inflater, container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        setupClickListeners()
        // OSM Konfiguration laden
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )

        val map = binding.map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Startposition setzen (z.B. Berlin)
        val mapController = map.controller
        mapController.setZoom(12.0)
        val startPoint = GeoPoint(52.5200, 13.4050)
        mapController.setCenter(startPoint)
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume() // wichtig für OSM
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause() // wichtig für OSM
    }

    override fun onDestroyView() {
        super.onDestroyView()
        //binding = null
    }
}