package fr.curlyspiker.jpics

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import java.util.*

object PiwigoData {

    interface ProgressListener {
        fun onStarted()
        fun onCompleted()
        fun onProgress(progress: Float)
    }

    var currentlyDisplayedList: MutableList<Int> = Collections.synchronizedList(mutableListOf<Int>())

    private var homeCat = Category(0, "Home")

    suspend fun refreshEverything() {
        refreshUsers ()
        refreshCategories()
        refreshTags()
        refreshPictures(null)
    }

    suspend fun refreshPictures(cats: List<Int>?, cb: () -> Unit = {}): List<Picture> {
        val pictures = Collections.synchronizedList(mutableListOf<Picture>())
        suspend fun getPicturesNextPage(page: Int = 0) {
            val pics = PiwigoAPI.pwgCategoriesGetImages(cats, page = page, perPage = 500, order = "date_creation")
            pictures.addAll(pics)
            if(pics.size == 500) {
                getPicturesNextPage(page + 1)
            }
        }

        getPicturesNextPage ()

        // Delete pics that are not on the server anymore
        val picIds = pictures.map { p -> p.picId }
        if(cats != null) {
            DatabaseProvider.db.PictureCategoryDao().deletePicsNotInListFromCats(picIds.toIntArray(), cats.toIntArray())
        } else {
            DatabaseProvider.db.PictureCategoryDao().deletePicsNotInList(picIds.toIntArray())
        }

        val picCats = mutableListOf<PictureCategoryCrossRef>()
        pictures.forEach { p ->
            p.getCategoriesFromInfoJson().forEach { catId ->
                picCats.add(PictureCategoryCrossRef(p.picId, catId))
            }
        }

        DatabaseProvider.db.PictureDao().insertOrReplace(pictures)
        DatabaseProvider.db.PictureCategoryDao().insertOrReplace(picCats)
        // TODO: Insert or replace links to tags?

        cb()
        return pictures
    }

    suspend fun refreshCategories(cb: () -> Unit = {}) {
        val categories = PiwigoAPI.pwgCategoriesGetList(recursive = true)

        // Delete cats that are not in list anymore (this includes the Home...)
        val idList = categories.map { cat -> cat.catId }
        DatabaseProvider.db.CategoryDao().deleteIdsNotInList(idList)

        // Put back 'Home' category
        DatabaseProvider.db.CategoryDao().insertOrReplace(homeCat)

        // Add all new categories received from server
        DatabaseProvider.db.CategoryDao().insertOrReplace(categories)

        when {
            getInstantUploadCat() == null -> {
                addCategory("JPicsInstantUpload", visible = false)
                refreshCategories()
            }

            else -> cb()
        }
    }

    suspend fun addCategory(name: String, parentId: Int? = null, visible: Boolean = true): Int {
        val id = PiwigoAPI.pwgCategoriesAdd(name, parentId, isPublic = false, visible = visible)
        if(id > 0) {
            DatabaseProvider.db.CategoryDao().insertAll(Category(id, name, parentId ?: -1))
            refreshPictures(listOf(id))
        }
        return id
    }

    suspend fun deleteCategories(cats: List<Int>, listener: ProgressListener? = null) {
        suspend fun deleteNext(index: Int = 0) {
            if(index >= cats.size) {
                listener?.onCompleted()
            } else {
                val cat = cats[index]
                PiwigoAPI.pwgCategoriesDelete(cat, PiwigoSession.token)
                DatabaseProvider.db.CategoryDao().deleteFromId(cat)
                DatabaseProvider.db.PictureCategoryDao().deleteCat(cat)
                listener?.onProgress(index.toFloat() / cats.size)
                deleteNext(index + 1)
            }
        }

        listener?.onStarted()
        deleteNext()
    }

