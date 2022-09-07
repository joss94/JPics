package fr.curlyspiker.jpics

import android.provider.ContactsContract
import android.util.Base64
import android.util.Log
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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

    var tags = ConcurrentHashMap<Int, PicTag>()
    var picturesTags = Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())

    var users = ConcurrentHashMap<Int, User>()

    private var listeners = Collections.synchronizedList(mutableListOf<PiwigoDataListener?>())

    var currentlyDisplayedList = Collections.synchronizedList(mutableListOf<Int>())

    var homeCat = Category(0, "Home")

    fun addListener(l: PiwigoDataListener?) {
        listeners.add(l)
    }

    fun removeListener(l: PiwigoDataListener) {
        listeners.remove(l)
    }

    fun refreshEverything(callback: () -> Unit) {
        refreshCategories {
            refreshAllPictures {
                refreshTags {
                    refreshUsers {
                        callback()
                    }
                }
            }
        }
    }

    fun refreshAllPictures(cb: () -> Unit) {

        refreshPictures(null) { success, pics ->
            pics.forEach { p ->
                DatabaseProvider.db.PictureDao().insertOrReplace(p)
                p.getCategoriesFromInfoJson().forEach { catId ->
                    DatabaseProvider.db.PictureCategoryDao().insertOrReplace(PictureCategoryCrossRef(p.picId, catId))
                }
            }

            listeners.forEach { it?.onImagesReady(null) }
            cb()
        }
    }

    fun refreshPictures(cats: List<Int>?, callback: (success: Boolean, pictures: List<Picture>) -> Unit) {
        val pictures = Collections.synchronizedList(mutableListOf<Picture>())
        val picIds = Collections.synchronizedList(mutableListOf<Int>())
        fun getPicturesNextPage(page: Int = 0) {
            PiwigoAPI.pwgCategoriesGetImages(cats, page = page, perPage = 500, order = "date_creation") { success, pics ->
                if(success) {
                    pictures.addAll(pics)
                    for (pic in pics) {
                        picIds.add(pic.picId)
                    }

                    if(pics.size == 500) {
                        getPicturesNextPage(page + 1)
                    } else {
                        cats?.let {
                            DatabaseProvider.db.PictureCategoryDao().deletePicsNotInCats(picIds.toIntArray(), cats.toIntArray())
                        }
                        pictures.forEach { p ->
                            p.getCategoriesFromInfoJson().forEach { catId ->
                                DatabaseProvider.db.PictureCategoryDao().insertOrReplace(PictureCategoryCrossRef(p.picId, catId))
                            }
                        }
                        callback(success, pictures)
                        cats?.forEach { c-> listeners.forEach { it?.onImagesReady(c) } }
                    }
                }
            }
        }
        getPicturesNextPage()
    }

    fun refreshCategories(callback: () -> Unit) {
        Log.d("TAG", "Refreshing categories")
        PiwigoAPI.pwgCategoriesGetList(recursive = true) { success, categories ->
            if(success) {
                DatabaseProvider.db.CategoryDao().insertOrReplace(homeCat)
                categories.forEach { c ->
                    DatabaseProvider.db.CategoryDao().insertOrReplace(c)
                }

                when {
                    getInstantUploadCat() == null -> {
                        addCategory("JPicsInstantUpload", visible = false) {
                            refreshCategories(callback)
                        }
                    }
                    getArchiveCat() == null -> {
                        addCategory("JPicsArchiveFolder", visible = false) {
                            refreshCategories(callback)
                        }
                    }
                    getNoAlbumCat() == null -> {
                        addCategory("JPicsNoAlbum", visible = false) {
                            refreshCategories(callback)
                        }
                    }
                    else -> {
                        callback()
                    }
                }

                listeners.forEach { it?.onCategoriesReady() }
                callback()
            }
        }
    }

    fun addCategory(name: String, parentId: Int? = null, visible: Boolean = true, callback: (success: Boolean) -> Unit) {
        PiwigoAPI.pwgCategoriesAdd(name, parentId, isPublic = false, visible = visible) { success, id, rsp ->
            if(success && id != null) {
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

    fun getCategoryFromId(id: Int): Category? {
        return DatabaseProvider.db.CategoryDao().loadOneById(id)
    }

    fun getPictureFromId(id: Int): Picture? {
        return DatabaseProvider.db.PictureDao().loadOneById(id)
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

        fun sendImageByChunks(pos: Int = 0, chunkCb: () -> Unit, ) {
            val toSend = StrictMath.min(bytes.size - sent, chunkSize)
            val data = Base64.encodeToString(bytes.sliceArray(sent until sent + toSend),Base64.DEFAULT)
            PiwigoAPI.pwgImagesAddChunk(data, md5sum, (pos).toString()) { success, rsp ->
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
                    } else if(id != null && DatabaseProvider.db.PictureDao().loadOneById(id) != null) { // Image already exists, get it directly from local pictures
                        if(cats?.isNotEmpty() == true){
                            cats.forEach { c -> addPicsToCat(listOf(id), c) {} }
                        }
                        restorePictures(listOf(id), listener)
                    } else {
                        listener?.onStarted()
                        sendImageByChunks {
                            PiwigoAPI.pwgImagesAdd(md5sum, img.filename, img.filename, img.author,
                                img.creationDate, img.comment, cats, tags) { success, id, _ ->
                                if(success && id !=  null) {
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
        imgs: List<PiwigoAPI.ImageUploadData>,
        cats: List<Int>? = null,
        tags: List<Int>? = null,
        listener: ProgressListener? = null
    ) {
        fun addNext(index: Int = 0) {
            if(index >= imgs.size) {
                listener?.onCompleted()
            } else {
                val img = imgs[index]
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
        PiwigoAPI.pwgImagesDelete(ids, PiwigoSession.token) { success, rsp ->
            if(success) {

                // Apply changes locally
                ids.forEach { id ->
                    DatabaseProvider.db.PictureDao().loadOneById(id)?.getRepresentedBy()?.forEach { cat ->
                        refreshCategoryRepresentative(cat)
                    }

                    DatabaseProvider.db.PictureDao().deleteFromId(id)
                    DatabaseProvider.db.PictureCategoryDao().deletePic(id)
                    picturesTags.removeIf { pair -> pair.first == id }
                }

                listeners.forEach { it?.onImagesReady(null) }
                callback()
            }
        }
    }

    fun archivePictures(picsIds: List<Int>, listener : ProgressListener? = null, callback: () -> Unit = {}) {
        PiwigoAPI.pwgJpicsImagesArchive(picsIds, true) { success, _ ->
            if (success) {
                picsIds.forEach {
                    DatabaseProvider.db.PictureDao().loadOneById(it)?.getRepresentedBy()?.forEach { cat ->
                        refreshCategoryRepresentative(cat)
                    }
                }
                callback()
            }
        }
    }

    fun restorePictures(picsIds: List<Int>, listener : ProgressListener? = null) {
        PiwigoAPI.pwgJpicsImagesArchive(picsIds, false) { _, _ -> }
    }

    fun addPicsToCat(pics: List<Int>, newCategory: Int, listener : ProgressListener? = null, callback: () -> Unit) {
        fun moveNext(index: Int = 0) {
            if(index < pics.size) {

                // Apply changes remotely
                val id = pics[index]
                PiwigoAPI.pwgImagesSetInfo(id, categories = listOf(newCategory), multipleValueMode = "append") { success, rsp ->
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
                if(cats?.isEmpty() == true) { cats.add(if(cat == getArchiveCat()) getNoAlbumCat()!! else getArchiveCat()!!) }
                PiwigoAPI.pwgImagesSetInfo(id, categories = cats, multipleValueMode = "replace") { success, rsp ->
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
                PiwigoAPI.pwgCategoriesSetRepresentative(cat, validPic) { success, rsp ->
                    listeners.forEach { it?.onCategoriesReady() }
                }
            } else {
                PiwigoAPI.pwgCategoriesDeleteRepresentative(cat) { _, _ ->
                    Log.d("TAG", "Deleting represnetative")
                    category.thumbnailUrl = ""
                    listeners.forEach { it?.onCategoriesReady() }
                }
            }
            DatabaseProvider.db.CategoryDao().update(category)
        }
    }

    fun setPicCreationDate(picId: Int, creationDate: Date, cb: () -> Unit) {
        PiwigoAPI.pwgImagesSetInfo(picId, creationDate = creationDate, singleValueMode = "replace") { success, rsp ->
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
        PiwigoAPI.pwgImagesSetInfo(picId, name = name, singleValueMode = "replace") { success, rsp ->
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
                picturesTags.removeIf { pair -> pair.first == picId }
                tags.forEach { picturesTags.add(Pair(picId, it)) }
                listeners.forEach { it?.onImagesReady(null) }
            }
            cb()
        }
    }


    fun refreshTags(cb: () -> Unit) {
        PiwigoAPI.pwgTagsGetAdminList { success, tags ->
            if(success) {
                val newTags = ConcurrentHashMap<Int, PicTag>()
                tags.forEach { t -> newTags[t.id] = t }
                this.tags = newTags
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
            PiwigoAPI.pwgTagsAdd(tag) { success, id, rsp ->
                id?.let { tags[id] = PicTag(id, tag.name) }
                addNextTag(index + 1)
            }

        }
        addNextTag()
    }




    fun refreshUsers(cb: () -> Unit) {
        PiwigoAPI.pwgUsersGetList { success, users, rsp ->
            this.users.clear()
            users.forEach { u -> this.users[u.id] = u }
            cb()
        }
    }





    fun getInstantUploadCat() : Int? {
        return DatabaseProvider.db.CategoryDao().findByName("JPicsInstantUpload")?.catId
    }

    fun getArchiveCat() : Int? {
        return DatabaseProvider.db.CategoryDao().findByName("JPicsArchiveFolder")?.catId
    }

    fun getNoAlbumCat() : Int? {
        return DatabaseProvider.db.CategoryDao().findByName("JPicsNoAlbum")?.catId
    }
}

