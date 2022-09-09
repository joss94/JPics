package fr.curlyspiker.jpics

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupTextProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import kotlin.collections.ArrayList

class ILFViewModel : ViewModel() {

    private var isArchive: Boolean = false
    private var catId: Int? = null
    private val imageList = MutableLiveData<List<Picture>>()

    fun setArchive(isArchive: Boolean) {
        this.isArchive = isArchive
    }

    fun setCategory(catId: Int?) {
        this.catId = catId
        loadPictures()
    }

    fun getPictures(): LiveData<List<Picture>> {
        return imageList
    }

    fun loadPictures() {
        val picsIds = mutableListOf<Int>()

        if (isArchive) {
            picsIds.addAll(DatabaseProvider.db.PictureDao().getArchivedIds())
        } else {
            val cat = catId
            if (cat != null) {
                picsIds.addAll(PiwigoData.getCategoryFromId(cat)?.getPictures() ?: listOf())
            }
            else {
                picsIds.addAll(DatabaseProvider.db.PictureDao().getAllIds())
            }
        }

        val newList = DatabaseProvider.db.PictureDao().loadManyById(picsIds).filterNotNull().sortedWith(compareByDescending<Picture>{ it.creationDate }.thenBy{ it.name })
        Log.d("ILF", "Loading done!")

        imageList.postValue(newList)
    }

    fun movePicturesToCategory(pics: List<Int>, cat: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.movePicsToCat(pics, cat)
            loadPictures()
        }
    }

    fun addPicturesToCategory(pics: List<Int>, cats: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.addPicsToCats(pics, cats)
        }
    }

    fun removePicturesFromCategory(pics: List<Int>, cat: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.removePicsFromCat(pics, cat)
            loadPictures()
        }
    }

    fun archivePictures(pics: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.archivePictures(pics, true)
            loadPictures()
        }
    }

    fun restorePictures(pics: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.restorePictures(pics)
            loadPictures()
        }
    }

    fun deletePictures(pics: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.deleteImages(pics)
            loadPictures()
        }
    }

    fun setImagesCreationDate(pics: List<Int>, creationDate: Date) {
        viewModelScope.launch(Dispatchers.IO) {
            pics.forEach { id ->
                PiwigoData.setPicCreationDate(id, creationDate = creationDate)
            }
            loadPictures()
        }
    }
}

