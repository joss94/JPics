package fr.curlyspiker.jpics

import android.os.Bundle

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