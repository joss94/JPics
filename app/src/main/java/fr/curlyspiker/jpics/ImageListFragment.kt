package fr.curlyspiker.jpics

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupTextProvider
import java.text.SimpleDateFormat
import java.util.*
import androidx.navigation.fragment.findNavController

class ILFViewModel(private var catId: Int?) : ViewModel() {

    private var isArchive: Boolean = false

    private var fullList = listOf<Picture>()
    private val imageList = MutableLiveData<List<Picture>>()

    private var query: String = ""

    private var flowJob: Job? = null

    init {
        loadPictures()
    }

    fun setArchive(isArchive: Boolean) {
        this.isArchive = isArchive
        filterImages()
    }

    fun setCategory(catId: Int?) {
        this.catId = catId
        loadPictures()
    }

    fun getPictures(): LiveData<List<Picture>> {
        return imageList
    }

    fun getCatId(): Int? {
        return catId
    }

    private fun loadPictures() {
        flowJob?.cancel()
        flowJob = viewModelScope.launch(Dispatchers.IO) {
            val category = PiwigoData.getCategoryFromId(catId ?: -1)
            if (category != null) {
                category.getPictures().collect { pics ->
                    fullList = pics.sortedWith(compareByDescending<Picture>{ it.creationDate }.thenBy{ it.name })
                    filterImages()
                }
            } else {
                DatabaseProvider.db.PictureDao().getAllPictures().collect { pics ->
                    fullList = pics.sortedWith(compareByDescending<Picture>{ it.creationDate }.thenBy{ it.name })
                    filterImages()
                }
            }
        }
    }

    private fun filterImages() {
        viewModelScope.launch(Dispatchers.IO) {
            var filteredPictures = if(query.isNotEmpty()) {
                val filteredTags = PiwigoData.getAllTags().filter { it.name.lowercase().contains(query) }.map { it.tagId }
                val filteredCats = PiwigoData.getAllCategories().filter {
                    PiwigoData.getCategoriesFromIds(PiwigoData.getCategoryParentsTree(it.catId)).any {
                            parent -> parent.name.lowercase().contains(query)
                    }
                }.map { it.catId }

                fullList.filter { pic ->
                    pic.name.lowercase().contains(query) ||
                            filteredTags.intersect(pic.getTags().toSet()).isNotEmpty() ||
                            PiwigoData.getPictureCategories(pic.picId).intersect(filteredCats.toSet()).isNotEmpty()
                }
            } else { fullList }

            filteredPictures = filteredPictures.filter { p -> p.isArchived == isArchive }

            imageList.postValue(filteredPictures)
        }
    }

    fun setFilter(query: String) {
        this.query = query.lowercase().trim()
        filterImages()
    }

    fun addPictures(uris: List<Uri>, ctx: Context, cats: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.addImagesFromContentUris(uris, ctx.contentResolver, cats)
        }
    }
}

class ILFViewModelFactory(private val catId: Int?) :
    ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ILFViewModel(catId) as T
        }
    }

class ImageListFragment (startCat: Int? = 0, val isArchive: Boolean = false): Fragment() {

    private val imageListVM: ILFViewModel by viewModels { ILFViewModelFactory(startCat) }
    private val mainActivityVM: MainActivityViewModel by activityViewModels()

    private var actionMode : ActionMode? = null

    private lateinit var picturesView: RecyclerView
    private var picturesAdapter: PicturesListAdapter? = null
    private lateinit var noImageView: ImageView

