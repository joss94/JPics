package fr.curlyspiker.jpics

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.android.volley.toolbox.Volley

class SplashScreenActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        PiwigoSession.checkStatus {
            if(PiwigoSession.logged) {
                proceedToApp()
            }
        }
    }
}