package fr.curlyspiker.jpics

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat

class AccountActivity : AppCompatActivity() {

    private lateinit var usernameText: TextView
    private lateinit var logoutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        usernameText = findViewById(R.id.user)
        logoutButton = findViewById(R.id.logout_button)

        usernameText.text = PiwigoSession.username

        logoutButton.setOnClickListener {
            logout()
        }

        findViewById<TextView>(R.id.sync_button).setOnClickListener {
            val intent = Intent(this, SyncActivity::class.java)
            ContextCompat.startActivity(this, intent, null)
        }
    }

    private fun logout() {
        PiwigoSession.logout {
            val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)
            prefs.edit().remove("username").apply()
            prefs.edit().remove("password").apply()

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            ContextCompat.startActivity(this, intent, null)
        }
    }
}