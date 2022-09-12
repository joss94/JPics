package fr.curlyspiker.jpics

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity() {

    private lateinit var accountButton: ImageButton
    private lateinit var bottomView: BottomNavigationView

    private lateinit var albumsFragment: ExplorerFragment
    private lateinit var allImagesFragment: AllImagesFragment
    private lateinit var searchFragment: SearchFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.my_toolbar))

        bottomView = findViewById(R.id.bottom_nav)
        bottomView.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.all_pictures -> showAllImagesTab()
                R.id.albums -> showAlbumsTab()
                R.id.search -> showSearchTab()
            }
            true
        }

        accountButton = findViewById(R.id.account_button)
        accountButton.setOnClickListener {
            val intent = Intent(this, AccountActivity::class.java)
            ContextCompat.startActivity(this, intent, null)
        }

        if (savedInstanceState == null) {
            allImagesFragment = AllImagesFragment()
            searchFragment = SearchFragment()
            if(PiwigoSession.logged) {
                albumsFragment = ExplorerFragment(0)
                bottomView.selectedItemId = R.id.albums
            } else {
                PiwigoSession.login("joss", "Cgyn76&cgyn76") {
                    albumsFragment = ExplorerFragment(0)
                    bottomView.selectedItemId = R.id.albums
                }
            }
        } else {
            allImagesFragment = (this.supportFragmentManager.findFragmentByTag("all_images") as AllImagesFragment?) ?: AllImagesFragment()
            searchFragment = (this.supportFragmentManager.findFragmentByTag("search") as SearchFragment?) ?: SearchFragment()
            albumsFragment = (this.supportFragmentManager.findFragmentByTag("albums") as ExplorerFragment?) ?: ExplorerFragment(0)
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

        lifecycleScope.launch(Dispatchers.IO) {
            PiwigoData.refreshEverything ()
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
            .enqueueUniquePeriodicWork("instant_upload", ExistingPeriodicWorkPolicy.REPLACE, instantUploadRequest)
    }

    private fun showAllImagesTab() {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, allImagesFragment, "all_images")
        transaction.commitAllowingStateLoss()
    }

    private fun showAlbumsTab() {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, albumsFragment, "albums")
        transaction.commitAllowingStateLoss()
    }

    private fun showSearchTab() {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, searchFragment, "search")
        transaction.commitAllowingStateLoss()
    }
}