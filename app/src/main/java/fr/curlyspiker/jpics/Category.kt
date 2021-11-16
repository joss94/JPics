package fr.curlyspiker.jpics

import android.util.Log
import org.json.JSONObject

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
                Log.d("TAG", "Refreshed all pictures, received ${pictures.size} items")
                return
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

    fun getPictures(catId: Int? = null) : List<Int>  {
        return if(catId == null) {
            pictures.keys.toMutableList()
        } else {
            val cat = fromID(catId)
            Log.d("TAG", "True cat ${cat?.name} has ${cat?.picturesIDs?.size} pictures")
            fromID(catId)?.getPictures() ?: listOf()
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

            if(getInstantUploadCat() == null) {
                PiwigoSession.addCategory("InstantUpload", 0) {
                    refreshCategories(callback)
                }
            } else {
                listeners.forEach { l -> l?.onCategoriesReady() }
                callback()
            }
        }
    }

    fun getInstantUploadCat() : Category? {
        return categories.firstOrNull { c -> c.name == "InstantUpload" }
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
            if (ranks.size >= 2) {
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

    fun getPictures(recursive: Boolean = false) : List<Int>  {

        val out = picturesIDs.toMutableList()
        if(recursive) {
            getChildren().forEach { c -> out.addAll(c.getPictures(true)) }
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

