package fr.curlyspiker.jpics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.provider.ContactsContract
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
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
import androidx.recyclerview.widget.LinearLayoutManager

class ILFViewModel(private var catId: Int?) : ViewModel() {

    private var isArchive: Boolean = false

    private var fullList = listOf<Picture>()
    private val imageList = MutableLiveData<List<Picture>>()

    private var query: String = ""

    private var picsJob: Job? = null
    private var catJob: Job? = null

    private val category = MutableLiveData<CategoryWithChildren>()

    init {
        loadPictures()
        loadCategory()
    }

    fun setArchive(isArchive: Boolean) {
        this.isArchive = isArchive
        filterImages()
    }

    fun setCategory(catId: Int?) {
        this.catId = catId
        loadCategory()
        loadPictures()
    }

    fun getCategory(): LiveData<CategoryWithChildren> {
        return category
    }

    fun getPictures(): LiveData<List<Picture>> {
        return imageList
    }

    fun getCatId(): Int? {
        return catId
    }

    private fun loadPictures() {
        picsJob?.cancel()
        picsJob = viewModelScope.launch(Dispatchers.IO) {
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

    private fun loadCategory() {
        catJob?.cancel()
        catJob = viewModelScope.launch(Dispatchers.IO) {
            DatabaseProvider.db.CategoryDao().loadOneByIdWithChildrenFlow(catId ?: 0)?.collect {
                category.postValue(it)
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
}

class ILFViewModelFactory(private val catId: Int?) :
    ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ILFViewModel(catId) as T
        }
    }

class ImageListFragment (startCat: Int? = 0, val isArchive: Boolean = false, val showAlbums: Boolean = false): Fragment() {

    private val imageListVM: ILFViewModel by viewModels { ILFViewModelFactory(startCat) }
    private val mainActivityVM: MainActivityViewModel by activityViewModels()

    private var actionMode : ActionMode? = null

    private lateinit var picturesView: RecyclerView
    private var picturesAdapter: PicturesListAdapter? = null
    private lateinit var noImageView: ImageView

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val parent = imageListVM.getCategory().value?.category?.parentId ?: -1
            if (PiwigoData.getCategoryFromId(parent) != null) {
                setCategory(parent)
            } else {
                activity?.moveTaskToBack(true)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_image_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                                    R.id.action_share -> {(activity as MainActivity).shareImages(selectedPics) }
                                    R.id.action_select_all -> picturesAdapter?.selectAll()
                                    R.id.action_move -> (activity as MainActivity).moveImages(imageListVM.getCatId(), selectedPics)
                                    R.id.action_delete -> (activity as MainActivity).archiveImages(selectedPics)
                                    R.id.action_delete_definitive -> (activity as MainActivity).deleteImages(selectedPics)
                                    R.id.action_restore -> (activity as MainActivity).restoreImages(selectedPics)
                                    R.id.action_remove_from_album -> (activity as MainActivity).removeImagesFromCat(imageListVM.getCatId(), selectedPics)
                                    R.id.action_creation_date -> (activity as MainActivity).editImagesCreationDate(selectedPics)
                                    else -> {}
                                }
                            }
                        }

                        mode?.finish()
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

        imageListVM.setArchive(isArchive)
        imageListVM.getPictures().observe(viewLifecycleOwner, Observer { pictures ->
            lifecycleScope.launch(Dispatchers.IO) {
                picturesAdapter?.replaceAll(pictures)
            }
        })

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    override fun onPause() {
        super.onPause()
        backPressedCallback.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        backPressedCallback.isEnabled = true
    }

    fun setCategory(c: Int?) {
        if(isAdded && context != null) {
            imageListVM.setCategory(c)
        }
    }

    fun setFilter(query: String) {
        imageListVM.setFilter(query)
    }

    class PicturesListAdapter(private val fragment: ImageListFragment) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        SectionIndexer,
        PopupTextProvider {

        private val ITEM_VIEW_TYPE_MAIN_HEADER = -1
        private val ITEM_VIEW_TYPE_HEADER = -2
        private val ITEM_VIEW_TYPE_ITEM = -3

        sealed class DataItem {
            data class PictureItem(val picture: Picture) : DataItem() {
                override val id = picture.picId.toLong()
                override val creationDay = picture.creationDay
                var checked = false
            }

            data class Header(override val creationDay: Date): DataItem() {
                override val id = creationDay.hashCode().toLong()
            }

            data class MainHeader(override val creationDay: Date = Date(0)): DataItem() {
                override val id = creationDay.hashCode().toLong()
            }

            abstract val id: Long
            abstract val creationDay: Date
        }


        abstract class SelectionListener {
            abstract fun onSelectionEnabled()
        }

        var selectionListener: SelectionListener? = null
        var selecting: Boolean = false

        private var sections = listOf<Date>()

        private var items = listOf<DataItem>()
        private var pictures = listOf<Picture>()

        fun isHeader(pos: Int) : Boolean {
            return items[pos] is DataItem.Header || items[pos] is DataItem.MainHeader
        }

        private fun showImageFullscreen(position: Int) {
            fragment.mainActivityVM.setCatId(fragment.imageListVM.getCatId())
            fragment.mainActivityVM.setPicId(getItemId(position).toInt())
            (fragment.activity as MainActivity).setDisplayedPics(fragment.imageListVM.getPictures())
            fragment.findNavController().navigate(R.id.action_showImageViewer)
        }

        fun getSelectedPictures() : List<Int> {
            return items.filter { it is DataItem.PictureItem && it.checked }.map { (it as DataItem.PictureItem).picture.picId }
        }

        fun replaceAll(picsList: List<Picture>) {
            pictures = picsList
            val groupedList = pictures.groupBy { p -> p.creationDay }

            // Create list items
            val selected = getSelectedPictures()

            val newItems = mutableListOf<DataItem>()

            if (fragment.showAlbums)
                newItems.add(DataItem.MainHeader())

            for(entry in groupedList.entries){
                newItems.add(DataItem.Header(entry.key))
                entry.value.forEach { v ->
                    val item = DataItem.PictureItem(v)
                    item.checked = selected.contains(v.picId)
                    newItems.add(item)
                }
            }

            // Update list
            fragment.activity?.runOnUiThread {
                items = newItems
                sections = items.filterIsInstance<DataItem.Header>().map { it.creationDay }
                notifyDataSetChanged()

                val hasItems = items.isNotEmpty()
                fragment.picturesView.visibility = if(hasItems) View.VISIBLE else View.GONE
                fragment.noImageView.visibility = if(hasItems) View.GONE else View.VISIBLE
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        private fun setItemChecked(index: Int, checked: Boolean) {

            (items[index] as DataItem.PictureItem).checked = checked
            if(checked) {
                if(!selecting) {
                    selecting = true
                    selectionListener?.onSelectionEnabled()
                    notifyDataSetChanged()
                }
                notifyItemChanged(index)
                fragment.actionMode?.title = getSelectedPictures().size.toString()
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        fun exitSelectionMode() {
            selecting = false
            items.filterIsInstance<DataItem.PictureItem>().forEach { it.checked = false }
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun selectAll() {
            selecting = true
            items.filterIsInstance<DataItem.PictureItem>().forEach { it.checked = true }
            fragment.actionMode?.title = getSelectedPictures().size.toString()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                ITEM_VIEW_TYPE_MAIN_HEADER -> {
                    val view: View =  LayoutInflater.from(fragment.requireContext()).inflate(R.layout.explorer_header, parent, false)
                    CategoriesHeaderHolder(view)
                }
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
                is CategoriesHeaderHolder -> holder.onViewBinded(fragment)
                is PicViewHolder -> {
                    val p = items[p0] as DataItem.PictureItem
                    Picasso.with(fragment.requireContext()).load(p.picture.thumbnailUrl).into(holder.icon)

                    holder.checkBox.visibility = if(selecting) View.VISIBLE else View.INVISIBLE
                    holder.checkBox.isChecked = p.checked
                    holder.tile.radius = if(p.checked) 20.0f else 0.0f
                    holder.wideButton.visibility = if(selecting) View.VISIBLE else View.INVISIBLE
                    (holder.itemView.layoutParams as GridLayoutManager.LayoutParams).rightMargin = 5
                    (holder.itemView.layoutParams as GridLayoutManager.LayoutParams).topMargin = 5

                    val margin = if(p.checked) 40 else 0
                    val tileLayoutParams = holder.tile.layoutParams as RelativeLayout.LayoutParams
                    tileLayoutParams.setMargins(margin, margin, margin, margin)
                    holder.tile.layoutParams = tileLayoutParams

                    holder.checkBox.setOnClickListener {
                        setItemChecked(p0, !p.checked)
                    }

                    holder.wideButton.setOnClickListener {
                        showImageFullscreen(p0)
                    }

                    holder.icon.setOnClickListener {
                        if(selecting) {
                            setItemChecked(p0, !p.checked)
                        } else {
                            showImageFullscreen(p0)
                        }
                    }

                    holder.icon.setOnLongClickListener {
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
                is DataItem.MainHeader -> ITEM_VIEW_TYPE_MAIN_HEADER
                is DataItem.Header -> ITEM_VIEW_TYPE_HEADER
                is DataItem.PictureItem -> ITEM_VIEW_TYPE_ITEM
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

    class CategoriesHeaderHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var albumTitleLayout: LinearLayout = itemView.findViewById(R.id.album_title_layout)
        private var albumTitleEditLayout: LinearLayout = itemView.findViewById(R.id.album_title_edit_layout)
        private var albumPathLayout: LinearLayout = itemView.findViewById(R.id.album_path_layout)
        private var albumTitle: TextView = itemView.findViewById(R.id.album_name)
        private var albumTitleEdit: EditText = itemView.findViewById(R.id.album_name_edit)
        private var albumEditButton: ImageButton = itemView.findViewById(R.id.album_edit_button)
        private var albumEditConfirmButton: ImageButton = itemView.findViewById(R.id.album_edit_confirm_button)

        private var categoriesView: RecyclerView = itemView.findViewById(R.id.categories_grid_view)
        private val addImagesButton: Button = itemView.findViewById(R.id.add_image_button)
        private val addCategoryButton: Button = itemView.findViewById(R.id.add_category_button)

        fun onViewBinded(fragment: ImageListFragment) {
            val activity = fragment.requireActivity()
            val categoriesAdapter = CategoryListAdapter(fragment)
            categoriesView.layoutManager = LinearLayoutManager(fragment.requireContext(), LinearLayoutManager.HORIZONTAL, false)
            categoriesView.adapter  = categoriesAdapter

            categoriesAdapter.selectionListener = object : CategoryListAdapter.SelectionListener {

                var actionModeMenu : Menu? = null

                override fun onSelectionEnabled() {
                    fragment.requireActivity().startActionMode(object: ActionMode.Callback {

                        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                            val menuInflater: MenuInflater? = mode?.menuInflater
                            menuInflater?.inflate(R.menu.explorer_category_menu, menu)
                            categoriesAdapter.selecting = true
                            return true
                        }

                        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                            var out = true
                            when (item?.itemId) {
                                R.id.action_move -> (fragment.activity as MainActivity).moveCategories(categoriesAdapter.getSelectedCategories())
                                R.id.action_delete -> (fragment.activity as MainActivity).deleteCategories(categoriesAdapter.getSelectedCategories())
                                else -> out = false
                            }
                            if(out) mode?.finish()
                            return out
                        }

                        override fun onDestroyActionMode(p0: ActionMode?) {
                            categoriesAdapter.exitSelectionMode()
                            categoriesView.invalidate()
                        }

                        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                            actionModeMenu = menu
                            return true
                        }
                    })
                }

                override fun onSelectionChanged() {}
            }

            addImagesButton.setOnClickListener { (activity as MainActivity).addImages(0) }
            addCategoryButton.setOnClickListener { (activity as MainActivity).addCategory(0) }

            albumEditButton.setOnClickListener {
                setAlbumTitleEditMode(fragment, true)
            }

            albumEditConfirmButton.setOnClickListener {
                fragment.imageListVM.getCategory().value?.category?.catId?.let {
                    (activity as MainActivity).setCategoryName(it, albumTitleEdit.text.toString())
                }
                setAlbumTitleEditMode(fragment, false)
            }

            fragment.imageListVM.getCategory().observe(fragment.viewLifecycleOwner, Observer { cat ->
                cat?.let {
                    categoriesAdapter.refresh(cat.children)
                    updateViews(fragment, cat)
                    setAlbumTitleEditMode(fragment, false)
                    categoriesView.visibility = if (cat.children.isNotEmpty()) View.VISIBLE else View.GONE
                }
            })
        }

        private fun updateViews(fragment: ImageListFragment, cat: CategoryWithChildren) {

            albumTitle.text = cat.category.name
            albumTitleEdit.setText(cat.category.name)

            albumPathLayout.removeAllViews()
            val parents = PiwigoData.getCategoryParentsTree(cat.category.catId).reversed()
            for(i in parents.indices) {
                val p = parents[i]
                val isLast = i == parents.size - 1

                val textView = TextView(fragment.requireContext())
                textView.setTextColor(ContextCompat.getColor(fragment.requireContext(), R.color.white))
                textView.textSize = 15.0f
                var txt = PiwigoData.getCategoryFromId(p)?.name
                if(!isLast) {
                    textView.ellipsize = TextUtils.TruncateAt.END
                    txt += " > "
                }
                textView.text = txt
                textView.maxLines = 1
                textView.setOnClickListener { fragment.setCategory(p) }
                albumPathLayout.addView(textView)
            }

            albumEditButton.visibility = if(cat.category.catId != 0) View.VISIBLE else View.GONE
        }

        private fun setAlbumTitleEditMode(fragment: ImageListFragment, editing: Boolean) {
            val imm = fragment.requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if(editing) {
                albumTitleLayout.visibility = View.GONE
                albumTitleEditLayout.visibility = View.VISIBLE
                albumTitleEdit.requestFocus()
                albumTitleEdit.setSelection(albumTitleEdit.length())
                imm.showSoftInput(albumTitleEdit, InputMethodManager.SHOW_IMPLICIT)
            } else {
                albumTitleLayout.visibility = View.VISIBLE
                albumTitleEditLayout.visibility = View.GONE
                albumTitleEdit.clearFocus()
                imm.hideSoftInputFromWindow(albumTitleEdit.windowToken, 0)
            }
        }
    }
}