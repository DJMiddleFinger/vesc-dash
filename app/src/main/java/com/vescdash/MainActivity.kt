package com.vescdash

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vescdash.ui.AppRoot
import com.vescdash.ui.MainViewModel
import com.vescdash.ui.orientationFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the first frame, so a Stark launch doesn't start in portrait and then turn.
        requestedOrientation = orientationFor((application as VescDashApp).repository.vehicle.value.appearance)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val vm: MainViewModel = viewModel()
            AppRoot(vm)
        }
    }
}