class ImageListFragment (startCat: Int? = 0, val isArchive: Boolean = false)  :
    Fragment(), PiwigoDataListener, PiwigoData.ProgressListener {

    private val imageListVM: ILFViewModel by viewModels()

    private var actionMode : ActionMode? = null

    private lateinit var addButton: ImageButton
    private lateinit var refreshButton: ImageButton

    private lateinit var progressLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTitle: TextView

    private lateinit var picturesView: RecyclerView
    private var picturesAdapter: PicturesListAdapter? = null
    private lateinit var noImageView: ImageView

    private var onPermissionsGranted : () -> Unit = {}
    private val permissionReq = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted: Boolean ->
        if (granted) { onPermissionsGranted() }
    }

    private var imgReq = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onImagesPicked(result.data)
        }
    }

    private var catId: Int? = null

    init {
        catId = startCat
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

        addButton = view.findViewById(R.id.add_image_button)

        progressLayout = view.findViewById(R.id.progress_layout)
        progressBar = view.findViewById(R.id.progress_bar)
        progressTitle = view.findViewById(R.id.progress_title)

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

        refreshButton = view.findViewById(R.id.refresh_button)
        refreshButton.setOnClickListener { refreshPictures() }

        picturesAdapter?.selectionListener = object : PicturesListAdapter.SelectionListener() {
            override fun onSelectionEnabled() {
                actionMode = activity?.startActionMode(object: ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        val menuInflater: MenuInflater? = activity?.menuInflater
                        val menuRes = if(isArchive) R.menu.explorer_menu_archive else R.menu.explorer_menu
                        menuInflater?.inflate(menuRes, menu)
                        picturesAdapter?.selecting = true
                        addButton.visibility = View.INVISIBLE
                        refreshButton.visibility = View.INVISIBLE
                        return true
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        picturesAdapter?.let{
                            val selectedPics = it.getSelectedPictures()
                            if(selectedPics.isNotEmpty()) {
                                when (item?.itemId) {
                                    R.id.action_download -> downloadImages(selectedPics)
                                    R.id.action_share -> shareImages(selectedPics)
                                    R.id.action_select_all -> picturesAdapter?.selectAll()
                                    R.id.action_move -> moveImages(selectedPics)
                                    R.id.action_delete -> deleteImages(selectedPics)
                                    R.id.action_delete_definitive -> deleteDefinitiveImages(selectedPics)
                                    R.id.action_restore -> restoreImages(selectedPics)
                                    R.id.action_remove_from_album -> removeImagesFromAlbum(selectedPics)
                                    R.id.action_creation_date -> editImagesCreationDate(selectedPics)
                                    else -> {}
                                }
                            }
                        }
                        return true
                    }

                    override fun onDestroyActionMode(p0: ActionMode?) {
                        picturesAdapter?.exitSelectionMode()
                        picturesView.invalidate()
                        addButton.visibility = View.VISIBLE
                        refreshButton.visibility = View.VISIBLE
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, p1: Menu?): Boolean { return true }
                })
            }
        }

        addButton.setOnClickListener { addImages() }

        PiwigoData.addListener(this)

        imageListVM.setArchive(isArchive)
        imageListVM.setCategory(catId)
        imageListVM.getPictures().observe(viewLifecycleOwner) { pictures ->
            lifecycleScope.launch(Dispatchers.IO) {
                picturesAdapter?.replaceAll(pictures)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PiwigoData.removeListener(this)
    }

    fun onItemsChanged() {
        val hasItems = (picturesAdapter?.itemCount ?: 0) > 0
        picturesView.visibility = if(hasItems) View.VISIBLE else View.GONE
        noImageView.visibility = if(hasItems) View.GONE else View.VISIBLE
    }

    fun setCategory(c: Int?) {
        catId = c
        if(isAdded && context != null) {
            imageListVM.setCategory(c)
        }
    }

    private fun refreshPictures() {
        InstantUploadManager.getInstance(requireContext()).checkForNewImages()
        activity?.runOnUiThread {
            refreshButton.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                PiwigoData.refreshPictures(if(catId != null) listOf(catId!!) else null)
                activity?.runOnUiThread {
                    refreshButton.isEnabled = true
                }
            }
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

        selectCategory { c ->
            // Need to show the progress bar now because it takes quite some time to load the images from disk
            activity?.runOnUiThread {
                progressLayout.visibility = View.VISIBLE
                progressBar.progress = 0
                progressTitle.text = getString(R.string.read_from_disk)
            }

            val progressListener = this
            lifecycleScope.launch {
                PiwigoData.addImages(uris, requireContext().contentResolver, listOf(c), listener = progressListener)
            }
        }
    }

    override fun onStarted() {
        activity?.runOnUiThread {
            progressLayout.visibility = View.VISIBLE
            progressBar.progress = 0
            progressTitle.text = getString(R.string.process_images).format(0)
        }
    }

    override fun onCompleted() {
        activity?.runOnUiThread {
            progressLayout.visibility = View.GONE
            actionMode?.finish()
        }
    }

    override fun onProgress(progress: Float) {
        activity?.runOnUiThread {
            val progressInt = (progress * 100).toInt()
            progressBar.progress = progressInt
            progressTitle.text = getString(R.string.process_images).format(progressInt)
        }
    }

    fun downloadImages(pictures: List<Int>) {

        fun downloadImagesPermissionGranted() {
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path + "/JPics"
            onStarted()
            var downloaded = 0
            pictures.forEach { pic ->
                imageListVM.getPictures().value?.find { p -> p.picId == pic }?.saveToLocal(requireContext(), path) {
                    downloaded += 1
                    onProgress(downloaded.toFloat() / pictures.size)
                }
            }
            activity?.runOnUiThread {
                actionMode?.finish()
            }
            onCompleted()

        }

        if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            downloadImagesPermissionGranted()
        }
        else {
            onPermissionsGranted = { downloadImagesPermissionGranted() }
            permissionReq.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun moveImages(pictures: List<Int>) {

        var checkedItem = 0
        val labels = arrayOf("Add to other album", "Move to different location")
        val builder = AlertDialog.Builder(requireContext(), R.style.AlertStyle)
            .setSingleChoiceItems(labels, checkedItem) { _, i -> checkedItem = i }
            .setTitle("What do you want to do ?")
            .setCancelable(true)
            .setPositiveButton("OK") { _, _ ->
                val excludeList = mutableListOf<Int>()
                catId?.let { excludeList.add(it) }
                selectCategory(excludeList) { c ->
                    if (checkedItem == 0) {
                        if (checkedItem == 0) {
                            imageListVM.addPicturesToCategory(pictures, listOf(c))
                        } else {
                            imageListVM.movePicturesToCategory(pictures, c)
                        }
                        actionMode?.finish()
                    }
                }
            }

        builder.create().show()
    }

    private fun removeImagesFromAlbum(pictures: List<Int>) {
        catId?.let { c ->
            imageListVM.removePicturesFromCategory(pictures, c)
            actionMode?.finish()
        }
    }

    private fun deleteImages(pictures: List<Int>) {
        AlertDialog.Builder(requireContext(), R.style.AlertStyle)
            .setTitle("Delete images")
            .setMessage("Are you sure you want to delete these images? You will be able to restore them from the archive")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ ->
                imageListVM.archivePictures(pictures)
                actionMode?.finish()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteDefinitiveImages(pictures: List<Int>) {
        AlertDialog.Builder(requireContext(), R.style.AlertStyle)
            .setTitle("Delete images")
            .setMessage("Are you sure you want to delete these images? This will be definitive !")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ ->
                imageListVM.deletePictures(pictures)
                activity?.runOnUiThread { actionMode?.finish() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun restoreImages(pictures: List<Int>) {
        AlertDialog.Builder(requireContext(), R.style.AlertStyle)
            .setTitle("Restore images")
            .setMessage("Are you sure you want to restore these images?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ ->
                imageListVM.restorePictures(pictures)
                activity?.runOnUiThread { actionMode?.finish() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun shareImages(pictures: List<Int>) {

        val path = requireContext().cacheDir.absolutePath + File.separator + "/images"
        val listOfUris = ArrayList<Uri>()
        pictures.forEachIndexed { i, p ->
            val target = object : com.squareup.picasso.Target {
                override fun onBitmapLoaded(bitmap: Bitmap, arg1: Picasso.LoadedFrom?) {
                    try {
                        val folder = File(path)
                        if(!folder.exists()) { folder.mkdirs() }
                        val file = File(folder.path + File.separator + "image_$i.jpg")
                        file.createNewFile()
                        val stream = FileOutputStream(file)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                        stream.close()

                        val contentUri = FileProvider.getUriForFile(
                            requireContext(),
                            "fr.curlyspiker.jpics.fileprovider",
                            file
                        )
                        listOfUris.add(contentUri)
                        if(listOfUris.size == pictures.size) {
                            val shareIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND_MULTIPLE
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                type = "*/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, listOfUris)
                            }
                            try {
                                actionMode?.finish()
                                startActivity(Intent.createChooser(shareIntent, "Select sharing app"))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(activity, "No App Available", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        actionMode?.finish()
                        e.printStackTrace()
                    }
                }
                override fun onBitmapFailed(errorDrawable: Drawable?) {
                    actionMode?.finish()
                    Log.d("TAG", "Error during download !")
                }
                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
            }
            Picasso.with(requireContext()).load(imageListVM.getPictures().value?.find { pic -> pic.picId == p}?.largeResUrl).into(target)
        }
    }

    private fun editImagesCreationDate(pictures: List<Int>) {
        val date = Date()
        Utils.getDatetime(parentFragmentManager, date) {
            imageListVM.setImagesCreationDate(pictures, Date(it))
        }

        actionMode?.finish()
    }

    private fun selectCategory(excludeList : List<Int> = listOf(), callback: (cat: Int) -> Unit) {
        val dialog = CategoryPicker(requireContext())
        dialog.setOnCategorySelectedCallback(callback)
        dialog.excludeCategories(excludeList)
        dialog.show()
    }

    fun setFilter(query: String) {
        picturesAdapter?.applyFilter(query)
    }

    override fun onImagesReady(catId: Int?) {
        if(this.catId == null || this.catId == catId || catId == null) {
            imageListVM.loadPictures()
        }
    }

    override fun onCategoriesReady() {}

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
        private var filteredPictures = listOf<Picture>()
        private var filterQuery: String = ""

        fun isHeader(pos: Int) : Boolean {
            return items[pos] is DataItem.Header
        }

        private fun showImageFullscreen(position: Int) {
            PiwigoData.currentlyDisplayedList.clear()
            items.forEach { i ->
                if(i is DataItem.PictureItem) {
                    PiwigoData.currentlyDisplayedList.add(i.picture.picId)
                }
            }

            val intent = Intent(mContext,ImageViewerActivity::class.java)
            intent.putExtra("img_id", items[position].id.toInt())
            ContextCompat.startActivity(mContext, intent, null)
        }

        fun getSelectedPictures() : List<Int> {
            val selectedPictures = mutableListOf<Int>()
            items.filter { i -> i is DataItem.PictureItem && i.checked }.forEach { selectedPictures.add((it as DataItem.PictureItem).picture.picId) }
            return selectedPictures
        }

        fun replaceAll(picsList: List<Picture>) {
            pictures = picsList
            applyFilter(filterQuery)
        }

        fun applyFilter(query: String) {
            fragment.lifecycleScope.launch(Dispatchers.IO) {
                filterQuery = query
                filteredPictures = pictures
                Log.d("ILF", "0")

                if(filterQuery.isNotEmpty()) {
                    val queryLow = filterQuery.lowercase()
                    val filteredTags =
                        PiwigoData.getAllTags().filter { t -> t.name.lowercase().contains(queryLow) }
                            .map { t -> t.tagId }
                    val filteredCats = PiwigoData.getAllCategories()
                        .filter { c -> c.name.lowercase().contains(queryLow) }.map { c -> c.catId }

                    filteredPictures = pictures.filter { pic ->
                        pic.name.lowercase().contains(queryLow) || filteredTags.intersect(pic.getTags().toSet()).isNotEmpty() ||
                                pic.getCategories(recursive = true).intersect(filteredCats.toSet()).isNotEmpty()
                    }
                }

                val groupedList = filteredPictures.groupBy { p -> p.creationDay }

                Log.d("ILF", "1")
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

                Log.d("ILF", "3")
                // Update list
                fragment.activity?.runOnUiThread {
                    items = newItems
                    updateSections()
                    fragment.onItemsChanged()
                    notifyDataSetChanged()
                }
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
                    holder.label.text = SimpleDateFormat("EEE. d MMM. yyyy", Locale.US).format(headerItem.creationDay)
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