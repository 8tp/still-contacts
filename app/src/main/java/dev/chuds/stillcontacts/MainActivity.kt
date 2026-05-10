package dev.chuds.stillcontacts

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.chuds.stillcontacts.ui.theme.StillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val incomingViewUri = consumeViewVCardUriIfAny()
        val pickMode = isPickMode()

        setContent {
            StillTheme {
                StillContactsApp(
                    pickMode = pickMode,
                    incomingViewUri = incomingViewUri,
                    onPicked = { uri ->
                        setResult(Activity.RESULT_OK, Intent().setData(uri))
                        finish()
                    },
                )
            }
        }
    }
}
