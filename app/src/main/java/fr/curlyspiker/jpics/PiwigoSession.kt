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
}