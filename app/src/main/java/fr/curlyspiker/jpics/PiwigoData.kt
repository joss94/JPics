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

interface PiwigoDataListener {
    fun onImagesReady(catId: Int?)
    fun onCategoriesReady()
}

object PiwigoData {

    interface ProgressListener {
        fun onStarted()
        fun onCompleted()
        fun onProgress(progress: Float)
    }

    private var listeners = Collections.synchronizedList(mutableListOf<PiwigoDataListener?>())

    var currentlyDisplayedList: MutableList<Int> = Collections.synchronizedList(mutableListOf<Int>())

    private var homeCat = Category(0, "Home")

    fun addListener(l: PiwigoDataListener?) {
        listeners.add(l)
    }

    fun removeListener(l: PiwigoDataListener) {
        listeners.remove(l)
    }

    fun refreshEverything() {
        refreshCategories {
            refreshPictures(null) { _, _ ->
                refreshTags {
                    refreshUsers { }
                }
            }
        }
    }

    fun refreshPictures(cats: List<Int>?, callback: (success: Boolean, pictures: List<Picture>) -> Unit) {
        val pictures = Collections.synchronizedList(mutableListOf<Picture>())
        fun getPicturesNextPage(page: Int = 0, cb: (Boolean) -> Unit) {
            PiwigoAPI.pwgCategoriesGetImages(cats, page = page, perPage = 500, order = "date_creation") { success, pics ->
                if(success) {
                    pictures.addAll(pics)
                    if(pics.size == 500) {
                        getPicturesNextPage(page + 1, cb)
                    } else {
                        cb(true)
                    }
                } else {
                    cb(false)
                }
            }
        }

        getPicturesNextPage { success ->
            if (success) {
                // Delete pics that are not on the server anymore
                val picIds = pictures.map { p -> p.picId }
                if(cats != null) {
                    DatabaseProvider.db.PictureCategoryDao().deletePicsNotInListFromCats(picIds.toIntArray(), cats.toIntArray())
                } else {
                    DatabaseProvider.db.PictureCategoryDao().deletePicsNotInList(picIds.toIntArray())
                }

                pictures.forEach { p ->
                    // Insert or replace received picture
                    DatabaseProvider.db.PictureDao().insertOrReplace(p)

                    // Insert or replace its link to categories
                    p.getCategoriesFromInfoJson().forEach { catId ->
                        DatabaseProvider.db.PictureCategoryDao().insertOrReplace(PictureCategoryCrossRef(p.picId, catId))
                    }
                    
                    // TODO: Insert or replace its links to tags?
                }
            }

            callback(success, pictures)
            if (cats != null) {
                cats.forEach { c-> listeners.forEach { it?.onImagesReady(c) } }
            } else {
                listeners.forEach { it?.onImagesReady(null) }
            }
        }
    }

    fun refreshCategories(callback: () -> Unit) {
        PiwigoAPI.pwgCategoriesGetList(recursive = true) { success, categories ->
            if(success) {
                // Delete cats that are not in list anymore (this includes the Home...)
                val idList = categories.map { cat -> cat.catId }
                DatabaseProvider.db.CategoryDao().deleteIdsNotInList(idList)

                // Put back 'Home' category
                DatabaseProvider.db.CategoryDao().insertOrReplace(homeCat)

                // Add all new categories received from server
                categories.forEach { c ->
                    DatabaseProvider.db.CategoryDao().insertOrReplace(c)
                }

                when {
                    getInstantUploadCat() == null -> {
                        addCategory("JPicsInstantUpload", visible = false) {
                            refreshCategories(callback)
                        }
                    }

                    else -> {
                        listeners.forEach { it?.onCategoriesReady() }
                        callback()
                    }
                }
            }
        }
    }

    fun addCategory(name: String, parentId: Int? = null, visible: Boolean = true, callback: (success: Boolean) -> Unit) {
        PiwigoAPI.pwgCategoriesAdd(name, parentId, isPublic = false, visible = visible) { success, id, _ ->
            if(success && id > 0) {
                DatabaseProvider.db.CategoryDao().insertAll(Category(id, name, parentId ?: -1))
                listeners.forEach { it?.onCategoriesReady() }
                refreshPictures(listOf(id)) { _, _ -> }
                callback(success)
            }
        }
    }

