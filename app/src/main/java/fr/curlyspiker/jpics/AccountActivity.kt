package fr.curlyspiker.jpics

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

        usernameText.text = PiwigoSession.user.username

        logoutButton.setOnClickListener {
            logout()
        }

        findViewById<TextView>(R.id.sync_button).setOnClickListener {
            val intent = Intent(this, SyncActivity::class.java)
            ContextCompat.startActivity(this, intent, null)
        }

        findViewById<TextView>(R.id.archive_button).setOnClickListener {
            val intent = Intent(this, ArchiveActivity::class.java)
            ContextCompat.startActivity(this, intent, null)
        }
    }

    private fun logout() {
        PiwigoSession.logout(this)
    }
}