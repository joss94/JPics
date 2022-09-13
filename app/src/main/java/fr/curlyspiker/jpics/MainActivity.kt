package fr.curlyspiker.jpics

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.work.*
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivityViewModel : ViewModel() {
    private var catId: Int? = null
    private var picId: Int? = null

    fun getCatId(): Int? {
        return catId
    }

    fun getPicId(): Int? {
        return picId
    }

    fun setCatId(catId: Int?) {
        this.catId = catId
    }

    fun setPicId(picId: Int?) {
        this.picId = picId
    }
}

class MainActivity : AppCompatActivity() {

    private var isReady: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PreferenceManager.setDefaultValues(this, R.xml.jpics_prefs, true)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.my_toolbar))

        DatabaseProvider.initDB(applicationContext)

        PiwigoServerHelper.initialize(Volley.newRequestQueue(this))

        val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)

        val autoLogin = prefs.getBoolean("auto_login", false)
        val url = prefs.getString("server_url", "") ?: ""
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        PiwigoServerHelper.serverUrl = url

        findViewById<ImageButton>(R.id.account_button).setOnClickListener {
            val action = HomeFragmentDirections.actionHomeFragmentToAccountFragment()
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container) as NavHostFragment
            navHostFragment.navController.navigate(action)
        }

        findViewById<ImageButton>(R.id.refresh_button).setOnClickListener {
            refreshData()
        }

        val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startInstantUploadJob()
            }
            else {
                Toast.makeText(this, "Instant upload is disabled because app was not authorized access to local files", Toast.LENGTH_LONG).show()
            }
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            startInstantUploadJob()
        }
        else {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    handleSendImage(intent) // Handle single image being sent
                }
            }
            intent?.action == Intent.ACTION_SEND_MULTIPLE
                    && intent.type?.startsWith("image/") == true -> {
                handleSendMultipleImages(intent) // Handle multiple images being sent
            }
            else -> {
                // Handle other intents, such as being started from the home screen
            }
        }

        checkStatus {
            if(PiwigoSession.logged) {
                goToHome()
            } else {
                if(autoLogin) {
                    login(url, username, password)
                } else {
                    goToLogin()
                }
            }

        }

        // Prevent first draw until first login has been attempted
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (isReady) {
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            }
        )
    }

    private fun handleSendImage(intent: Intent) {
        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
            Log.d("TAG", "Received images: $uri")
            Log.d("TAG", "Received images, converted path: ${uri.path}")

            AlertDialog.Builder(this, R.style.AlertStyle)
                .setTitle("Upload image")
                .setMessage("Are you sure you want to import this image?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes") { _, _ ->
                    val dialog = CategoryPicker(this)
                    dialog.setOnCategorySelectedCallback { c ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            PiwigoData.addImages(listOf(uri), contentResolver, listOf(c), listener = object: PiwigoData.ProgressListener {
                                override fun onStarted() {}
                                override fun onProgress(progress: Float) {}
                                override fun onCompleted() {
                                    finish()
                                }
                            })
                        }
                    }
                    dialog.show()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    finish()
                }
                .show()

        }
    }

    private fun handleSendMultipleImages(intent: Intent) {
        AlertDialog.Builder(this, R.style.AlertStyle)
            .setTitle("Upload images")
            .setMessage("Are you sure you want to import these images?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ ->
                val dialog = CategoryPicker(this)
                dialog.setOnCategorySelectedCallback { c ->
                    val uris = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.map { it -> it as Uri } ?: listOf()
                    lifecycleScope.launch(Dispatchers.IO) {
                        PiwigoData.addImages(uris, contentResolver, listOf(c), listener = object : PiwigoData.ProgressListener {
                            override fun onStarted() {}
                            override fun onProgress(progress: Float) {}
                            override fun onCompleted() {
                                finish()
                            }
                        })
                    }
                }
                dialog.show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .show()
    }

    private fun startInstantUploadJob() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_ROAMING)
            .setRequiresBatteryNotLow(true)
            .build()

        // Periodic time is limited by Android to 15 min, it will not repeat faster than this...
        val instantUploadRequest = PeriodicWorkRequestBuilder<InstantUploaderWorker>(10, TimeUnit.SECONDS)
            .setConstraints(constraints)
            .setInitialDelay(2L, TimeUnit.SECONDS)
            .build()

        WorkManager
            .getInstance(applicationContext)
            .enqueueUniquePeriodicWork("jpics_instant_upload", ExistingPeriodicWorkPolicy.REPLACE, instantUploadRequest)
    }

    private fun goToLogin() {
        proceedToApp(R.id.loginFragment)
    }

    private fun goToHome() {
        proceedToApp(R.id.homeFragment)
    }

    private fun proceedToApp(startFragmentId: Int) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container) as NavHostFragment
        val navController = navHostFragment.navController
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(startFragmentId)
        navController.graph = graph
        isReady = true
    }

    fun logout() {
        lifecycleScope.launch {
            PiwigoAPI.pwgSessionLogout()
            PiwigoSession.logged = false
            PiwigoSession.token = ""
            PiwigoSession.availableSizes.clear()
            PiwigoSession.isAdmin = false

            val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)
            prefs.edit().remove("username").apply()
            prefs.edit().remove("password").apply()
        }
    }

    fun login(url: String, username: String, password: String) {

        PiwigoServerHelper.serverUrl = url

        val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)

        prefs.edit().putString("server_url", url).apply()

        lifecycleScope.launch {
            PiwigoAPI.pwgSessionLogin(username, password)
            checkStatus {
                if(PiwigoSession.logged) {
                    prefs.edit().putString("username", username).apply()
                    prefs.edit().putString("password", password).apply()
                    goToHome()
                    refreshData()
                } else {
                    goToLogin()
                }
            }
        }
    }

    private fun checkStatus(cb: () -> Unit) {
        lifecycleScope.launch {
            val rsp = PiwigoAPI.pwgSessionGetStatus()

            val sizes = mutableListOf<String>()
            val sizesArray = rsp.optJSONArray("available_sizes")
            if (sizesArray != null) {
                for (i in 0 until sizesArray.length()) {
                    sizes.add(sizesArray[i].toString())
                }
            }

            PiwigoSession.user = User(-1, rsp.optString("username", "unknown"))
            PiwigoSession.isAdmin = rsp.optString("status", "guest") == "admin"
            PiwigoSession.token = rsp.optString("pwg_token")
            PiwigoSession.availableSizes = sizes.toMutableList()
            PiwigoSession.logged = PiwigoSession.user.username != "guest" && PiwigoSession.user.username != "unknown"
            Log.d("JP", "Connected user: ${PiwigoSession.user.username}")
            cb()
        }
    }

    private fun refreshData() {
        InstantUploadManager.getInstance(this).checkForNewImages()
        lifecycleScope.launch(Dispatchers.IO) {
            PiwigoData.refreshEverything()
        }
    }
}