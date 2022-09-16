package fr.curlyspiker.jpics

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*


class InstantUploadManager private constructor(val context: Context) {

    class SyncFolder(val name: String, var ignored: Boolean)

    private val allFolders = mutableListOf<SyncFolder>()
    private val imageFolders = mutableListOf<SyncFolder>()
    private var defaultCatId: Int? = null

    init {
        val sharedPref = context.getSharedPreferences("jpics.InstantUpload", Context.MODE_PRIVATE)
        val foldersParamsString = sharedPref.getString("syncFoldersParams", "") ?: ""
        try {
            val foldersParamsJson = JSONObject(foldersParamsString)
            val foldersArray = foldersParamsJson.optJSONArray("folders") ?: JSONArray()
            for (i in 0 until foldersArray.length()) {
                val foldersParams = foldersArray.optJSONObject(i)
                val folderName = foldersParams.optString("name", "Unknown")
                val isIgnored = foldersParams.optBoolean("ignored", true)

                allFolders.add(SyncFolder(folderName, isIgnored))
            }
        } catch(e: Exception) {
            Log.d("IU", "Error loading InstantUploader preferences: $e")
        }

    }

    fun setDefaultCategory(id: Int?) {
        defaultCatId = id
    }

    private fun saveToPreferences() {
        val sharedPref = context.getSharedPreferences("jpics.InstantUpload", Context.MODE_PRIVATE)

        val foldersParamsJson = JSONObject()
        val foldersArray = JSONArray()

        for(f in allFolders) {
            val folderParams = JSONObject()
            folderParams.put("name", f.name)
            folderParams.put("ignored", f.ignored)
            foldersArray.put(folderParams)
        }

        foldersParamsJson.put("folders", foldersArray)

        with (sharedPref.edit()) {
            putString("syncFoldersParams", foldersParamsJson.toString())
            apply()
        }
    }

    fun getAllFolders() : List<SyncFolder> {
        return allFolders
    }

    fun isFolderIgnored(folder: SyncFolder?) : Boolean {
        return isFolderIgnored(folder?.name)
    }

    fun isFolderIgnored(folder: String?) : Boolean {
        return allFolders.any { f -> f.ignored && "${folder}//".contains(f.name) }
    }

    fun isFolderSynced(folder: String) : Boolean {

        // If folder is not included in the list, then it is not synced
        if (!allFolders.any {f -> f.name == folder }) {
            return false
        }

        // If folder is ignored, then it is not synced
        if(isFolderIgnored(folder)) {
            return false
        }

        // If parent folder is specifically ignored, then its children are ignored too
        if(isFolderIgnored(File(folder).parent!!)) {
            return false
        }

        // If all conditions passed, folder is synced*
        return true
    }

    fun setFolderIgnored(name: String, ignored: Boolean) {

        val syncFolder = SyncFolder(name, ignored)

        // Add the folder to the list if it does not exist
        if(!allFolders.any { f -> f.name == syncFolder.name })
            allFolders.add(syncFolder)

        // Set ignore parameter
        allFolders.find { f -> f.name == syncFolder.name }?.ignored = syncFolder.ignored

        // If the folder does not contain images (i.e., it is not in imageFolders), remove it
        // from the list, unless it is marked as "ignored", in this case we want to keep it because
        // it might receive images in the future
        //if(!imageFolders.any { f -> f.name == syncFolder.name} && !syncFolder.ignored)
        //    allFolders.removeAll { f -> f.name == syncFolder.name }

        saveToPreferences()
    }

    fun checkForNewImages() {
        if (defaultCatId == null) {
            Log.d("IU", "Not checking images, default cat is null")
            return
        }

        val checkDate = Date()


        Log.d("IU", "Checking for new images")
        GlobalScope.launch(Dispatchers.IO) {

            // Get all images from device
            val imageList = findImages()

            // Get all folders corresponding to the image list
            imageFolders.clear()
            imageFolders.addAll(extractImagesFolders(imageList).map { f -> SyncFolder(f, true) })

            // Build a list of
            imageFolders.forEach { f ->
                if(!allFolders.any { it.name == f.name }) {
                    allFolders.add(f)
                }
            }
            allFolders.removeIf { f ->  imageFolders.firstOrNull { it.name == f.name } == null && !f.ignored }

            saveToPreferences()

            val sharedPref = context.getSharedPreferences("jpics.InstantUpload", Context.MODE_PRIVATE)
            val lastUpdate = sharedPref.getLong("last_update", 0L)

            val notUpdated = imageList
                .filter { p ->
                    val f = File(p)
                    f.lastModified() > lastUpdate && isFolderSynced(Uri.parse(f.parent).path ?: "")
                }
                .sortedBy { p -> File(p).lastModified() }

            Log.d("IU", "Found ${notUpdated.size} images to sync")

            defaultCatId?.let { catId ->
                PiwigoData.addImagesFromFilePaths(notUpdated, listOf(catId))
                sharedPref.edit().putLong("last_update", checkDate.time).apply()
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun syncWithServer() {
        GlobalScope.launch(Dispatchers.IO) {
            PiwigoData.refreshEverything()
        }
    }

    private fun findImages() : List<String>{

        val out = mutableListOf<String>()

        val location = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media._ID)
        val orderBy = MediaStore.Images.Media._ID

        context.contentResolver.query(location, columns, null, null, orderBy)?.apply {
            for (i in 0 until count) {
                moveToPosition(i)
                val index = getColumnIndex(MediaStore.Images.Media.DATA)
                out.add(getString(index))
            }
            close()
        }

        return out
    }

    private fun extractImagesFolders(imageList: List<String>) : List<String> {
        val out = mutableListOf<String>()

        imageList.forEach { path ->
            val folderPath = Uri.parse(File(path).parent).path
            if(folderPath != null && !out.contains(folderPath)) {
                out.add(folderPath)
            }
        }

        return out
    }

    companion object : SingletonHolder<InstantUploadManager, Context>(::InstantUploadManager)
}

open class SingletonHolder<out T: Any, in A>(creator: (A) -> T) {
    private var creator: ((A) -> T)? = creator
    @Volatile private var instance: T? = null

    fun getInstance(arg: A): T {
        val checkInstance = instance
        if (checkInstance != null) {
            return checkInstance
        }

        return synchronized(this) {
            val checkInstanceAgain = instance
            if (checkInstanceAgain != null) {
                checkInstanceAgain
            } else {
                val created = creator!!(arg)
                instance = created
                creator = null
                created
            }
        }
    }
}

class InstantUploaderWorker(appContext: Context, workerParams: WorkerParameters): Worker(appContext, workerParams) {
    override fun doWork(): Result {
        InstantUploadManager.getInstance(applicationContext).syncWithServer()
        InstantUploadManager.getInstance(applicationContext).checkForNewImages()
        return Result.success()
    }
}