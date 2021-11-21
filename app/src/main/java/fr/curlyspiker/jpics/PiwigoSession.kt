package fr.curlyspiker.jpics

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object PiwigoSession {

    var logged = false
    var user = User(-1, "dummy")
    var isAdmin = false
    var token = ""
    var availableSizes = mutableListOf<String>()

    fun login(username: String, password: String, cb: (logged: Boolean) -> Unit) {
        PiwigoAPI.pwgSessionLogin(username, password) { _, logged, _ ->
            if (logged) {
                checkStatus {
                    cb(this.logged)
                }
            }
        }
    }

    fun logout(ctx: Context) {
        PiwigoAPI.pwgSessionLogout { success, rsp ->
            if(success) {
                logged = false
                token = ""
                availableSizes.clear()
                isAdmin = false

                val prefs = ctx.getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)
                prefs.edit().remove("username").apply()
                prefs.edit().remove("password").apply()

                val intent = Intent(ctx, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                ContextCompat.startActivity(ctx, intent, null)
            } else {
                Log.d("JP", "Connection failed: ${rsp.optString("message", "Unknown error")}")
            }
        }
    }

    fun checkStatus(cb: () -> Unit) {
        PiwigoAPI.pwgSessionGetStatus { username, isAdmin, token, sizes ->
            user = User(-1, "dummy")
            this.isAdmin = isAdmin
            this.token = token
            this.availableSizes = sizes.toMutableList()
            logged = username != "guest" && username != "unknown"
            Log.d("JP", "Connected user: $username")
            cb()
        }
    }
}