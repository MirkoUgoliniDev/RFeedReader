package it.mirkougolinidev.rfeedreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import it.mirkougolinidev.rfeedreader.ui.RFeedReaderApp
import it.mirkougolinidev.rfeedreader.ui.theme.RFeedReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RFeedReaderTheme {
                RFeedReaderApp()
            }
        }
    }
}
