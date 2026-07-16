package com.viedan.mahdwartpro_fragments

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.viedan.mahdwartpro_fragments.databinding.FragmentMahdwartBinding
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.TimePicker
import android.widget.Toast.LENGTH_LONG


class MahdwartFragment : Fragment() {


    private lateinit var binding: FragmentMahdwartBinding
    private val selectedWtgs = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMahdwartBinding.inflate(inflater, container, false)
        """binding.materialTimepickerView.setIs24HourView(true) """
        return binding.root
    }



    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()

        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )

        val map = binding.map

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)



        val geoJsonString = requireContext()
            .assets
            .open("edited_fields.geojson")
            .bufferedReader()
            .use { it.readText() }

        val geoJson = JSONObject(geoJsonString)
        val features = geoJson.getJSONArray("features")

        for (i in 0 until features.length()) {

            val feature = features.getJSONObject(i)
            val geometry = feature.getJSONObject("geometry")
            val props = feature.optJSONObject("properties")

            if (geometry.getString("type") != "MultiPolygon")
                continue

            val wtg = props?.optString("wtg") ?: "unknown"
            val multiPolygon = geometry.getJSONArray("coordinates")

            for (p in 0 until multiPolygon.length()) {

                val polygonCoords = multiPolygon.getJSONArray(p)
                val outerRing = polygonCoords.getJSONArray(0)
                val points = mutableListOf<GeoPoint>()

                for (j in 0 until outerRing.length()) {

                    val coord = outerRing.getJSONArray(j)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)

                    points.add(
                        GeoPoint(lat, lon)
                    )
                }

                if (points.first() != points.last()) {
                    points.add(points.first())
                }

                val polygon = Polygon(map)

                polygon.points = points

                polygon.fillColor = 0x555993A7
                polygon.strokeColor = 0xFF2A6274.toInt()
                polygon.strokeWidth = 4f

                polygon.setOnClickListener { _, _, _ ->

                    val selectedBorder = 0xFFFFC107.toInt()
                    val selectedFill = 0x55FFC107

                    if (polygon.strokeColor == selectedBorder) {

                        polygon.strokeColor = 0xFF2A6274.toInt()
                        polygon.fillColor = 0x555993A7
                        wtg.split(",")
                            .map { it.trim() }
                            .forEach {
                                selectedWtgs.remove(it)
                            }

                    } else {

                        polygon.strokeColor = selectedBorder
                        polygon.fillColor = selectedFill
                        wtg.split(",")
                            .map { it.trim() }
                            .forEach {
                                selectedWtgs.add(it)
                            }
                    }

                    map.invalidate()
                    true
                }

                map.overlays.add(polygon)
            }
        }

        map.controller.apply {
            setZoom(16.0)
            setCenter(
                GeoPoint(
                    54.063065,
                    9.878683
                )
            )
        }

        map.invalidate()
        loadWindmills(map)

    }

    private fun getSelectedDate(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = binding.calendarView3.date

        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val year = calendar.get(java.util.Calendar.YEAR)

        return "%02d.%02d.%d".format(day, month, year)
    }

    private fun getTime(timePicker: TimePicker): String {
        return "%02d:%02d".format(
            timePicker.hour,
            timePicker.minute
        )
    }

    private fun setupClickListeners() {

        binding.buttonSelected.setOnClickListener {

            val fragment = SendDialogFragment()

            val date = getSelectedDate()

            val startTime = getTime(binding.timePickerStart)

            val endTime = getTime(binding.timePickerEnd)


            fragment.arguments = Bundle().apply {

                putStringArrayList(
                    "wtgs",
                    ArrayList(selectedWtgs)
                )

                putString(
                    "date",
                    date
                )

                putString(
                    "startTime",
                    startTime
                )

                putString(
                    "endTime",
                    endTime
                )
            }


            parentFragmentManager
                .beginTransaction()
                .replace(
                    R.id.frame_content,
                    fragment
                )
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loadWindmills(map: MapView) {

        val json =
            requireContext()
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

                val drawable = ContextCompat.getDrawable(requireContext(),R.drawable.windkraftanlage)

                val scaled =
                    drawable?.let {

                        val bitmap = (it as BitmapDrawable).bitmap

                        val scaledBitmap =
                            Bitmap.createScaledBitmap(bitmap, 40, 40, false)
                            BitmapDrawable(resources, scaledBitmap)
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
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }
}