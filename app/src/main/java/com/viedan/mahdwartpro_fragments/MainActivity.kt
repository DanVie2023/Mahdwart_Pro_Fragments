package com.viedan.mahdwartpro_fragments

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.viedan.mahdwartpro_fragments.databinding.ActivityMainBinding

/**
 * Single activity of the app. It only hosts the fragments:
 * [MahdwartFragment] is shown on start, [SendDialogFragment] is placed on top of it
 * (via the back stack) when the user wants to send a notification.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Add the start fragment only on a fresh start. After a configuration change
        // (e.g. rotation) the FragmentManager restores the fragments itself.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame_content, MahdwartFragment())
                .commit()
        }

    }
}