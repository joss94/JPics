package fr.curlyspiker.jpics

import android.util.Log
import org.json.JSONObject
import java.net.URLEncoder

interface CategoriesManagerListener {
    fun onImagesReady(catId: Int?)
    fun onCategoriesReady()
}

object CategoriesManager {

    var pictures = mutableMapOf<Int, Picture>()
    var categories = mutableListOf<Category>()
    private var listeners: MutableList<CategoriesManagerListener?> = mutableListOf()

    var currentlyDisplayedList: MutableList<Int> = mutableListOf()

    init{ categories.add(Category(0, "Home")) }

    fun addListener(l: CategoriesManagerListener?) {
        listeners.add(l)
    }

    fun removeListener(l: CategoriesManagerListener) {
        listeners.remove(l)
    }

    fun fromID(id: Int?): Category? {
        return categories.firstOrNull { c->  c.id == id }
    }

    fun refreshAllPictures(callback: () -> Unit = {}) {

        fun refreshNext(index: Int) {
            if(index >= categories.size) {
                listeners.forEach { l -> l?.onImagesReady(null) }
                callback()
                return
            }

            val c = categories[index]
            PiwigoSession.getPictures(listOf(c)) { _, pics ->
                c.picturesIDs.clear()
                pics.forEach { p ->
                    pictures[p.id] = p
                    c.picturesIDs.add(p.id)
                }
            }
            refreshPictures(categories[index].id) {
                refreshNext(index + 1)
            }
        }
        refreshNext(0)
    }

    fun refreshPictures(catId: Int?, callback: () -> Unit = {}) {
        val c = fromID(catId)
        if(c == null) {
            refreshAllPictures(callback)
        } else {
            PiwigoSession.getPictures(listOf(c)) { _, pics ->
                c.picturesIDs.clear()
                pics.forEach { p ->
                    pictures[p.id] = p
                    c.picturesIDs.add(p.id)
                }

                listeners.forEach { l -> l?.onImagesReady(c.id) }
                callback()
            }
        }
    }

    fun getPictures(catId: Int? = null, getArchived: Boolean = false) : List<Int>  {
        return if(catId == null) {
            if(getArchived) {
                pictures.keys.toMutableList()
            } else {
                pictures.filter { e -> !e.value.isArchived }.keys.toMutableList()
            }
        } else {
            fromID(catId)?.getPictures(getArchived = getArchived || catId == getArchiveCat()?.id) ?: listOf()
        }
    }

    fun refreshCategories(callback: () -> Unit) {
        PiwigoSession.getCategories { _, cats ->
            val newCats = cats.toMutableList()
            newCats.add(fromID(0)!!)

            newCats.forEach { c ->
                val oldCat = categories.firstOrNull { it.id == c.id }
                oldCat?.let {
                    c.picturesIDs = oldCat.picturesIDs
                }
            }

            categories = newCats

            when {
                getInstantUploadCat() == null -> {
                    PiwigoSession.addCategory(URLEncoder.encode("JPicsInstantUpload<!--hidden-->", java.nio.charset.StandardCharsets.UTF_8.toString()), 0) {
                        refreshCategories(callback)
                    }
                }
                getArchiveCat() == null -> {
                    PiwigoSession.addCategory(URLEncoder.encode("JPicsArchiveFolder<!--hidden-->", java.nio.charset.StandardCharsets.UTF_8.toString()), 0) {
                        refreshCategories(callback)
                    }
                }
                getNoAlbumCat() == null -> {
                    PiwigoSession.addCategory(URLEncoder.encode("JPicsNoAlbum<!--hidden-->", java.nio.charset.StandardCharsets.UTF_8.toString()), 0) {
                        refreshCategories(callback)
                    }
                }
                else -> {
                    refreshAllPictures {}
                    listeners.forEach { l -> l?.onCategoriesReady() }
                    callback()
                }
            }
        }
    }

    fun getInstantUploadCat() : Category? {
        return categories.firstOrNull { c -> c.name.contains("JPicsInstantUpload")}
    }

    fun getArchiveCat() : Category? {
        return categories.firstOrNull { c -> c.name.contains("JPicsArchiveFolder")}
    }

    fun getNoAlbumCat() : Category? {
        return categories.firstOrNull { c -> c.name.contains("JPicsNoAlbum")}
    }

    fun getCategoriesOf(picId: Int, recursive : Boolean = false) : List<Category>{

        fun addToList(out: MutableList<Category>, c: Category, recursive: Boolean) {
            if(!out.contains(c)) {
                out.add(c)

                if(recursive) {
                    val parents = c.getHierarchy()
                    parents.forEach { parent ->
                        addToList(out, parent, recursive)
                    }
                }
            }
        }

        val out = mutableListOf<Category>()
        categories.filter { c -> c.picturesIDs.contains(picId) }.forEach { c ->addToList(out, c, recursive) }

        return out
    }
}

class Category (
    val id: Int,
    val name: String,
    private val parentId: Int = -1
    ) {

    var thumbnailId: Int = -1
    private var thumbnailUrl: String = ""

    var picturesIDs = mutableListOf<Int>()

    companion object {
        fun fromJson(json: JSONObject) : Category {
            val id = json.optInt("id")
            val name = json.optString("name")

            val rankString = json.optString("uppercats")
            val ranks = rankString.split(",")
            var parentId = 0
            if(name.contains("JPicsArchiveFolder") || name.contains("JPicsInstantUpload") || name.contains("JPicsNoAlbum")) {
                parentId = -1
            }
            else if (ranks.size >= 2) {
                parentId = ranks[ranks.size - 2].toInt()
            }

            val c = Category(id, name, parentId)
            c.thumbnailId = json.optString("representative_picture_id").toIntOrNull()?:-1
            c.thumbnailUrl = json.optString("tn_url")

            return c
        }
    }

    fun getThumbnailUrl() : String {
        if (thumbnailUrl.isEmpty()) {
            val validPic = getPictures(true).firstOrNull { id -> CategoriesManager.pictures.getValue(id).thumbnailUrl.isNotEmpty() }
            validPic?.let { thumbnailUrl = CategoriesManager.pictures.getValue(id).thumbnailUrl }
        }
        return thumbnailUrl
    }

    fun getChildren(): List<Category> {
        return CategoriesManager.categories.filter { c -> c.parentId == id}
    }

    fun getParent() : Category? {
        return CategoriesManager.categories.firstOrNull { c -> c.id == parentId }
    }

    fun getPictures(recursive: Boolean = false, getArchived: Boolean = false) : List<Int>  {

        val out = if(getArchived) {
            picturesIDs.toMutableList()
        } else {
            picturesIDs.filter { id -> !CategoriesManager.pictures.getValue(id).isArchived }
        }.toMutableList()

        if(recursive) {
            getChildren().forEach { c -> out.addAll(c.getPictures(true, getArchived)) }
        }
        return out
    }

    fun getHierarchy() : List<Category> {
        val parents: MutableList<Category> = mutableListOf()
        val parent = getParent()
        parent?.let{
            parents.addAll(parent.getHierarchy())
        }
        parents.add(this)
        return parents
    }
}