    fun deleteCategories(cats: List<Int>, listener: ProgressListener? = null) {
        fun deleteNext(index: Int = 0) {
            if(index >= cats.size) {
                listener?.onCompleted()
            } else {
                val cat = cats[index]
                PiwigoAPI.pwgCategoriesDelete(cat, PiwigoSession.token) { success, _ ->
                    if(success) {
                        DatabaseProvider.db.CategoryDao().deleteFromId(cat)
                        DatabaseProvider.db.PictureCategoryDao().deleteCat(cat)
                        listeners.forEach { it?.onCategoriesReady() }
                    }
                    listener?.onProgress(index.toFloat() / cats.size)
                    deleteNext(index + 1)
                }
            }
        }

        listener?.onStarted()
        deleteNext()
    }

    fun moveCategories(cats: List<Int>, parentId: Int, listener : ProgressListener? = null) {

        fun moveNext(index: Int = 0) {
            if(index >= cats.size) {
                listener?.onCompleted()
            } else {
                val catID = cats[index]
                PiwigoAPI.pwgCategoriesMove(catID, PiwigoSession.token, parentId) { success, _ ->
                    if(success) {
                        DatabaseProvider.db.CategoryDao().loadOneById(catID)?.let { cat ->
                            cat.parentId = parentId
                            DatabaseProvider.db.CategoryDao().update(cat)
                            listeners.forEach { it?.onCategoriesReady() }
                        }

                    }
                    listener?.onProgress(index.toFloat() / cats.size)
                    moveNext(index + 1)
                }
            }
        }

        listener?.onStarted()
        moveNext()
    }

    fun getAllCategories(): List<Category> {
        return DatabaseProvider.db.CategoryDao().getAll()
    }

    fun getAllTags(): List<PicTag> {
        return DatabaseProvider.db.TagDao().getAll()
    }

    fun getCategoryFromId(id: Int): Category? {
        return DatabaseProvider.db.CategoryDao().loadOneById(id)
    }

