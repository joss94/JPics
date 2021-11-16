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
    }

    private fun doLogin(url: String, username: String, password: String) {
        PiwigoServerHelper.serverUrl = url
        PiwigoSession.login(username, password) { success ->
            if(success) {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                ContextCompat.startActivity(this, intent, null)
            }
            else {
                goToLogin()
            }
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        ContextCompat.startActivity(this, intent, null)
    }

    private fun proceedToApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        ContextCompat.startActivity(this, intent, null)
    }
}