package com.viedan.mahdwartpro_fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast.LENGTH_LONG
import com.google.android.material.bottomsheet.BottomSheetDialog


class MahdwartFragment : Fragment() {

    private lateinit var binding: FragmentMahdwartBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMahdwartBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val colorMap = mutableMapOf<String, Int>()

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun getColor(key: String): Int {

        return colorMap.getOrPut(key) {

            val seed = key.hashCode()
            val random = java.util.Random(seed.toLong())

            val r = random.nextInt(80, 220)
            val g = random.nextInt(80, 220)
            val b = random.nextInt(80, 220)

            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )

        val map = binding.map

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)


        // POLYGONS
        val geoJsonString = requireContext().assets
            .open("edited_fields.geojson")
            .bufferedReader()
            .use { it.readText() }

        val geoJson = JSONObject(geoJsonString)
        val features = geoJson.getJSONArray("features")
        val originalColors = mutableMapOf<Polygon, Int>()

        val selectedWtgs = mutableSetOf<String>()

        for (i in 0 until features.length()) {

            val feature = features.getJSONObject(i)

            val geometry = feature.getJSONObject("geometry")
            val props = feature.optJSONObject("properties")

            val type = geometry.getString("type")

            if (type == "LineString") {

                val coords = geometry.getJSONArray("coordinates")

                val points = mutableListOf<GeoPoint>()

                for (j in 0 until coords.length()) {

                    val coord = coords.getJSONArray(j)

                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)

                    points.add(GeoPoint(lat, lon))
                }

                if (points.isNotEmpty() && points.first() != points.last()) {
                    points.add(points.first())
                }

                val wtg = props?.optString("WTG") ?: "unknown"

                val color = getColor(wtg)

                val polygon = Polygon(map)

                polygon.setPoints(points)

                polygon.fillColor = color.withAlpha(0x55)
                polygon.strokeColor = color
                polygon.strokeWidth = 4f

                originalColors[polygon] = color

                polygon.setOnClickListener { _, _, _ ->

                    val selectedColor = 0xFF00BFFF.toInt()

                    if (polygon.strokeColor == selectedColor) {

                        val orig = originalColors[polygon]!!

                        polygon.strokeColor = orig
                        polygon.fillColor = orig.withAlpha(0x55)

                        selectedWtgs.remove(wtg)

                    } else {

                        polygon.strokeColor = selectedColor
                        polygon.fillColor = selectedColor.withAlpha(0x55)

                        selectedWtgs.add(wtg)
                    }

                    map.invalidate()
                    true
                }

                map.overlays.add(polygon)
            }
        }

        // MAP POSITION
        map.controller.apply {

            setZoom(15.0)

            setCenter(
                GeoPoint(54.068055, 9.8672034)
            )
        }

        map.invalidate()

        binding.buttonSelected.setOnClickListener {

            val fragment = SendDialogFragment()

            fragment.arguments = Bundle().apply {
                putStringArrayList("wtgs", ArrayList(selectedWtgs))
            }

            parentFragmentManager.beginTransaction()
                .replace(R.id.frame_content, fragment)
                .addToBackStack(null)
                .commit()
        }

        // WEA
        loadWindmills(map)
    }

    private fun loadWindmills(map: MapView) {

        val json = requireContext().assets
            .open("points_only.geojson")
            .bufferedReader()
            .use { it.readText() }

        val geoJson = JSONObject(json)

        val features = geoJson.getJSONArray("features")

        for (i in 0 until features.length()) {

            val feature = features.getJSONObject(i)

            val geometry = feature.getJSONObject("geometry")
            val properties = feature.getJSONObject("properties")

            val type = geometry.getString("type")

            if (type == "Point") {

                val coordinates = geometry.getJSONArray("coordinates")

                val lon = coordinates.getDouble(0)
                val lat = coordinates.getDouble(1)

                val point = GeoPoint(lat, lon)

                val weaNr = properties.optInt("WEA_Nr")
                val weaType = properties.optString("WEA_Typ")
                val gemarkung = properties.optString("Gemarkung")
                val flur = properties.optInt("Flur")
                val flurstueck = properties.optString("Flurstueck")

                val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.windmuhle)

                val scaled = drawable?.let {
                    val bitmap = (it as BitmapDrawable).bitmap
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 60, 60, false)
                    BitmapDrawable(resources, scaledBitmap)
                }

                val marker = Marker(map).apply {

                    position = point

                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                    title = "WEA $weaNr"

                    icon = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.windmuhle
                    )
                }

                marker.icon = scaled


                marker.setOnMarkerClickListener { _, _ ->

                    Toast.makeText(
                        requireContext(),
                        """
                        WEA: $weaNr
                        Type: $weaType
                        Gemarkung: $gemarkung
                        Flur: $flur
                        Flurstueck: $flurstueck
                        """.trimIndent(),
                        LENGTH_LONG
                    ).show()

                    true
                }

                map.overlays.add(marker)
            }
        }
    }
    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
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