    fun getPictureFromId(id: Int): Picture? {
        return DatabaseProvider.db.PictureDao().loadOneById(id)
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

    fun setCategoryName(catId: Int, name: String, cb: () -> Unit) {
        PiwigoAPI.pwgCategoriesSetInfo(catId, name = name) { success, _ ->
            if(success) {
                DatabaseProvider.db.CategoryDao().loadOneById(catId)?.let { cat ->
                    cat.name = name
                    DatabaseProvider.db.CategoryDao().update(cat)
                    listeners.forEach { it?.onCategoriesReady() }
                }
            }
            cb()
        }
    }

    private fun addImage(
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

        fun sendImageByChunks(pos: Int = 0, chunkCb: () -> Unit) {
            val toSend = StrictMath.min(bytes.size - sent, chunkSize)
            val data = Base64.encodeToString(bytes.sliceArray(sent until sent + toSend),Base64.DEFAULT)
            PiwigoAPI.pwgImagesAddChunk(data, md5sum, (pos).toString()) { _, _ ->
                sent += toSend
                if(sent == bytes.size) {
                    chunkCb()
                } else {
                    listener?.onProgress(sent.toFloat() / bytes.size)
                    sendImageByChunks(pos+1, chunkCb)
                }
            }
        }

        PiwigoAPI.pwgImagesCheckUpload { _, isReady ->
            if(isReady) {
                PiwigoAPI.pwgImagesExist(listOf(bytes)) { success, ids ->
                    val id = ids[0]
                    if(!success) { // Error finding whether image exists or not
                        listener?.onCompleted()
                    } else if(id != null) { // Image already exists, get it directly from local pictures
                        cats?.forEach { c -> addPicsToCat(listOf(id), c) {} }
                        restorePictures(listOf(id))
                    } else {
                        listener?.onStarted()
                        sendImageByChunks {
                            PiwigoAPI.pwgImagesAdd(md5sum, img.filename, img.filename, img.author,
                                img.creationDate, img.comment, cats, tags) { success, id, _ ->
                                if(success && id > 0) {
                                    PiwigoAPI.pwgImagesGetInfo(id) { success2, rsp ->
                                        if(success2) {
                                            val p = Picture.fromJson(rsp)
                                            DatabaseProvider.db.PictureDao().insertOrReplace(p)
                                            p.getCategoriesFromInfoJson().forEach { catId ->
                                                DatabaseProvider.db.PictureCategoryDao().insertOrReplace(PictureCategoryCrossRef(p.picId, catId))
                                            }
                                            listeners.forEach { it?.onImagesReady(null) }
                                        }
                                    }
                                }
                                listener?.onCompleted()
                            }
                        }
                    }
                }
            }
        }
    }

    fun addImages(
        imgs: List<Uri>,
        contentResolver: ContentResolver,
        cats: List<Int>? = null,
        tags: List<Int>? = null,
        listener: ProgressListener? = null
    ) {

        fun addNext(index: Int = 0) {
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
                    override fun onStarted() {}
                    override fun onCompleted() { addNext(index + 1) }
                    override fun onProgress(progress: Float) {
                        listener?.onProgress( (index.toFloat() + progress) / imgs.size )
                    }
                })
            }
        }

        listener?.onStarted()
        addNext()
    }

    fun deleteImages(ids: List<Int>, callback: () -> Unit) {
        PiwigoAPI.pwgImagesDelete(ids, PiwigoSession.token) { success, _ ->
            if(success) {
                // Apply changes locally
                ids.forEach { id ->
                    DatabaseProvider.db.PictureDao().loadOneById(id)?.getRepresentedBy()?.forEach { cat ->
                        refreshCategoryRepresentative(cat)
                    }

                    DatabaseProvider.db.PictureDao().deleteFromId(id)
                    DatabaseProvider.db.PictureCategoryDao().deletePic(id)
                    DatabaseProvider.db.PictureTagDao().deletePic(id)
                }
            }

            listeners.forEach { it?.onImagesReady(null) }
            callback()
        }
    }

    fun archivePictures(picsIds: List<Int>, archive: Boolean, callback: () -> Unit = {}) {
        PiwigoAPI.pwgJpicsImagesArchive(picsIds, archive) { success, _ ->
            if (success) {
                picsIds.forEach { it ->
                    getPictureFromId(it)?.let { p ->
                        p.isArchived = archive
                        DatabaseProvider.db.PictureDao().update(p)
                    }
                    if (archive) {
                        DatabaseProvider.db.PictureDao().loadOneById(it)?.getRepresentedBy()?.forEach { cat ->
                            refreshCategoryRepresentative(cat)
                        }
                    }
                }
                callback()
                listeners.forEach { it?.onImagesReady(null) }
            }
        }
    }

    fun restorePictures(picsIds: List<Int>, callback: () -> Unit = {}) {
        archivePictures(picsIds, false, callback)
    }

    fun addPicsToCat(pics: List<Int>, newCategory: Int, listener : ProgressListener? = null, callback: () -> Unit) {
        fun moveNext(index: Int = 0) {
            if(index < pics.size) {

                // Apply changes remotely
                val id = pics[index]
                PiwigoAPI.pwgImagesSetInfo(id, categories = listOf(newCategory), multipleValueMode = "append") { success, _ ->
                    listener?.onProgress(index.toFloat() / pics.size)
                    if(success) {
                        DatabaseProvider.db.PictureCategoryDao().insertOrReplace(
                            PictureCategoryCrossRef(id, newCategory)
                        )
                    }
                    moveNext(index + 1)
                }
            } else {
                listeners.forEach { it?.onImagesReady(newCategory) }
                listener?.onCompleted()
                callback()
            }
        }

        listener?.onStarted()
        moveNext()
    }

    fun movePicsToCat(pics: List<Int>, newCategory: Int, listener : ProgressListener? = null, callback: () -> Unit) {
        PiwigoAPI.pwgJpicsImagesMoveToCategory(pics, newCategory) { success, _ ->
            if (success) {
                for (id in pics) {
                    DatabaseProvider.db.PictureCategoryDao().insertOrReplace(PictureCategoryCrossRef(id, newCategory))
                    DatabaseProvider.db.PictureCategoryDao().deletePicFromOtherCats(id, newCategory)
                }
            }
            listener?.onCompleted()
            callback()
        }
    }

    fun removePicsFromCat(pics: List<Int>, cat: Int, listener : ProgressListener? = null, callback: () -> Unit) {
        fun moveNext(index: Int = 0) {
            if(index < pics.size) {
                // Apply changes remotely
                val id = pics[index]
                val cats = DatabaseProvider.db.PictureDao().loadOneById(id)?.getCategories()?.filter { it != cat }?.toMutableList()
                PiwigoAPI.pwgImagesSetInfo(id, categories = cats, multipleValueMode = "replace") { success, _ ->
                    listener?.onProgress(index.toFloat() / pics.size)
                    if(success) {
                        DatabaseProvider.db.PictureCategoryDao().delete(PictureCategoryCrossRef(id, cat))

                    }
                    moveNext(index + 1)
                }
            } else {
                refreshCategoryRepresentative(cat)
                listeners.forEach { it?.onImagesReady(cat) }
                listener?.onCompleted()
                callback()
            }
        }

        listener?.onStarted()
        moveNext()
    }

    fun refreshCategoryRepresentative(cat: Int) {
        DatabaseProvider.db.CategoryDao().loadOneById(cat)?.let { category ->
            val validPic = category.getPictures(true).firstOrNull { id -> DatabaseProvider.db.PictureDao().loadOneById(id)?.thumbnailUrl?.isNotEmpty() == true }
            if(validPic != null) {
                Log.d("TAG", "Setting representative for cat $cat: $validPic")
                category.thumbnailUrl = DatabaseProvider.db.PictureDao().loadOneById(validPic)!!.thumbnailUrl
                PiwigoAPI.pwgCategoriesSetRepresentative(cat, validPic) { success, _ ->
                    if (success) {
                        listeners.forEach { it?.onCategoriesReady() }
                    }
                }
            } else {
                PiwigoAPI.pwgCategoriesDeleteRepresentative(cat) { success, _ ->
                    if (success) {
                        category.thumbnailUrl = ""
                        listeners.forEach { it?.onCategoriesReady() }
                    }
                }
            }
            DatabaseProvider.db.CategoryDao().update(category)
        }
    }

    fun setPicCreationDate(picId: Int, creationDate: Date, cb: () -> Unit) {
        PiwigoAPI.pwgImagesSetInfo(picId, creationDate = creationDate, singleValueMode = "replace") { success, _ ->
            if(success) {
                DatabaseProvider.db.PictureDao().loadOneById(picId)?.let { p ->
                    p.creationDate = creationDate
                    DatabaseProvider.db.PictureDao().insertOrReplace(p)
                }
                listeners.forEach { it?.onImagesReady(null) }
            }
            cb()
        }
    }

    fun setPicName(picId: Int, name: String, cb: () -> Unit) {
        PiwigoAPI.pwgImagesSetInfo(picId, name = name, singleValueMode = "replace") { success, _ ->
            if(success) {
                DatabaseProvider.db.PictureDao().loadOneById(picId)?.let { p ->
                    p.name = name
                    DatabaseProvider.db.PictureDao().insertOrReplace(p)
                }
                listeners.forEach { it?.onImagesReady(null) }
            }
            cb()
        }
    }

    fun setPicTags(picId: Int, tags: List<Int>, cb: () -> Unit) {
        PiwigoAPI.pwgImagesSetInfo(picId, tags = tags) { success, _ ->
            if(success) {
                DatabaseProvider.db.PictureTagDao().deletePic(picId)
                tags.forEach {
                    DatabaseProvider.db.PictureTagDao().insertOrReplace(PictureTagCrossRef(picId, it))
                }
                listeners.forEach { it?.onImagesReady(null) }
            }
            cb()
        }
    }

    fun refreshTags(cb: () -> Unit) {
        PiwigoAPI.pwgTagsGetAdminList { success, tags ->
            if(success) {
                // Delete tags that are not in list anymore
                val idList = tags.map { tag -> tag.tagId }
                DatabaseProvider.db.TagDao().deleteIdsNotInList(idList)

                // Add all new categories received from server
                tags.forEach { t ->
                    DatabaseProvider.db.TagDao().insertOrReplace(t)
                }

                cb()
            }
        }
    }

    fun addTags(newTags: List<PicTag>, cb: () -> Unit = {}) {
        fun addNextTag(index: Int = 0) {
            if(index >= newTags.size) {
                cb()
                return
            }

            val tag = newTags[index]
            PiwigoAPI.pwgTagsAdd(tag) { success, id, _ ->
                if (success && id != null) {
                    DatabaseProvider.db.TagDao().insertOrReplace(PicTag(id, tag.name))
                }

                addNextTag(index + 1)
            }

        }
        addNextTag()
    }

    fun refreshUsers(cb: () -> Unit) {
        PiwigoAPI.pwgUsersGetList { success, users, _ ->
            if (success) {
                // Delete tags that are not in list anymore
                val idList = users.map { user -> user.userId }
                DatabaseProvider.db.TagDao().deleteIdsNotInList(idList)

                // Add all new categories received from server
                users.forEach { user ->
                    DatabaseProvider.db.UserDao().insertOrReplace(user)
                }
            }

            cb()
        }
    }

    fun getInstantUploadCat() : Int? {
        return DatabaseProvider.db.CategoryDao().findByName("JPicsInstantUpload")?.catId
    }
}

