package com.viedan.mahdwartpro_fragments

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TimePicker
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.viedan.mahdwartpro_fragments.databinding.FragmentMahdwartBinding
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Suppress("DEPRECATION")
class MahdwartFragment : Fragment() {

    private var _binding: FragmentMahdwartBinding? = null
    private val binding get() = _binding!!

    private val selectedWtgs = mutableSetOf<String>()
    private val selectedParsels = mutableListOf<String>()

    private val parcelToWtgs = mutableMapOf<String, Set<String>>()

    // Allgemeine Kontakte des Projekts
    private var projectContacts = JSONArray()

    private var selectedDate: LocalDate = LocalDate.now()
    private var siteattendant: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMahdwartBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parentFragmentManager.setFragmentResultListener(
            "reset_selection",
            viewLifecycleOwner
        ) { _, _ ->
            resetSelection()
        }

        setupClickListeners()

        binding.calendarView3.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // month ist hier 0-basiert (Januar = 0)
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
        }

        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )

        val map = binding.map

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        loadProjectData(map)

        map.controller.apply {
            setZoom(16.0)
            setCenter(GeoPoint(54.063065, 9.878683))
        }

        loadWindmills(map)
        map.invalidate()
    }

    /** Loading data from GeoJSON-Datei.*/
    private fun loadProjectData(map: MapView) {
        try {
            val geoJsonString = requireContext()
                .assets
                .open("WasbekEhndorfProjectfile.geojson")
                .bufferedReader()
                .use { it.readText() }

            val geoJson = JSONObject(geoJsonString)

            val metadata = geoJson.getJSONObject("metadata")

            siteattendant = metadata.optString("siteattendant", "")
            projectContacts = metadata.optJSONArray("email") ?: JSONArray()

            val features = geoJson.getJSONArray("features")

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val geometry = feature.getJSONObject("geometry")
                val props = feature.getJSONObject("properties")

                if (geometry.getString("type") != "MultiPolygon") {
                    continue
                }

                val wtg = props.optString("wtg")
                val parcel = props.optString("parcel")

                val parcels = parcel.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val wtgs = wtg.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()

                parcels.forEach { parcelName ->
                    parcelToWtgs[parcelName] =
                        parcelToWtgs[parcelName].orEmpty() + wtgs
                }

                val multiPolygon = geometry.getJSONArray("coordinates")

                for (p in 0 until multiPolygon.length()) {

                    val polygonCoords = multiPolygon.getJSONArray(p)
                    val outerRing = polygonCoords.getJSONArray(0)
                    val points = mutableListOf<GeoPoint>()

                    for (j in 0 until outerRing.length()) {
                        val coord = outerRing.getJSONArray(j)
                        points.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
                    }

                    if (points.isEmpty()) continue

                    if (points.first() != points.last()) {
                        points.add(points.first())
                    }

                    val polygon = Polygon(map)
                    polygon.points = points
                    polygon.strokeWidth = 4f

                    // selected Areas repare after view-rebuild
                    applyPolygonStyle(polygon, parcel in selectedParsels)

                    polygon.setOnClickListener { _, _, _ ->

                        if (polygon.strokeColor == SELECTED_STROKE) {
                            applyPolygonStyle(polygon, false)
                            selectedParsels.remove(parcel)
                        } else {
                            applyPolygonStyle(polygon, true)
                            if (parcel !in selectedParsels) {
                                selectedParsels.add(parcel)
                            }
                        }

                        selectedWtgs.clear()
                        selectedWtgs.addAll(collectWtgs())

                        map.invalidate()
                        true
                    }

                    map.overlays.add(polygon)
                }
            }
        } catch (e: Exception) {
            Log.e("Mahdwart", "Projektdatei konnte nicht geladen werden", e)
            Toast.makeText(
                requireContext(),
                R.string.error_geojson_load,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun applyPolygonStyle(polygon: Polygon, selected: Boolean) {
        if (selected) {
            polygon.strokeColor = SELECTED_STROKE
            polygon.fillColor = 0x55FFC107
        } else {
            polygon.strokeColor = 0xFF2A6274.toInt()
            polygon.fillColor = 0x555993A7
        }
    }

    private fun collectWtgs(): Set<String> =
        selectedParsels
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMapTo(linkedSetOf()) { parcelToWtgs[it].orEmpty() }
    private fun getSelectedDate(): String =
        selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

    private fun getTime(timePicker: TimePicker): String {
        return "%02d:%02d".format(
            timePicker.hour,
            timePicker.minute
        )
    }

    private fun setupClickListeners() {

        binding.buttonSelected.setOnClickListener {

            if (selectedParsels.isEmpty()) {

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.select_parcel_title)
                    .setMessage(R.string.select_parcel_message)
                    .setPositiveButton(R.string.understood, null)
                    .show()

                return@setOnClickListener
            }

            val agriculturalType = when (binding.toggleType.checkedButtonId) {
                R.id.radioGrass -> "Mahd"
                R.id.radioFarming -> "Ernte"
                else -> ""
            }

            // Stops moving forward without selecting Bewirtschaftungsform
            if (agriculturalType.isEmpty()) {

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.select_type_title)
                    .setMessage(R.string.select_type_message)
                    .setPositiveButton(R.string.understood, null)
                    .show()

                return@setOnClickListener
            }

            selectedWtgs.clear()
            selectedWtgs.addAll(collectWtgs())
            if (selectedWtgs.isEmpty()) {
                Log.w("MahdwartDebug", "Keine WEA für $selectedParsels, Keys: ${parcelToWtgs.keys}")
            }
            val fragment = SendDialogFragment()

            val startDate = getSelectedDate()
            val startTime = getTime(binding.timePickerStart)

            fragment.arguments = Bundle().apply {
                putStringArrayList("wtgs", ArrayList(selectedWtgs))
                putStringArrayList("parcel", ArrayList(selectedParsels))
                putString("contacts", projectContacts.toString())
                putString("startDate", startDate)
                putString("startTime", startTime)
                putString("agriculturalType", agriculturalType)
                putString("siteattendant", siteattendant)
            }

            Log.d("MahdwartDebug", "WTGs: $selectedWtgs")
            Log.d("MahdwartDebug", "Parcels: $selectedParsels")
            Log.d("MahdwartDebug", "Start date: $startDate")
            Log.d("MahdwartDebug", "Start time: $startTime")
            Log.d("MahdwartDebug", "Agricultural type: $agriculturalType")
            Log.d("MahdwartDebug", "Site attendant: $siteattendant")
            Log.d("MahdwartDebug", "Email: $projectContacts")

            parentFragmentManager
                .beginTransaction()
                .replace(R.id.frame_content, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loadWindmills(map: MapView) {
        try {
            val json = requireContext()
                .assets
                .open("points_only.geojson")
                .bufferedReader()
                .use { it.readText() }

            val geoJson = JSONObject(json)
            val features = geoJson.getJSONArray("features")

            for (i in 0 until features.length()) {

                val feature = features.getJSONObject(i)
                val geometry = feature.getJSONObject("geometry")
                val properties = feature.getJSONObject("properties")

                if (geometry.getString("type") == "Point") {

                    val coordinates = geometry.getJSONArray("coordinates")
                    val lon = coordinates.getDouble(0)
                    val lat = coordinates.getDouble(1)
                    val point = GeoPoint(lat, lon)
                    val weaNr = properties.optInt("WEA_Nr")
                    val weaType = properties.optString("WEA_Typ")
                    val gemarkung = properties.optString("Gemarkung")
                    val flur = properties.optInt("Flur")
                    val flurstueck = properties.optString("Flurstueck")

                    val drawable =
                        ContextCompat.getDrawable(requireContext(), R.drawable.windkraftanlage)

                    val scaled =
                        drawable?.let {
                            val bitmap = (it as BitmapDrawable).bitmap
                            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 40, 40, false)
                            scaledBitmap.toDrawable(resources)
                        }

                    val marker =
                        Marker(map).apply {
                            position = point
                            setAnchor(
                                Marker.ANCHOR_CENTER,
                                Marker.ANCHOR_BOTTOM
                            )
                            title = "WEA $weaNr"
                            icon = scaled
                        }

                    marker.setOnMarkerClickListener { _, _ ->

                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("WEA $weaNr")
                            .setIcon(R.drawable.windkraftanlage)
                            .setMessage(
                                """
                                Typ: $weaType

                                Gemarkung: $gemarkung
                                Flur: $flur
                                Flurstück: $flurstueck
                                """.trimIndent()
                            )
                            .setPositiveButton("Schließen", null)
                            .show()

                        true
                    }

                    map.overlays.add(marker)
                }
            }
        } catch (e: Exception) {
            Log.e("Mahdwart", "Windkraftanlagen konnten nicht geladen werden", e)
            Toast.makeText(
                requireContext(),
                R.string.error_geojson_load,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun resetSelection() {
        val b = _binding ?: return

        selectedWtgs.clear()
        selectedParsels.clear()

        selectedDate = LocalDate.now()
        b.calendarView3.date = System.currentTimeMillis()

        // reset polygon
        b.map.overlays
            .filterIsInstance<Polygon>()
            .forEach { applyPolygonStyle(it, false) }

        b.map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        _binding?.map?.onResume()
    }

    override fun onPause() {
        super.onPause()
        _binding?.map?.onPause()
    }

    override fun onDestroyView() {
        _binding?.map?.onDetach()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val SELECTED_STROKE = 0xFFFFC107.toInt()
    }
}
