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

import android.widget.NumberPicker

/**
 * Main screen of the app.
 *
 * Shows a map of the wind farm Wasbek-Ehndorf. The user selects one or more parcels on the
 * map, a date, a start time and the type of farming ("Mahd" or "Ernte") and then opens
 * [SendDialogFragment] to send the notification by e-mail.
 *
 * Data sources (both in the app's assets):
 * - `WasbekEhndorfProjectfile.geojson`: parcel polygons (`MultiPolygon` features with the
 *   properties `wtg` and `parcel`) and metadata (park attendant, project e-mail contacts)
 * - `points_only.geojson`: positions of the wind turbines (`Point` features)
 *
 * The selection is kept in fields of this fragment. While [SendDialogFragment] is on top,
 * the view of this fragment is destroyed and later rebuilt, so the selection is re-applied
 * to the polygons when the map is loaded again. It is cleared when the fragment result
 * `"reset_selection"` is received.
 */
@Suppress("DEPRECATION")
class MahdwartFragment : Fragment() {

    private var _binding: FragmentMahdwartBinding? = null

    /** Only valid between [onCreateView] and [onDestroyView]. */
    private val binding get() = _binding!!

    /** Wind turbines (WTG numbers) affected by the currently selected parcels. */
    private val selectedWtgs = mutableSetOf<String>()

    /** Raw `parcel` property of each selected polygon. One entry can contain several */
    private val selectedParsels = mutableListOf<String>()

    /** Lookup: single parcel name (e.g. `"G4572-F12-FS46"`) to the WTG numbers it affects. */
    private val parcelToWtgs = mutableMapOf<String, Set<String>>()

    /** Project-wide e-mail contacts from the GeoJSON metadata (JSON array of name/address). */
    private var projectContacts = JSONArray()

    /** Start date chosen in the calendar; defaults to today. */
    private var selectedDate: LocalDate = LocalDate.now()

    /** Name of the park attendant from the GeoJSON metadata, printed on the PDF. */
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

        // Cleared by SendDialogFragment after sending or closing
        parentFragmentManager.setFragmentResultListener(
            "reset_selection",
            viewLifecycleOwner
        ) { _, _ ->
            resetSelection()
        }

        setupClickListeners()

        binding.calendarView3.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // month is 0-based here (January = 0)
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
        }

        // osmdroid needs its configuration (tile cache, user agent) loaded before the map is used
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )

        val map = binding.map

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        loadProjectData(map)

        map.setMinZoomLevel(10.0)
        map.controller.apply {
            setZoom(15.0)
            setCenter(GeoPoint(54.063065, 9.878683))
        }

        // Restrict the minute picker to quarter hours (00, 15, 30, 45).
        // The inner "minute" view only exists in the spinner style of the TimePicker.
        val timePickerStart = binding.timePickerStart

        val minuteId = resources.getIdentifier("minute", "id", "android")

        val minutePicker = timePickerStart.findViewById<NumberPicker>(minuteId)

        minutePicker?.apply {
            minValue = 0
            maxValue = 3
            displayedValues = arrayOf("00", "15", "30", "45")
        }

        loadWindmills(map)
        map.invalidate()
    }

    /**
     * Loads the parcel polygons and the metadata from the project GeoJSON and adds the
     * polygons as clickable overlays to [map].
     *
     * Fills [parcelToWtgs], [siteattendant] and [projectContacts]. Only features of type
     * `MultiPolygon` are used; others are skipped. Errors while reading or parsing are
     * logged and reported with a toast instead of crashing the app.
     *
     * @param map The map the polygons are added to.
     */
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

                // Remember which WTGs each single parcel affects
                parcels.forEach { parcelName ->
                    parcelToWtgs[parcelName] =
                        parcelToWtgs[parcelName].orEmpty() + wtgs
                }

                val multiPolygon = geometry.getJSONArray("coordinates")

                for (p in 0 until multiPolygon.length()) {

                    // Only the outer ring is used; holes are ignored.
                    // GeoJSON order is [longitude, latitude].
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

                    // Re-apply the selection after the view has been rebuilt
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

    /**
     * Colors [polygon] as selected (yellow) or unselected (blue-grey).
     */
    private fun applyPolygonStyle(polygon: Polygon, selected: Boolean) {
        if (selected) {
            polygon.strokeColor = SELECTED_STROKE
            polygon.fillColor = 0x55FFC107
        } else {
            polygon.strokeColor = 0xFF2A6274.toInt()
            polygon.fillColor = 0x555993A7
        }
    }

    /**
     * Calculates the wind turbines affected by the currently selected parcels.
     *
     * Entries in [selectedParsels] may contain several comma-separated parcels, so they are
     * split first, otherwise the lookup in [parcelToWtgs] would not find them.
     *
     * @return The WTG numbers without duplicates, in the order they were found.
     */
    private fun collectWtgs(): Set<String> =
        selectedParsels
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMapTo(linkedSetOf()) { parcelToWtgs[it].orEmpty() }

    /** Returns the selected start date formatted as `dd.MM.yyyy`. */
    private fun getSelectedDate(): String =
        selectedDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

    /** Returns the time of [timePicker] formatted as `HH:mm`. */
    private fun getTime(timePicker: TimePicker): String {
        return "%02d:%02d".format(
            timePicker.hour,
            timePicker.minute * 15
        )
    }

    /**
     * Sets up the "send" button. It validates the input (at least one parcel, a type of
     * farming) and then opens [SendDialogFragment] with the selection as arguments.
     */
    private fun setupClickListeners() {

        binding.buttonSelected.setOnClickListener {

            // At least one parcel has to be selected
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

            /// Do not continue without a type of farming (Bewirtschaftungsform)
            if (agriculturalType.isEmpty()) {

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.select_type_title)
                    .setMessage(R.string.select_type_message)
                    .setPositiveButton(R.string.understood, null)
                    .show()

                return@setOnClickListener
            }

            // Recalculate right before sending so the list is never stale
            selectedWtgs.clear()
            selectedWtgs.addAll(collectWtgs())
            if (selectedWtgs.isEmpty()) {
                Log.w("MahdwartDebug", "Keine WEA für $selectedParsels, Keys: ${parcelToWtgs.keys}")
            }
            val fragment = SendDialogFragment()

            val startDate = getSelectedDate()
            val startTime = getTime(binding.timePickerStart)

            // Keys must match the ones read in SendDialogFragment.onCreate()
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

    /**
     * Loads the wind turbine positions from `points_only.geojson` and adds a marker for
     * each `Point` feature to [map]. Tapping a marker shows a dialog with the turbine's
     * details (type, Gemarkung, Flur, Flurstück).
     *
     * Errors while reading or parsing are logged and reported with a toast.
     *
     * @param map The map the markers are added to.
     */
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

                    // Scale the icon to 40x40 px for the map
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

    /**
     * Clears the whole selection (parcels, WTGs, date) and resets the polygon colors.
     * Does nothing if the view no longer exists.
     */
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

    // Release the map's resources and drop the binding to avoid leaking the view
    override fun onDestroyView() {
        _binding?.map?.onDetach()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        /** Stroke color of a selected polygon; also used to detect the selected state. */
        const val SELECTED_STROKE = 0xFFFFC107.toInt()
    }
}
