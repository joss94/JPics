package fr.curlyspiker.jpics

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.android.volley.toolbox.Volley

class SplashScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        PiwigoServerHelper.initialize(Volley.newRequestQueue(this))

        // Do default login by using preferences
        val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)

        val url = prefs.getString("server_url", null)
        val autoLogin = prefs.getBoolean("auto_login", false)

        if(autoLogin) {
            val username = prefs.getString("username", null)
            val password = prefs.getString("password", null)
            if(url != null  && username != null && password != null) {
                doLogin(url, username, password)
            }else {
                goToLogin()
            }
        } else {
            goToLogin()
        }
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
}