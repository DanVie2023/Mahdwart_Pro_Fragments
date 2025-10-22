package com.viedan.mahdwartpro_fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.viedan.mahdwartpro_fragments.databinding.FragmentMahdwartBinding
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import kotlin.random.Random

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )

        val map = binding.map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        
        val geoJsonString = requireContext().assets
            .open("21-01-18 Wasbek-Ehndorf 5xV150_Mahdman_Fields.geojson")
            .bufferedReader()
            .use { it.readText() }

        val geoJson = JSONObject(geoJsonString)
        val features = geoJson.getJSONArray("features")
        val allPoints = mutableListOf<GeoPoint>()
        
        val originalColors = mutableMapOf<Polygon, Int>()
        
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val geometry = feature.getJSONObject("geometry")
            val type = geometry.getString("type")

            if (type == "Polygon" || type == "MultiPolygon") {

                val coordsRoot = geometry.getJSONArray("coordinates")
                val polygonsArray =
                    if (type == "MultiPolygon") coordsRoot.getJSONArray(0)
                    else coordsRoot

                val coordsArray = polygonsArray.getJSONArray(0)
                val points = mutableListOf<GeoPoint>()

                for (j in 0 until coordsArray.length()) {
                    val coord = coordsArray.getJSONArray(j)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    val geoPoint = GeoPoint(lat, lon)
                    points.add(geoPoint)
                    allPoints.add(geoPoint)
                }

                val polygon = Polygon(map)
                polygon.setPoints(points)
                
                val randomColor = randomColor()
                polygon.fillColor = randomColor.withAlpha(0x99)
                polygon.strokeColor = randomColor
                polygon.strokeWidth = 4f

                originalColors[polygon] = randomColor
                
                val props = feature.optJSONObject("properties")
                val name = props?.optString("name") ?: "Noname"
                
                polygon.setOnClickListener { _, _, _ ->
                    val currentColor = polygon.strokeColor
                    val origColor = originalColors[polygon] ?: randomColor
                    val selectedColor = 0xFF00BFFF.toInt() // blue

                    if (currentColor == selectedColor) {
                        polygon.strokeColor = origColor
                        polygon.fillColor = origColor.withAlpha(0x33)
                    } else {
                        polygon.strokeColor = selectedColor
                        polygon.fillColor = selectedColor.withAlpha(0x33)
                    }

                    map.invalidate()
                    Toast.makeText(requireContext(), "Poly: $name", Toast.LENGTH_SHORT).show()
                    true
                }

                map.overlays.add(polygon)
            }
        }

        val mapController = map.controller
        mapController.setZoom(15.0)
        val startPoint = GeoPoint(54.068055, 9.8672034, )
        mapController.setCenter(startPoint)

        map.invalidate()
    }
    
    private fun randomColor(): Int {
        val r = Random.nextInt(50, 255)
        val g = Random.nextInt(50, 255)
        val b = Random.nextInt(50, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
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

