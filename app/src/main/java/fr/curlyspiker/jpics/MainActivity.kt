package fr.curlyspiker.jpics

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.work.*
import com.android.volley.toolbox.Volley
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class MainActivityViewModel : ViewModel() {
    private var catId: Int? = null
    private var picId: Int? = null
    private var currentlyDisplayedPics = MutableLiveData<List<Picture>>()

    fun getDisplayedPics(): LiveData<List<Picture>> {
        return currentlyDisplayedPics
    }

    fun setDisplayedPics(pics: List<Picture>) {
        currentlyDisplayedPics.postValue(pics)
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

class MainActivity : AppCompatActivity(), PiwigoData.ProgressListener {

    private val mainVM: MainActivityViewModel by viewModels()
    private var isReady: Boolean = false

    private lateinit var progressLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTitle: TextView

    private var onPermissionsGranted : () -> Unit = {}
    private val permissionReq = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted: Boolean ->
        if (granted) { onPermissionsGranted() }
    }

    private var onImagesPicked : (data: Intent?) -> Unit = {}
    private var imgReq = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onImagesPicked(result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PreferenceManager.setDefaultValues(this, R.xml.jpics_prefs, true)

        //WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        DatabaseProvider.initDB(applicationContext)

        PiwigoServerHelper.initialize(Volley.newRequestQueue(this))

        val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)

        val autoLogin = prefs.getBoolean("auto_login", false)
        val url = prefs.getString("server_url", "") ?: ""
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        PiwigoServerHelper.serverUrl = url

        progressLayout = findViewById(R.id.progress_layout)
        progressBar = findViewById(R.id.progress_bar)
        progressTitle = findViewById(R.id.progress_title)

        when {
            // Handle single image being sent
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                        handleImportImages(listOf(uri))
                    }
                }
            }

            // Handle multiple images being sent
            intent?.action == Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uris = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.map { it -> it as Uri } ?: listOf()
                    handleImportImages(uris)
                }
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

        PiwigoData.setProgressListener(this)
    }

    override fun onTaskStarted() {
        runOnUiThread {
            progressLayout.visibility = View.VISIBLE
            progressBar.progress = 0
            progressTitle.text = getString(R.string.process_images).format(0)
        }
    }

    override fun onTaskCompleted() {
        runOnUiThread {
            progressLayout.visibility = View.GONE
        }
    }

    override fun onTaskProgress(progress: Float) {
        runOnUiThread {
            val progressInt = (progress * 100).toInt()
            progressBar.progress = progressInt
            progressTitle.text = getString(R.string.process_images).format(progressInt)
        }
    }

    fun setDisplayedPics(pics: LiveData<List<Picture>>) {
        pics.observe(this, Observer {
            mainVM.setDisplayedPics(it)
        })
    }

    fun downloadImages(pictures: List<Int>) {

        fun downloadImagesPermissionGranted() {
            onTaskStarted()

            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path + "/JPics"
            var downloaded = 0

            val pics = PiwigoData.getPicturesFromIds(pictures)
            suspend fun downloadNext(index: Int = 0) {
                if (index < pics.size) {
                    Log.d("MA", "Downloading nb. $index")
                    val pic = pics[index]
                    var tries = 0
                    var success: Boolean? = false
                    while(success != true && tries<3) {
                        tries += 1
                        success = withTimeoutOrNull(3000L) {
                            downloadSingleImage(pic.elementUrl, path, pic.name)
                        }
                        Log.d("MA", "Download success: $success")
                    }

                    downloaded += 1
                    onTaskProgress(downloaded.toFloat() / pictures.size)
                    downloadNext(index + 1)

                } else {
                    onTaskCompleted()
                }
            }
            lifecycleScope.launch {
                downloadNext()
            }
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            downloadImagesPermissionGranted()
        }
        else {
            onPermissionsGranted = { downloadImagesPermissionGranted() }
            permissionReq.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun shareImages(ids: List<Int>) {
        val ctx = this
        lifecycleScope.launch(Dispatchers.IO) {
            val pictures = PiwigoData.getPicturesFromIds(ids)
            val path = cacheDir.absolutePath + File.separator + "/images"
            val listOfUris = ArrayList<Uri>()
            pictures.forEachIndexed { i, p ->
                val target = object : com.squareup.picasso.Target {
                    override fun onBitmapLoaded(bitmap: Bitmap, arg1: Picasso.LoadedFrom?) {
                        try {
                            val folder = File(path)
                            if(!folder.exists()) { folder.mkdirs() }
                            val file = File(folder.path + File.separator + "image_$i.jpg")
                            file.createNewFile()
                            val stream = FileOutputStream(file)
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                            stream.close()

                            val contentUri = FileProvider.getUriForFile(
                                ctx,
                                "fr.curlyspiker.jpics.fileprovider",
                                file
                            )
                            listOfUris.add(contentUri)
                            if(listOfUris.size == pictures.size) {
                                val shareIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND_MULTIPLE
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, listOfUris)
                                }
                                try {
                                    startActivity(Intent.createChooser(shareIntent, "Select sharing app"))
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(ctx, "No App Available", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    override fun onBitmapFailed(errorDrawable: Drawable?) {
                        Log.d("TAG", "Error during download !")
                    }
                    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                }
                Picasso.with(ctx).load(p.largeResUrl).into(target)
            }
        }
    }

    fun archiveImages(ids: List<Int>) {
        AlertDialog.Builder(this, R.style.AlertStyle)
            .setTitle("Delete image")
            .setMessage("Are you sure you want to delete this image?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    PiwigoData.archivePictures(ids, true)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    fun restoreImages(pictures: List<Int>) {
        AlertDialog.Builder(this, R.style.AlertStyle)
            .setTitle("Restore images")
            .setMessage("Are you sure you want to restore these images?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    PiwigoData.restorePictures(pictures)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    fun deleteImages(pictures: List<Int>) {
        AlertDialog.Builder(this, R.style.AlertStyle)
            .setTitle("Delete images")
            .setMessage("Are you sure you want to delete these images? This will be definitive !")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    PiwigoData.deleteImages(pictures)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    fun moveImages(currentCat: Int?, pictures: List<Int>) {

        var checkedItem = 0
        val labels = arrayOf("Add to other album", "Move to different location")
        val builder = AlertDialog.Builder(this, R.style.AlertStyle)
            .setSingleChoiceItems(labels, checkedItem) { _, i -> checkedItem = i }
            .setTitle("What do you want to do ?")
            .setCancelable(true)
            .setPositiveButton("OK") { _, _ ->
                val excludeList = mutableListOf<Int>()
                currentCat?.let { excludeList.add(it) }
                selectCategory(currentCat, excludeList) { c ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (checkedItem == 0) {
                            PiwigoData.addPicsToCats(pictures, listOf(c))
                        } else {
                            PiwigoData.movePicsToCat(pictures, c)
                        }
                    }
                }
            }

        builder.create().show()
    }

    fun removeImagesFromCat(catId: Int?, pictures: List<Int>) {
        catId?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                PiwigoData.removePicsFromCat(pictures, it)
            }
        }
    }

    fun editImagesCreationDate(pictures: List<Int>) {
        val date = Date()
        Utils.getDatetime(supportFragmentManager, date) {
            lifecycleScope.launch(Dispatchers.IO) {
                pictures.forEach { id ->
                    PiwigoData.setPicCreationDate(id, creationDate = Date(it))
                }
            }
        }
    }

    private fun addImages(currentCat: Int?, uris: List<Uri>) {
        runOnUiThread {
            selectCategory(currentCat ?: 0) { c ->
                lifecycleScope.launch(Dispatchers.IO) {
                    PiwigoData.addImagesFromContentUris(uris, contentResolver, listOf(c))
                }
            }
        }
    }

    fun addImages(currentCat: Int?) {

        onImagesPicked = { data ->
            val clipData = data?.clipData
            val singleData = data?.data

            val uris = mutableListOf<Uri>()
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else if (singleData != null) {
                uris.add(singleData)
            }

            addImages(currentCat, uris)
        }

        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.putExtra(Intent.EXTRA_TITLE, "Select images to add")
        imgReq.launch(intent)
    }

    fun addCategory(parent: Int?) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.AlertStyle)
        builder.setTitle("Create a new album")

        val input = EditText(this)
        input.hint = "Name of new album"
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setTextColor(this.getColor(R.color.white))
        input.setHintTextColor(this.getColor(R.color.light_gray))
        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, _ ->
            val name = input.text.toString()
            lifecycleScope.launch {
                PiwigoData.addCategory(name, parent)
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    fun deleteCategories(cats: List<Int>) {
        AlertDialog.Builder(this, R.style.AlertStyle)
            .setTitle("Delete categories")
            .setMessage("Are you sure you want to delete these categories?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    PiwigoData.deleteCategories(cats)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun moveCategories(cats: List<Int>) {
        val excludeList = cats.toMutableList()
        selectCategory(null, excludeList) { c ->
            lifecycleScope.launch(Dispatchers.IO) {
                PiwigoData.moveCategories(cats, c)
            }
        }
    }

    fun setCategoryName(catId: Int, name: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            PiwigoData.setCategoryName(catId, name)
        }
    }

    private suspend fun downloadSingleImage(url: String, path: String, filename: String) = suspendCancellableCoroutine<Boolean>{
        val target = object : com.squareup.picasso.Target {
            override fun onBitmapLoaded(bitmap: Bitmap, arg1: Picasso.LoadedFrom?) {
                var success = true
                try {
                    val folder = File(path)
                    if(!folder.exists()) { folder.mkdirs() }
                    val file = File(folder.path + File.separator + filename + if(filename.endsWith(".jpg")) "" else ".jpg")
                    file.createNewFile()
                    val stream = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    stream.close()
                } catch (e: Exception) {
                    Log.d("MA", "A problem occurred during download of $url!")
                    e.printStackTrace()
                    success = false
                } finally {
                    Log.d("MA", "Download of $url finished !")
                    it.resume(success)
                }
            }
            override fun onBitmapFailed(errorDrawable: Drawable?) {
                Log.d("TAG", "Error during download !")
                it.resume(false)
            }
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
        }

        Log.d("MA", "Downloading $url")
        Picasso.with(this).load(url).into(target)
    }

    private fun handleImportImages(uris: List<Uri>) {
        AlertDialog.Builder(this, R.style.AlertStyle)
            .setTitle("Upload image")
            .setMessage("Are you sure you want to import these images?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ ->
                selectCategory { c ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        PiwigoData.addImagesFromContentUris(uris, contentResolver, listOf(c))
                    }
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .show()
    }

    fun selectCategory(startCat: Int? = null, excludeList : List<Int> = listOf(), callback: (cat: Int) -> Unit) {
        val dialog = CategoryPicker(startCat ?: 0, excludeList, callback)
        dialog.show(supportFragmentManager, "cat_picker")
    }

    private fun startInstantUploadJob() {

        fun startJob() {

            val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)
            val c = prefs.getInt("default_album", -1)
            InstantUploadManager.getInstance(this).setDefaultCategory(c)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_ROAMING)
                .setRequiresBatteryNotLow(true)
                .build()

            // Periodic time is limited by Android to 15 min, it will not repeat faster than this...
            val instantUploadRequest =
                PeriodicWorkRequestBuilder<InstantUploaderWorker>(10, TimeUnit.SECONDS)
                    .setConstraints(constraints)
                    .setInitialDelay(2L, TimeUnit.SECONDS)
                    .build()

            WorkManager
                .getInstance(applicationContext)
                .enqueueUniquePeriodicWork(
                    "jpics_instant_upload",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    instantUploadRequest
                )
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            startJob()
        }
        else {
            onPermissionsGranted = { startJob() }
            permissionReq.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
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
        // Remove stored username and password
        val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)
        prefs.edit().remove("username").apply()
        prefs.edit().remove("password").apply()

        lifecycleScope.launch {

            // Log out the session
            PiwigoAPI.pwgSessionLogout()

            // Check status
            checkStatus { }
        }
    }

    fun login(url: String, username: String, password: String) {

        // Set up server URL
        PiwigoServerHelper.serverUrl = url
        val prefs = getSharedPreferences("fr.curlyspiker.jpics", Context.MODE_PRIVATE)
        prefs.edit().putString("server_url", url).apply()

        // Store username and password locally TODO: Avoid doing that, password in saved in clear
        prefs.edit().putString("username", username).apply()
        prefs.edit().putString("password", password).apply()

        lifecycleScope.launch {
            // Try to log into API
            PiwigoAPI.pwgSessionLogin(username, password)

            // Check status
            checkStatus { }
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

            if(PiwigoSession.logged) {
                // Proceed to Home screen
                goToHome()

                // Start the worker (will trigger a first server sync)
                WorkManager.getInstance(applicationContext).cancelAllWork()
                startInstantUploadJob()
            } else {
                // If login failed at any point, go back to login page
                goToLogin()

                // Stop instant upload job
                WorkManager.getInstance(applicationContext).cancelAllWork()
            }
            cb()
        }
    }

    fun refreshData() {
        InstantUploadManager.getInstance(this).checkForNewImages()
        InstantUploadManager.getInstance(this).syncWithServer()
    }
}