    private var imgReq = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onImagesPicked(result.data)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_image_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imgReq = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                onImagesPicked(result.data)
            }
        }

        picturesView = view.findViewById(R.id.pictures_grid_view)
        picturesView.itemAnimator = null
        noImageView = view.findViewById(R.id.no_image_view)

        picturesAdapter = PicturesListAdapter(this)
        val layoutManager = GridLayoutManager(requireContext(), 3)
        picturesView.layoutManager = layoutManager
        layoutManager.spanSizeLookup = object: GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(pos: Int) : Int {
                return if(picturesAdapter?.isHeader(pos) == true) 3 else 1
            }
        }
        picturesView.adapter  = picturesAdapter

        val builder = FastScrollerBuilder(picturesView)
        builder.setPopupStyle { v ->
            v.setBackgroundColor(context?.getColor(R.color.black)!!)
            v.textSize = 20.0f
            (v.layoutParams as ViewGroup.MarginLayoutParams).setMargins(0, 0, 200, 0)
            v.setPadding(20, 20, 20, 20)
        }
        builder.build()

        picturesAdapter?.selectionListener = object : PicturesListAdapter.SelectionListener() {
            override fun onSelectionEnabled() {
                actionMode = activity?.startActionMode(object: ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        val menuInflater: MenuInflater? = activity?.menuInflater
                        val menuRes = if(isArchive) R.menu.explorer_menu_archive else R.menu.explorer_menu
                        menuInflater?.inflate(menuRes, menu)
                        picturesAdapter?.selecting = true
                        return true
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        picturesAdapter?.let{
                            val selectedPics = it.getSelectedPictures()
                            if(selectedPics.isNotEmpty()) {
                                when (item?.itemId) {
                                    R.id.action_download -> (activity as MainActivity).downloadImages(selectedPics)
                                    R.id.action_share -> {
                                        val pics = selectedPics.mapNotNull { id -> imageListVM.getPictures().value?.find { pic -> pic.picId == id} }
                                        (activity as MainActivity).shareImages(pics)
                                    }
                                    R.id.action_select_all -> picturesAdapter?.selectAll()
                                    R.id.action_move -> (activity as MainActivity).moveImages(imageListVM.getCatId(), selectedPics)
                                    R.id.action_delete -> (activity as MainActivity).archiveImages(selectedPics)
                                    R.id.action_delete_definitive -> (activity as MainActivity).deleteImages(selectedPics)
                                    R.id.action_restore -> (activity as MainActivity).restoreImages(selectedPics)
                                    R.id.action_remove_from_album -> (activity as MainActivity).removeImagesFromCat(imageListVM.getCatId(), selectedPics)
                                    R.id.action_creation_date -> (activity as MainActivity).editImagesCreationDate(selectedPics)
                                    else -> {}
                                }
                                mode?.finish()
                            }
                        }
                        return true
                    }

                    override fun onDestroyActionMode(p0: ActionMode?) {
                        picturesAdapter?.exitSelectionMode()
                        picturesView.invalidate()
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, p1: Menu?): Boolean { return true }
                })
            }
        }

        val addImagesButton = view.findViewById<Button>(R.id.add_image_button)
        addImagesButton.setOnClickListener { addImages() }

        val addCategoryButton = view.findViewById<Button>(R.id.add_category_button)
        addCategoryButton.setOnClickListener {
            (activity as MainActivity).addCategory(imageListVM.getCatId() ?: 0)
        }

        imageListVM.setArchive(isArchive)
        imageListVM.getPictures().observe(viewLifecycleOwner, Observer { pictures ->
            lifecycleScope.launch(Dispatchers.IO) {
                picturesAdapter?.replaceAll(pictures)
            }
        })
    }

    fun onItemsChanged() {
        val hasItems = (picturesAdapter?.itemCount ?: 0) > 0
        picturesView.visibility = if(hasItems) View.VISIBLE else View.GONE
        noImageView.visibility = if(hasItems) View.GONE else View.VISIBLE
    }

    fun setCategory(c: Int?) {
        if(isAdded && context != null) {
            imageListVM.setCategory(c)
        }
    }

    private fun addImages() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.putExtra(Intent.EXTRA_TITLE, "Select images to add")
        imgReq.launch(intent)
    }

    private fun onImagesPicked(data: Intent?) {
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

        (activity as MainActivity).selectCategory(imageListVM.getCatId()) { c ->
            imageListVM.addPictures(uris, requireContext(), listOf(c))
        }
    }

    fun setFilter(query: String) {
        imageListVM.setFilter(query)
    }

    class PicturesListAdapter(private val fragment: ImageListFragment) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        SectionIndexer,
        PopupTextProvider {

        private val ITEM_VIEW_TYPE_HEADER = -1
        private val ITEM_VIEW_TYPE_ITEM = -2

        sealed class DataItem {
            data class PictureItem(val picture: Picture) : DataItem() {
                override val id = picture.picId.toLong()
                override val creationDay = picture.creationDay
                var checked = false
            }

            data class Header(override val creationDay: Date): DataItem() {
                override val id = creationDay.hashCode().toLong()
            }

            abstract val id: Long
            abstract val creationDay: Date
        }


        abstract class SelectionListener {
            abstract fun onSelectionEnabled()
        }

        var selectionListener: SelectionListener? = null

        private val mContext = fragment.requireContext()
        var selecting: Boolean = false

        private var sections: MutableList<Date> = mutableListOf()

        private var items = listOf<DataItem>()
        private var pictures = listOf<Picture>()

        fun isHeader(pos: Int) : Boolean {
            return items[pos] is DataItem.Header
        }

        private fun showImageFullscreen(position: Int) {
            fragment.mainActivityVM.setCatId(fragment.imageListVM.getCatId())
            fragment.mainActivityVM.setPicId(getItemId(position).toInt())

            fragment.findNavController().navigate(R.id.action_showImageViewer)

           (fragment.activity as MainActivity).setDisplayedPics(fragment.imageListVM.getPictures())
        }

        fun getSelectedPictures() : List<Int> {
            val selectedPictures = mutableListOf<Int>()
            items.filter { i -> i is DataItem.PictureItem && i.checked }.forEach { selectedPictures.add((it as DataItem.PictureItem).picture.picId) }
            return selectedPictures
        }

        fun replaceAll(picsList: List<Picture>) {
            pictures = picsList
            val groupedList = pictures.groupBy { p -> p.creationDay }

            // Create list items
            val selected = getSelectedPictures()

            val newItems = mutableListOf<DataItem>()
            for(i in groupedList.keys){
                newItems.add(DataItem.Header(i))
                for(v in groupedList.getValue(i)){
                    val item = DataItem.PictureItem(v)
                    item.checked = selected.contains(v.picId)
                    newItems.add(item)
                }
            }

            // Update list
            fragment.activity?.runOnUiThread {
                items = newItems
                updateSections()
                fragment.onItemsChanged()
                notifyDataSetChanged()
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        private fun setItemChecked(index: Int, checked: Boolean) {
            if(checked) {
                if(!selecting) {
                    selecting = true
                    selectionListener?.onSelectionEnabled()
                }
            }
            (items[index] as DataItem.PictureItem).checked = checked
            fragment.actionMode?.title = getSelectedPictures().size.toString()
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun exitSelectionMode() {
            selecting = false
            for(i in items) {
                if (i is DataItem.PictureItem) {
                    i.checked = false
                }
            }
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun selectAll() {
            selecting = true
            for(i in items) {
                if (i is DataItem.PictureItem) {
                    i.checked = true
                }
            }
            fragment.actionMode?.title = getSelectedPictures().size.toString()
            notifyDataSetChanged()
        }

        // stores and recycles views as they are scrolled off screen
        class PicViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var icon: ImageView = itemView.findViewById(R.id.image_tile_image)
            var checkBox: CheckBox = itemView.findViewById(R.id.image_tile_checkbox)
            var wideButton: ImageButton = itemView.findViewById(R.id.image_tile_wide)
            var tile: CardView = itemView.findViewById(R.id.cardview)
        }

        class TextViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var label: TextView = itemView.findViewById(R.id.section_title)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                ITEM_VIEW_TYPE_HEADER -> {
                    val view: View =  LayoutInflater.from(fragment.requireContext()).inflate(R.layout.section_tile, parent, false)
                    TextViewHolder(view)
                }
                ITEM_VIEW_TYPE_ITEM -> {
                    val view: View =  LayoutInflater.from(fragment.requireContext()).inflate(R.layout.image_tile, parent, false)
                    PicViewHolder(view)
                }
                else -> throw ClassCastException("Unknown viewType $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, p0: Int) {

            when (holder) {
                is PicViewHolder -> {
                    val p = items[p0] as DataItem.PictureItem
                    Picasso.with(mContext).load(p.picture.thumbnailUrl).into(holder.icon)

                    val tile = holder.tile
                    val icon = holder.icon
                    val checkbox = holder.checkBox
                    val wideButton = holder.wideButton

                    checkbox.visibility = if(selecting) View.VISIBLE else View.INVISIBLE
                    checkbox.isChecked = p.checked
                    checkbox.setOnClickListener { setItemChecked(p0, !p.checked) }

                    tile.radius = if(p.checked) 20.0f else 0.0f
                    val tileLayoutParams = tile.layoutParams as RelativeLayout.LayoutParams
                    val margin = if(p.checked) 40 else 0
                    tileLayoutParams.setMargins(margin, margin, margin, margin)
                    tile.layoutParams = tileLayoutParams

                    wideButton.visibility = if(selecting) View.VISIBLE else View.INVISIBLE
                    wideButton.setOnClickListener {
                        showImageFullscreen(p0)
                    }

                    (holder.itemView.layoutParams as GridLayoutManager.LayoutParams).rightMargin = 5
                    (holder.itemView.layoutParams as GridLayoutManager.LayoutParams).topMargin = 5

                    icon.setOnClickListener {
                        if(selecting) {
                            setItemChecked(p0, !p.checked)
                        } else {
                            showImageFullscreen(p0)
                        }
                    }

                    icon.setOnLongClickListener {
                        setItemChecked(p0, true)
                        true
                    }
                }
                is TextViewHolder -> {
                    val headerItem = items[p0] as DataItem.Header
                    val cal = Calendar.getInstance()
                    val currentYear = cal.get(Calendar.YEAR)
                    cal.time = headerItem.creationDay
                    val headerYear = cal.get(Calendar.YEAR)
                    var format = "EEE. d MMM."
                    if (headerYear != currentYear)
                        format += " yyyy"
                    holder.label.text = SimpleDateFormat(format, Locale.US).format(headerItem.creationDay)
                }
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun getItemId(position: Int): Long {
            return items[position].id
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is DataItem.Header -> ITEM_VIEW_TYPE_HEADER
                is DataItem.PictureItem -> ITEM_VIEW_TYPE_ITEM
            }
        }

        private fun updateSections() {
            sections.clear()
            for(i in items.filterIsInstance<DataItem.Header>()) {
                sections.add(i.creationDay)
            }
        }

        override fun getSections(): Array<Any> {
            return sections.toTypedArray()
        }

        override fun getPositionForSection(p0: Int): Int {
            val section = sections[p0]
            var index = -1
            for(i in items.indices) {
                val item = items[i]
                if(section >= item.creationDay) {
                    index = i
                    break
                }
            }
            return index
        }

        override fun getSectionForPosition(p0: Int): Int {
            val sections = sections
            val picDate = (items[p0] as DataItem.PictureItem).picture.creationDay
            return sections.indexOfFirst { s -> s <= picDate }
        }

        override fun getPopupText(position: Int): String {
            val simpleDate = SimpleDateFormat("EEE. d MMM. yyyy", Locale.US)
            return simpleDate.format(items[position].creationDay)
        }
    }
}