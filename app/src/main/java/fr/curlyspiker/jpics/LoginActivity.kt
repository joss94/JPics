package fr.curlyspiker.jpics

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat


class LoginActivity : AppCompatActivity() {

    private lateinit var urlEdit: EditText
    private lateinit var usernameEdit: EditText
    private lateinit var passwordEdit: EditText

    private lateinit var autoCheckBox : CheckBox
    private lateinit var loginButton: Button
    private lateinit var passwordVisibleButton: ImageButton

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        urlEdit = findViewById(R.id.url_edit)
        usernameEdit = findViewById(R.id.login_edit)
        passwordEdit = findViewById(R.id.passwsord_edit)

        autoCheckBox = findViewById(R.id.remember_checkbox)
        loginButton = findViewById(R.id.login_button)
        loginButton.setOnClickListener {
            doLogin(urlEdit.text.toString(), usernameEdit.text.toString(), passwordEdit.text.toString())
        }

        passwordVisibleButton = findViewById(R.id.password_visible_button)
        passwordVisibleButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    passwordEdit.transformationMethod = null
                }
                MotionEvent.ACTION_UP -> {
                    passwordEdit.transformationMethod = PasswordTransformationMethod()
                }
            }
            false
        }

        // Do default login by using preferences
        val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)

        val url = prefs.getString("server_url", null)
        if(url != null) {
            urlEdit.setText(url)
        }
        PiwigoServerHelper.serverUrl = url?:""

        val autoLogin = prefs.getBoolean("auto_login", false)
        autoCheckBox.isChecked = autoLogin
    }

    private fun doLogin(url: String, username: String, password: String) {
        Log.d("TAG", "Doing login with url: $url")

        PiwigoServerHelper.serverUrl = url
        val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)
        prefs.edit().putString("server_url", url).apply()
        prefs.edit().putBoolean("auto_login", autoCheckBox.isChecked).apply()

        PiwigoSession.login(username, password) { success ->
            if(success) {
                prefs.edit().putString("username", username).apply()
                prefs.edit().putString("password", password).apply()

                proceedToApp()
            }
            else {
                try {
                    Toast.makeText(this, "Login failed, please check URL and login credentials", Toast.LENGTH_LONG).show()
                } catch(e: Exception) {}
            }
        }
    }

    private fun proceedToApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        ContextCompat.startActivity(this, intent, null)
    }
}