    suspend fun moveCategories(cats: List<Int>, parentId: Int, listener : ProgressListener? = null) {

        suspend fun moveNext(index: Int = 0) {
            if(index >= cats.size) {
                listener?.onCompleted()
            } else {
                val catID = cats[index]
                PiwigoAPI.pwgCategoriesMove(catID, PiwigoSession.token, parentId)
                DatabaseProvider.db.CategoryDao().loadOneById(catID)?.let { cat ->
                    cat.parentId = parentId
                    DatabaseProvider.db.CategoryDao().update(cat)
                }
                listener?.onProgress(index.toFloat() / cats.size)
                moveNext(index + 1)
            }
        }

        listener?.onStarted()
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

    fun getCategoriesRepresentedByPic(picId: Int) : List<Int> {
        return DatabaseProvider.db.PictureCategoryDao().getCategoriesRepresentedByPic(picId)
    }

    fun getArchivedIds(): List<Int> {
        return DatabaseProvider.db.PictureDao().getArchivedIds()
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

    private suspend  fun addImage(
        img: PiwigoAPI.ImageUploadData,
        cats: List<Int>? = null,
        tags: List<Int>? = null,
        listener: ProgressListener? = null
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
                listener?.onProgress(sent.toFloat() / bytes.size)
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
                    listener?.onCompleted()
                }else {
                    listener?.onCompleted()
                }
            } else {
                listener?.onStarted()
                sendImageByChunks()
                val newId = PiwigoAPI.pwgImagesAdd(md5sum, img.filename, img.filename, img.author,
                    img.creationDate, img.comment, cats, tags)
                if(newId > 0) {
                    val info = PiwigoAPI.pwgImagesGetInfo(newId)
                    val p = Picture.fromJson(info)
                    DatabaseProvider.db.PictureDao().insertOrReplace(p)
                    p.getCategoriesFromInfoJson().forEach { catId ->
                        DatabaseProvider.db.PictureCategoryDao().insertOrReplace(PictureCategoryCrossRef(p.picId, catId))
                    }
                }
                listener?.onCompleted()
            }
        }
    }

    suspend fun addImages(
        imgs: List<Uri>,
        contentResolver: ContentResolver,
        cats: List<Int>? = null,
        tags: List<Int>? = null,
        listener: ProgressListener? = null
    ) {

        suspend fun addNext(index: Int = 0) {
            if(index >= imgs.size) {
                listener?.onCompleted()
            } else {
                val uri = imgs[index]
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }

                var filename = ""
                var date = Calendar.getInstance().time.time

                val cursor = contentResolver.query(uri, null, null, null, null)
                if(cursor != null) {
                    cursor.moveToFirst()

                    val dateIndex: Int = cursor.getColumnIndexOrThrow("last_modified")
                    date = cursor.getString(dateIndex).toLong()

                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    filename = cursor.getString(nameIndex)

                    cursor.close()
                }

                val img = PiwigoAPI.ImageUploadData(bitmap, filename, Date(date), creationDate = Date(date))
                addImage(img, cats, tags, object : ProgressListener {
                    override fun onStarted() {
                    }
                    override fun onCompleted() {
                        listener?.onProgress( ((index + 1).toFloat()) / imgs.size )
                    }
                    override fun onProgress(progress: Float) {
                        listener?.onProgress( (index.toFloat() + progress) / imgs.size )
                    }
                })
                addNext(index + 1)
            }
        }

        listener?.onStarted()
        addNext()
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
                listener?.onProgress(index.toFloat() / pics.size)
                cats.forEach { catId ->
                    DatabaseProvider.db.PictureCategoryDao().insertOrReplace(
                        PictureCategoryCrossRef(id, catId)
                    )
                }
                moveNext(index + 1)
            } else {
                listener?.onCompleted()
            }
        }

        listener?.onStarted()
        moveNext()
    }

    suspend fun movePicsToCat(pics: List<Int>, newCategory: Int, listener : ProgressListener? = null) {
        PiwigoAPI.pwgJpicsImagesMoveToCategory(pics, newCategory)
        for (id in pics) {
            DatabaseProvider.db.PictureCategoryDao().insertOrReplace(PictureCategoryCrossRef(id, newCategory))
            DatabaseProvider.db.PictureCategoryDao().deletePicFromOtherCats(id, newCategory)
        }
        listener?.onCompleted()
    }

    suspend fun removePicsFromCat(pics: List<Int>, cat: Int, listener : ProgressListener? = null) {
        suspend fun moveNext(index: Int = 0) {
            if(index < pics.size) {
                // Apply changes remotely
                val id = pics[index]
                val cats = getPictureCategories(id).filter { it != cat }.toMutableList()
                PiwigoAPI.pwgImagesSetInfo(id, categories = cats, multipleValueMode = "replace")
                listener?.onProgress(index.toFloat() / pics.size)
                DatabaseProvider.db.PictureCategoryDao().delete(PictureCategoryCrossRef(id, cat))
                moveNext(index + 1)
            } else {
                refreshCategoryRepresentative(cat)
                listener?.onCompleted()
            }
        }

        listener?.onStarted()
        moveNext()
    }

    suspend fun refreshCategoryRepresentative(cat: Int) {
        DatabaseProvider.db.CategoryDao().loadOneById(cat)?.let { category ->
            category.getPicturesIds(true).collect {
                val validPic = it.firstOrNull { id -> DatabaseProvider.db.PictureDao().loadOneById(id)?.thumbnailUrl?.isNotEmpty() == true }
                if(validPic != null) {
                    Log.d("TAG", "Setting representative for cat $cat: $validPic")
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

    suspend fun refreshTags() {
        val tags = PiwigoAPI.pwgTagsGetAdminList()
        // Delete tags that are not in list anymore
        val idList = tags.map { tag -> tag.tagId }
        DatabaseProvider.db.TagDao().deleteIdsNotInList(idList)

        // Add all new categories received from server
        tags.forEach { t ->
            DatabaseProvider.db.TagDao().insertOrReplace(t)
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

    suspend fun refreshUsers() {
        val users = PiwigoAPI.pwgUsersGetList()
        // Delete tags that are not in list anymore
        val idList = users.map { user -> user.userId }
        DatabaseProvider.db.TagDao().deleteIdsNotInList(idList)

        // Add all new categories received from server
        users.forEach { user ->
            DatabaseProvider.db.UserDao().insertOrReplace(user)
        }
    }

    fun getInstantUploadCat() : Int? {
        return DatabaseProvider.db.CategoryDao().findByName("JPicsInstantUpload")?.catId
    }
}

