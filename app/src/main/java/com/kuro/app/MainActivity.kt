package com.kuro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.kuro.app.core.root.RootChecker
import com.kuro.app.di.ServiceLocator
import com.kuro.app.ui.navigation.KuroNavGraph
import com.kuro.app.ui.theme.KuroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)
        // Step 4: silent root availability check on every app open.
        // Background only — no UI, no dialogs, never blocks start.
        RootChecker.refreshSilent()
        enableEdgeToEdge()
        val coldStart = savedInstanceState == null && !ColdStartGate.introShown
        setContent {
            val settings by ServiceLocator.settingsRepository.settings.collectAsState()
            val themeMode = settings.themeMode
            val dynamic = settings.dynamicColor
            val reduceMotion = settings.reduceMotion

            KuroTheme(themeMode = themeMode, dynamicColor = dynamic) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    KuroNavGraph(
                        navController = nav,
                        reduceMotion = reduceMotion,
                        showIntro = coldStart,
                        onIntroConsumed = { ColdStartGate.introShown = true }
                    )
                }
            }
        }
    }
}

private object ColdStartGate {
    var introShown: Boolean = false
}
