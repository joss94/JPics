package fr.curlyspiker.jpics

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat

class LoginFragment : Fragment() {

    private lateinit var urlEdit: EditText
    private lateinit var usernameEdit: EditText
    private lateinit var passwordEdit: EditText

    private lateinit var autoCheckBox : CheckBox
    private lateinit var loginButton: Button
    private lateinit var passwordVisibleButton: ImageButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        urlEdit = view.findViewById(R.id.url_edit)
        usernameEdit = view.findViewById(R.id.login_edit)
        passwordEdit = view.findViewById(R.id.passwsord_edit)

        autoCheckBox = view.findViewById(R.id.remember_checkbox)
        loginButton = view.findViewById(R.id.login_button)
        loginButton.setOnClickListener {
            val url = urlEdit.text.toString()
            val username = usernameEdit.text.toString()
            val password = passwordEdit.text.toString()

            val prefs = requireActivity().getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("auto_login", autoCheckBox.isChecked).apply()

            (activity as MainActivity).login(url, username, password)
        }

        passwordVisibleButton = view.findViewById(R.id.password_visible_button)
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
        val prefs = requireActivity().getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)

        val url = prefs.getString("server_url", null)
        if(url != null) {
            urlEdit.setText(url)
        }
        PiwigoServerHelper.serverUrl = url?:""

        val autoLogin = prefs.getBoolean("auto_login", false)
        autoCheckBox.isChecked = autoLogin
    }
}