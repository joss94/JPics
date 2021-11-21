package fr.curlyspiker.jpics

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.Exception
import java.nio.file.Paths
import java.util.*

class InstantUploadManager private constructor(val context: Context) {

    class SyncFolder(val name: String, var ignored: Boolean)

    private val allFolders = mutableListOf<SyncFolder>()
    private val imageFolders = mutableListOf<SyncFolder>()

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
        allFolders.forEach { f ->
            if(f.ignored && "${folder}//".contains(f.name)) {
                return true
            }
        }
        return false
    }

    fun isFolderSynced(folder: String) : Boolean {

        // If folder is not included in the list, or is ignored, then it is not synced
        val matchingFolder = allFolders.firstOrNull { f -> f.name == folder }
        if(matchingFolder == null || matchingFolder.ignored) {
            return false
        }

        // If parent folder is included in the list, and is not synced, then his child is not synced
        val parentFolder = File(folder).parent!!
        if(allFolders.firstOrNull { f -> f.name == parentFolder } != null && !isFolderSynced(parentFolder)) {
            return false
        }

        // If folder is in the list, and parent is not in the list, or indicated as synced, then folder is synced
        return true
    }

    fun setFolderIgnored(folder: SyncFolder) {
        val index = allFolders.indexOfFirst { f -> f.name == folder.name }
        if(index == -1 && folder.ignored) {
            allFolders.add(folder)
        } else {
            allFolders[index].ignored = folder.ignored
            if(!folder.ignored && imageFolders.firstOrNull { f -> f.name == folder.name} == null) {
                allFolders.removeAll { f -> f.name == folder.name }
            }
        }

        saveToPreferences()
    }

    fun checkForNewImages() {
        GlobalScope.launch(Dispatchers.IO) {
            val imageList = findImages()
            imageFolders.clear()
            extractImagesFolders(imageList).forEach { f ->
                imageFolders.add(SyncFolder(f, true))
            }

            imageFolders.forEach { f ->
                if(allFolders.firstOrNull { it.name == f.name } == null) {
                    allFolders.add(f)
                }
            }

            allFolders.removeIf { f ->  imageFolders.firstOrNull { it.name == f.name } == null && !f.ignored }

            saveToPreferences()

            val sharedPref = context.getSharedPreferences("jpics.InstantUpload", Context.MODE_PRIVATE)
            val lastUpdate = sharedPref.getLong("last_update", 0L)

            val notUpdated = imageList.filter { p -> File(p).lastModified() > lastUpdate }
            notUpdated.sortedBy { p -> File(p).lastModified() }

            val instantUploadCat = PiwigoData.getInstantUploadCat()
            instantUploadCat?.let {
                fun uploadNext(index: Int = 0) {
                    if(index >= notUpdated.size) {
                        return
                    }

                    val path = notUpdated[index]
                    val folderPath = Uri.parse(File(path).parent).path
                    if(isFolderSynced(folderPath?:"")) {

                        Log.d("IU", "Syncing $path")

                        val file = File(path)
                        val filename = Paths.get(path).fileName.toString()
                        val createdTime = file.lastModified()
                        val bmp = BitmapFactory.decodeFile(path)

                        val imgData = PiwigoAPI.ImageUploadData(bmp, filename, Date(createdTime))
                        PiwigoData.addImages(listOf(imgData), listOf(instantUploadCat), listener = object : PiwigoData.ProgressListener{
                            override fun onStarted() {}

                            override fun onCompleted() {
                                with (sharedPref.edit()) {
                                    putLong("last_update", createdTime)
                                    apply()
                                }
                                uploadNext(index + 1)
                            }

                            override fun onProgress(progress: Float) {}

                        })
                    } else {
                        uploadNext(index + 1)
                    }
                }
                uploadNext()
            }
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
        InstantUploadManager.getInstance(applicationContext).checkForNewImages()
        return Result.success()
    }
}