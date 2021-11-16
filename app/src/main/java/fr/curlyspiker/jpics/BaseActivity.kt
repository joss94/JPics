package fr.curlyspiker.jpics

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.android.volley.toolbox.Volley

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PiwigoServerHelper.initialize(Volley.newRequestQueue(this))

        val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)

        val url = prefs.getString("server_url", null)
        val autoLogin = prefs.getBoolean("auto_login", false)
        PiwigoServerHelper.serverUrl = url?:""


        PiwigoSession.checkStatus {
            if(!PiwigoSession.logged) {
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
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        ContextCompat.startActivity(this, intent, null)
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
}