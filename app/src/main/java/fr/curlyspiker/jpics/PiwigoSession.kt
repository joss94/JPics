package fr.curlyspiker.jpics

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object PiwigoSession {

    var logged = false
    var user = User(-1, "dummy")
    var isAdmin = false
    var token = ""
    var availableSizes = mutableListOf<String>()

    fun login(username: String, password: String, cb: (logged: Boolean) -> Unit) {
        GlobalScope.launch {
            logged = PiwigoAPI.pwgSessionLogin(username, password)
            if (logged) {
                checkStatus {
                    cb(logged)
                }
            }
        }
    }

    fun logout(ctx: Context) {
        GlobalScope.launch {
            PiwigoAPI.pwgSessionLogout()
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
        }
    }

    fun checkStatus(cb: () -> Unit) {
        GlobalScope.launch {
            val rsp = PiwigoAPI.pwgSessionGetStatus()

            val sizes = mutableListOf<String>()
            val sizesArray = rsp.optJSONArray("available_sizes")
            if (sizesArray != null) {
                for (i in 0 until sizesArray.length()) {
                    sizes.add(sizesArray[i].toString())
                }
            }

            user = User(-1, rsp.optString("username", "unknown"))
            isAdmin = rsp.optString("status", "guest") == "admin"
            token = rsp.optString("pwg_token")
            availableSizes = sizes.toMutableList()
            logged = user.username != "guest" && user.username != "unknown"
            Log.d("JP", "Connected user: ${user.username}")
            cb()
        }
    }
}