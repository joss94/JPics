package fr.curlyspiker.jpics

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object PiwigoData {

    interface ProgressListener {
        fun onTaskStarted()
        fun onTaskCompleted()
        fun onTaskProgress(progress: Float)
    }

    class ImageUploadData (
        val bmp: Bitmap,
        val filename: String,
        val date: Date,
        val author: String = "unknown",
        val creationDate: Date = Date(),
        val comment: String = "")

    private var progressListener: ProgressListener? = null

    private var homeCat = Category(0, "Home")

    fun setProgressListener(listener: ProgressListener) {
        progressListener = listener
    }

    suspend fun refreshEverything() {
        refreshUsers ()
        refreshCategories()
        refreshTags()
        refreshPictures()
    }

    private suspend fun refreshUsers() {
        val users = PiwigoAPI.pwgUsersGetList()
        // Delete tags that are not in list anymore
        val idList = users.map { user -> user.userId }
        DatabaseProvider.db.TagDao().deleteIdsNotInList(idList)

        // Add all new categories received from server
        users.forEach { user ->
            DatabaseProvider.db.UserDao().insertOrReplace(user)
        }
    }

    private suspend fun refreshCategories() {
        val categories = PiwigoAPI.pwgCategoriesGetList(recursive = true)

        // Delete cats that are not in list anymore (this includes the Home...)
        val idList = categories.map { cat -> cat.catId }
        DatabaseProvider.db.CategoryDao().deleteIdsNotInList(idList)

        // Put back 'Home' category
        DatabaseProvider.db.CategoryDao().insertOrReplace(homeCat)

        // Add all new categories received from server
        DatabaseProvider.db.CategoryDao().insertOrReplace(categories)
    }

    private suspend fun refreshTags() {

        val pictures = mutableListOf<Int>()
        suspend fun getTagImagesNextPage(tagId: Int, page: Int = 0, perPage: Int = 500) {
            val pics = PiwigoAPI.pwgTagsGetImages(tags = listOf(tagId), page = page, perPage = perPage, order = "date_creation")
            pictures.addAll(pics)
            if(pics.size == perPage) {
                getTagImagesNextPage(page + 1)
            }
        }

        val tags = PiwigoAPI.pwgTagsGetAdminList()

        // Delete tags that are not in list anymore
        DatabaseProvider.db.TagDao().deleteIdsNotInList(tags.map { tag -> tag.tagId })

        // Add all new tags received from server

        val picTags = mutableListOf<PictureTagCrossRef>()

        tags.forEach { t ->
            DatabaseProvider.db.TagDao().insertOrReplace(t)
            pictures.clear()
            getTagImagesNextPage(t.tagId)
            pictures.forEach {
                picTags.add(PictureTagCrossRef(it, t.tagId))
            }

            DatabaseProvider.db.PictureTagDao().removeTagFromOtherPics(t.tagId, pictures)
        }

        DatabaseProvider.db.PictureTagDao().insertOrReplace(picTags)
    }

    private suspend fun refreshPictures(): List<Picture> {
        val pictures = mutableListOf<Picture>()
        suspend fun getPicturesNextPage(page: Int = 0, perPage: Int = 500) {
            val pics = PiwigoAPI.jpicsCategoriesGetImages(null, page, perPage, "date_creation")
            pictures.addAll(pics)
            if(pics.size == perPage) {
                getPicturesNextPage(page + 1)
            }
        }

        getPicturesNextPage ()

        // Delete pics that are not on the server anymore
        val picIds = pictures.map { p -> p.picId }
        val toDelete = DatabaseProvider.db.PictureDao().getAllIds().filter { id -> !picIds.contains(id) }
        DatabaseProvider.db.PictureDao().deleteAll(toDelete)

        val picCats = mutableListOf<PictureCategoryCrossRef>()
        pictures.forEach { p ->
            val pictureCats = p.getCategoriesFromInfoJson()
            pictureCats.forEach { catId ->
                picCats.add(PictureCategoryCrossRef(p.picId, catId))
            }

            DatabaseProvider.db.PictureCategoryDao().removePicFromOtherCategories(p.picId, pictureCats.toIntArray())
        }

        DatabaseProvider.db.PictureDao().insertOrReplace(pictures)
        DatabaseProvider.db.PictureCategoryDao().insertOrReplace(picCats)

        return pictures
    }

    suspend fun addCategory(name: String, parentId: Int? = null, visible: Boolean = true): Int {
        val id = PiwigoAPI.pwgCategoriesAdd(name, parentId, isPublic = false, visible = visible)
        if(id > 0) {
            DatabaseProvider.db.CategoryDao().insertAll(Category(id, name, parentId ?: -1))
        }
        return id
    }

    suspend fun deleteCategories(cats: List<Int>) {
        suspend fun deleteNext(index: Int = 0) {
            if(index >= cats.size) {
                progressListener?.onTaskCompleted()
            } else {
                val cat = cats[index]
                PiwigoAPI.pwgCategoriesDelete(cat, PiwigoSession.token)
                DatabaseProvider.db.CategoryDao().deleteFromId(cat)
                DatabaseProvider.db.PictureCategoryDao().deleteCat(cat)
                progressListener?.onTaskProgress(index.toFloat() / cats.size)
                deleteNext(index + 1)
            }
        }

        progressListener?.onTaskStarted()
        deleteNext()
    }

    suspend fun moveCategories(cats: List<Int>, parentId: Int) {

        suspend fun moveNext(index: Int = 0) {
            if(index >= cats.size) {
                progressListener?.onTaskCompleted()
            } else {
                val catID = cats[index]
                PiwigoAPI.pwgCategoriesMove(catID, PiwigoSession.token, parentId)
                DatabaseProvider.db.CategoryDao().loadOneById(catID)?.let { cat ->
                    cat.parentId = parentId
                    DatabaseProvider.db.CategoryDao().update(cat)
                }
                progressListener?.onTaskProgress(index.toFloat() / cats.size)
                moveNext(index + 1)
            }
        }

        progressListener?.onTaskStarted()
        moveNext()
    }

    fun getAllCategories(): List<Category> {
        return DatabaseProvider.db.CategoryDao().getAll()
    }

    fun getCategoryParentsTree(catId: Int): List<Int> {
        val parents: MutableList<Int> = mutableListOf()

        getCategoryFromId(catId)?.let {
            parents.add(catId)
            parents.addAll(getCategoryParentsTree(it.parentId))
        }

        return parents
    }

    fun getAllTags(): List<PicTag> {
        return DatabaseProvider.db.TagDao().getAll()
    }

    fun getCategoryFromId(id: Int): Category? {
        return DatabaseProvider.db.CategoryDao().loadOneById(id)
    }

    fun getCategoriesFromIds(ids: List<Int>): List<Category> {
        return DatabaseProvider.db.CategoryDao().loadManyById(ids)
    }

    fun getPictureFromId(id: Int): Picture? {
        return DatabaseProvider.db.PictureDao().loadOneById(id)
    }

    fun getPicturesFromIds(ids: List<Int>): List<Picture> {
        return DatabaseProvider.db.PictureDao().loadManyById(ids)
    }

    fun getPictureCategories(id: Int, recursive: Boolean = false): List<Int> {
        val out = mutableListOf<Int>()
        val directParents = DatabaseProvider.db.PictureCategoryDao().getParentsIds(id)
        for (cat in directParents) {
            out.add(cat)

            if(recursive) {
                val parents = getCategoryParentsTree(cat)
                parents.forEach { parent ->
                    if (!out.contains(parent)) {
                        out.add(parent)
                    }
                }
            }
        }
        return out
    }

    suspend fun getPictureInfo(picId: Int): JSONObject {
        return PiwigoAPI.pwgImagesGetInfo(picId)
    }

    private fun getCategoriesRepresentedByPic(picId: Int) : List<Int> {
        return DatabaseProvider.db.PictureCategoryDao().getCategoriesRepresentedByPic(picId)
    }

    fun getTagFromId(id: Int): PicTag? {
        return DatabaseProvider.db.TagDao().loadOneById(id)
    }

    fun getUserFromId(id: Int): User? {
        return DatabaseProvider.db.UserDao().loadOneById(id)
    }

    fun getTagFromName(name: String): PicTag? {
        return DatabaseProvider.db.TagDao().findByName(name)
    }

    suspend fun setCategoryName(catId: Int, name: String) {
        PiwigoAPI.pwgCategoriesSetInfo(catId, name = name)
        DatabaseProvider.db.CategoryDao().loadOneById(catId)?.let { cat ->
            cat.name = name
            DatabaseProvider.db.CategoryDao().update(cat)
        }
    }

    suspend fun addImagesFromContentUris(
        images: List<Uri>,
        contentResolver: ContentResolver,
        cats: List<Int>? = null,
        tags: List<Int>? = null
    ) {

        val imagesUploadData = images.mapNotNull { uri ->
            var filename = ""
            var date = Calendar.getInstance().time.time

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            val cursor = contentResolver.query(uri, null, null, null, null)
            if(cursor != null) {
                cursor.moveToFirst()
                try {
                    val dateIndex: Int = cursor.getColumnIndex("last_modified")
                    date = cursor.getString(dateIndex).toLong()
                } catch (e: Exception) {}

                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                filename = cursor.getString(nameIndex)

                cursor.close()
            }

            if (filename != "") ImageUploadData(bitmap, filename, Date(date), creationDate = Date(date)) else null
        }
        addImages(imagesUploadData, cats, tags)
    }

    suspend fun addImagesFromFilePaths(
        images: List<String>,
        cats: List<Int>? = null,
        tags: List<Int>? = null
    ) {
        val imagesUploadData = images.mapNotNull { path ->
            val filename = File(path).name
            val bitmap = BitmapFactory.decodeFile(path)
            val date = File(path).lastModified()

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            val current = LocalDateTime.ofInstant(Instant.ofEpochMilli(date),
                TimeZone.getDefault().toZoneId()).format(formatter)
            Log.d("TAG", current);

            if (filename != "") ImageUploadData(bitmap, filename, Date(date), creationDate = Date(date)) else null
        }
        addImages(imagesUploadData, cats, tags)
    }

    private suspend fun addImages(images: List<ImageUploadData>, cats: List<Int>? = null, tags: List<Int>? = null) {

        suspend fun addNext(index: Int = 0) {
            if (index == 0) {
                progressListener?.onTaskStarted()
            }
            if (index >= images.size) {
                progressListener?.onTaskCompleted()
            } else {
                addImage(images[index], cats, tags, object : ProgressListener {
                    override fun onTaskStarted() {}

                    override fun onTaskCompleted() {
                        progressListener?.onTaskProgress(((index + 1).toFloat()) / images.size)
                    }

                    override fun onTaskProgress(progress: Float) {
                        progressListener?.onTaskProgress((index.toFloat() + progress) / images.size)
                    }
                })
                addNext(index + 1)
            }
        }

        addNext()
    }

    private suspend  fun addImage(
        img: ImageUploadData,
        cats: List<Int>? = null,
        tags: List<Int>? = null,
        listener: ProgressListener?
    ) {
        val bmp = img.bmp
        val bytes = Utils.imgToByteArray(bmp)
        val md5sum = Utils.md5(bytes)
        val chunkSize = 500_000

        var sent = 0

        suspend fun sendImageByChunks(pos: Int = 0) {
            val toSend = StrictMath.min(bytes.size - sent, chunkSize)
            val data = Base64.encodeToString(bytes.sliceArray(sent until sent + toSend),Base64.DEFAULT)
            PiwigoAPI.pwgImagesAddChunk(data, md5sum, (pos).toString())
            sent += toSend
            if(sent!= bytes.size) {
                listener?.onTaskProgress(sent.toFloat() / bytes.size)
                sendImageByChunks(pos+1)
            }
        }

        val isReady = PiwigoAPI.pwgImagesCheckUpload()
        if(isReady) {
            val ids = PiwigoAPI.pwgImagesExist(listOf(bytes))
            val id = ids[0]
            if(id != null) { // Image already exists, get it directly from local pictures*
                addPicsToCats(listOf(id), cats ?: listOf())
                if (getPictureFromId(id)?.isArchived == true) {
                    restorePictures(listOf(id))
                }
            } else {
                listener?.onTaskStarted()
                sendImageByChunks()
                val newId = PiwigoAPI.pwgImagesAdd(md5sum, img.filename, img.filename, img.author,
                    img.creationDate, img.comment, cats, tags)
                if(newId > 0) {
                    val p = Picture.fromJson(PiwigoAPI.pwgImagesGetInfo(newId))
                    DatabaseProvider.db.PictureDao().insertOrReplace(p)
                    p.getCategoriesFromInfoJson().forEach { catId ->
                        DatabaseProvider.db.PictureCategoryDao().insertOrReplace(PictureCategoryCrossRef(p.picId, catId))
                    }
                }
            }
            listener?.onTaskCompleted()
        }
    }

    suspend fun deleteImages(ids: List<Int>) {
        PiwigoAPI.pwgImagesDelete(ids, PiwigoSession.token)

        // Apply changes locally
        ids.forEach { id ->
            getCategoriesRepresentedByPic(id).forEach { cat ->
                refreshCategoryRepresentative(cat)
            }

            DatabaseProvider.db.PictureDao().deleteFromId(id)
            DatabaseProvider.db.PictureCategoryDao().deletePic(id)
            DatabaseProvider.db.PictureTagDao().deletePic(id)
        }
    }

    suspend fun archivePictures(picsIds: List<Int>, archive: Boolean) {
        PiwigoAPI.pwgJpicsImagesArchive(picsIds, archive)
        picsIds.forEach {
            getPictureFromId(it)?.let { p ->
                p.isArchived = archive
                DatabaseProvider.db.PictureDao().update(p)
            }
            if (archive) {
                getCategoriesRepresentedByPic(it).forEach { cat ->
                    refreshCategoryRepresentative(cat)
                }
            }
        }
    }

    suspend fun restorePictures(picsIds: List<Int>) {
        archivePictures(picsIds, false)
    }

    suspend fun addPicsToCats(pics: List<Int>, cats: List<Int>, listener : ProgressListener? = null) {
        suspend fun moveNext(index: Int = 0) {
            if(index < pics.size) {

                // Apply changes remotely
                val id = pics[index]
                PiwigoAPI.pwgImagesSetInfo(id, categories = cats, multipleValueMode = "append")
                listener?.onTaskProgress(index.toFloat() / pics.size)
                cats.forEach { catId ->
                    DatabaseProvider.db.PictureCategoryDao().insertOrReplace(
                        PictureCategoryCrossRef(id, catId)
                    )
                }
                moveNext(index + 1)
            } else {
                listener?.onTaskCompleted()
            }
        }

        listener?.onTaskStarted()
        moveNext()
    }

    suspend fun movePicsToCat(pics: List<Int>, newCategory: Int, listener : ProgressListener? = null) {
        PiwigoAPI.pwgJpicsImagesMoveToCategory(pics, newCategory)
        for (id in pics) {
            DatabaseProvider.db.PictureCategoryDao().insertOrReplace(PictureCategoryCrossRef(id, newCategory))
            DatabaseProvider.db.PictureCategoryDao().deletePicFromOtherCats(id, newCategory)
        }
        listener?.onTaskCompleted()
    }

    suspend fun removePicsFromCat(pics: List<Int>, cat: Int, listener : ProgressListener? = null) {
        suspend fun moveNext(index: Int = 0) {
            if(index < pics.size) {
                // Apply changes remotely
                val id = pics[index]
                val cats = getPictureCategories(id).filter { it != cat }.toMutableList()
                PiwigoAPI.pwgImagesSetInfo(id, categories = cats, multipleValueMode = "replace")
                listener?.onTaskProgress(index.toFloat() / pics.size)
                DatabaseProvider.db.PictureCategoryDao().delete(PictureCategoryCrossRef(id, cat))
                moveNext(index + 1)
            } else {
                refreshCategoryRepresentative(cat)
                listener?.onTaskCompleted()
            }
        }

        listener?.onTaskStarted()
        moveNext()
    }

    private suspend fun refreshCategoryRepresentative(cat: Int) {
        DatabaseProvider.db.CategoryDao().loadOneById(cat)?.let { category ->
            category.getPicturesIds(true).collect {
                val validPic = it.firstOrNull { id -> DatabaseProvider.db.PictureDao().loadOneById(id)?.thumbnailUrl?.isNotEmpty() == true }
                if(validPic != null) {
                    category.thumbnailUrl = DatabaseProvider.db.PictureDao().loadOneById(validPic)!!.thumbnailUrl
                    PiwigoAPI.pwgCategoriesSetRepresentative(cat, validPic)
                } else {
                    PiwigoAPI.pwgCategoriesDeleteRepresentative(cat)
                    category.thumbnailUrl = ""
                }
                DatabaseProvider.db.CategoryDao().update(category)
            }
        }
    }

    suspend fun setPicCreationDate(picId: Int, creationDate: Date) {
        PiwigoAPI.pwgImagesSetInfo(picId, creationDate = creationDate, singleValueMode = "replace")
        DatabaseProvider.db.PictureDao().loadOneById(picId)?.let { p ->
            p.creationDate = creationDate
            DatabaseProvider.db.PictureDao().insertOrReplace(p)
        }
    }

    suspend fun setPicName(picId: Int, name: String) {
        PiwigoAPI.pwgImagesSetInfo(picId, name = name, singleValueMode = "replace")
        DatabaseProvider.db.PictureDao().loadOneById(picId)?.let { p ->
            p.name = name
            DatabaseProvider.db.PictureDao().insertOrReplace(p)
        }

    }

    suspend fun setPicTags(picId: Int, tags: List<Int>) {
        PiwigoAPI.pwgImagesSetInfo(picId, tags = tags)
        DatabaseProvider.db.PictureTagDao().deletePic(picId)
        tags.forEach {
            DatabaseProvider.db.PictureTagDao().insertOrReplace(PictureTagCrossRef(picId, it))
        }
    }

    suspend fun addTags(newTags: List<PicTag>) {
        suspend fun addNextTag(index: Int = 0) {
            if(index < newTags.size) {
                val tag = newTags[index]
                val id = PiwigoAPI.pwgTagsAdd(tag)
                if (id > 0) {
                    DatabaseProvider.db.TagDao().insertOrReplace(PicTag(id, tag.name))
                }

                addNextTag(index + 1)
            }
        }
        addNextTag()
    }
}

