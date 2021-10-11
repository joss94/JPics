package fr.curlyspiker.jpics

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.*
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.SortedList
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.squareup.picasso.Picasso
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupTextProvider
import java.text.SimpleDateFormat
import java.util.*


class SpacesItemDecoration(private val space: Int, private val span: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildLayoutPosition(view)
        val column = position % span
        outRect.left = if (column == 0) 0 else if (column == 1) space else space * 2
        outRect.right = if (column == 2) 0 else if(column == 1) space else space * 2
        outRect.top = if(position < span) 0 else if(parent.adapter?.itemCount ?:0 - position >= span) 2 * space else 0
        outRect.bottom = if(parent.adapter?.itemCount ?:0 - position >= span) space else if(position < span) 2 * space else 0
    }
}

class ImageListFragment (private val startCat: Category? = CategoriesManager.fromID(0)) :
    Fragment(), CategoriesManagerListener, PiwigoSession.UploadImageListener {

    private var actionMode : ActionMode? = null

    private lateinit var swipeContainer: SwipeRefreshLayout

    private lateinit var addButton: ImageButton

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

    var imgReq = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onImagesPicked(result.data)
        }
    }

    private var cat: Category? = null

    private var pics = mutableListOf<Picture>()

    private var filterQuery = ""
    private var onFilterChanged : () -> Unit = {}

    init {
        cat = startCat
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_image_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addButton = view.findViewById(R.id.add_image_button)

        progressLayout = view.findViewById(R.id.progress_layout)
        progressBar = view.findViewById(R.id.progress_bar)
        progressTitle = view.findViewById(R.id.progress_title)

        picturesView = view.findViewById(R.id.pictures_grid_view)
        picturesView.itemAnimator = null
        noImageView = view.findViewById(R.id.no_image_view)

        picturesAdapter = PicturesListAdapter(this)
        picturesView.addItemDecoration(SpacesItemDecoration(2, 3))
        picturesView.layoutManager = GridLayoutManager(requireContext(), 3)

        picturesView.adapter  = picturesAdapter

        val builder = FastScrollerBuilder(picturesView)
        builder.setPopupStyle { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                v.setBackgroundColor(context?.getColor(R.color.black)!!)
                v.textSize = 20.0f
                val params = v.layoutParams as ViewGroup.MarginLayoutParams
                params.setMargins(0, 0, 200, 0) //substitute parameters for left, top, right, bottom
                v.layoutParams = params
                v.setPadding(20, 20, 20, 20)
            }
        }
        builder.build()

        swipeContainer = view.findViewById(R.id.swipeContainer)
        swipeContainer.setOnRefreshListener { refreshPictures() }
        swipeContainer.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light);

        picturesAdapter?.selectionListener = object : PicturesListAdapter.SelectionListener() {
            override fun onSelectionEnabled() {
                actionMode = activity?.startActionMode(object: ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        val menuInflater: MenuInflater? = mode?.menuInflater
                        menuInflater?.inflate(R.menu.explorer_menu, menu)
                        picturesAdapter?.selecting = true
                        addButton.visibility = View.INVISIBLE
                        return true
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        when (item?.itemId) {
                            R.id.action_download -> downloadSelectedImages()
                            R.id.action_move -> moveSelectedImages()
                            R.id.action_delete -> deleteSelectedImages()
                            R.id.action_add_to_cat -> addSelectedImagesToCat()
                            R.id.action_remove_from_album -> removeSelectedImagesFromAlbum()
                        }
                        return true
                    }

                    override fun onDestroyActionMode(p0: ActionMode?) {
                        picturesAdapter?.exitSelectionMode()
                        picturesView.invalidate()
                        addButton.visibility = View.VISIBLE
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, p1: Menu?): Boolean { return true }
                })
            }
        }

        addButton.setOnClickListener { addImages() }

        CategoriesManager.addListener(this)

        onFilterChanged = {
            GlobalScope.launch {
                val filteredModelList = getFilteredPics(filterQuery)

                val newPicsItems = mutableListOf<PicturesListAdapter.PictureItem>()
                filteredModelList.forEach { p -> newPicsItems.add(PicturesListAdapter.PictureItem(p)) }

                activity?.runOnUiThread {
                    picturesAdapter?.replaceAll(newPicsItems)
                    picturesView.scrollToPosition(0)
                }
            }
        }
        onFilterChanged()
        onItemsChanged()

        onImagesReady(cat?.id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        CategoriesManager.removeListener(this)
    }

    fun onItemsChanged() {
        if (picturesAdapter?.itemCount?:0 <= 0) {
            picturesView.visibility = View.GONE
            noImageView.visibility = View.VISIBLE
        } else {
            picturesView.visibility = View.VISIBLE
            noImageView.visibility = View.GONE
        }
    }

    fun setCategory(c: Category?) {
        cat = c
        onImagesReady(cat?.id)
    }

    private fun refreshPictures(callback : () -> Unit = {}) {
        activity?.runOnUiThread {
            swipeContainer.isRefreshing = true
            CategoriesManager.refreshPictures(cat?.id) {
                activity?.runOnUiThread {
                    swipeContainer.isRefreshing = false
                }
                callback()
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
            c?.let{
                // Need to show the progress bar now because it takes quite some time to load the images from disk
                activity?.runOnUiThread {
                    progressLayout.visibility = View.VISIBLE
                    progressBar.progress = 0
                    progressTitle.text = "Reading images from disk"
                }

                val progressListener = this
                GlobalScope.launch {
                    val bitmaps = mutableListOf<Bitmap>()
                    val filenames = mutableListOf<String?>()
                    uris.forEach { uri ->
                        bitmaps.add(MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri))
                        filenames.add(null)
                    }

                    PiwigoSession.addImages(bitmaps, c, filenames, progressListener)
                }
            }
        }
    }

    override fun onStarted() {
        activity?.runOnUiThread {
            progressLayout.visibility = View.VISIBLE
            progressBar.progress = 0
            progressTitle.text = "Processing images (0%)"
        }
    }

    override fun onCompleted() {
        refreshPictures {
            activity?.runOnUiThread {
                progressLayout.visibility = View.GONE
                actionMode?.finish()
            }
        }
    }

    override fun onProgress(progress: Float) {
        activity?.runOnUiThread {
            val progressInt = (progress * 100).toInt()
            progressBar.progress = progressInt
            progressTitle.text = "Processing images ($progressInt%)"
        }
    }

    fun downloadSelectedImages() {

        fun downloadImagesPermissionGranted() {
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path + "/JPics"
            picturesAdapter?.getSelectedPictures()?.forEach { it.saveToLocal(requireContext(), path) }
            actionMode?.finish()
        }

        if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            downloadImagesPermissionGranted()
        }
        else {
            onPermissionsGranted = { downloadImagesPermissionGranted() }
            permissionReq.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun moveSelectedImages() {
        picturesAdapter?.let { adapter ->
            val selectedPictures = adapter.getSelectedPictures()
            selectCategory { c ->
                c?.let {
                    PiwigoSession.movePictures(selectedPictures, it) {
                        refreshPictures()
                        activity?.runOnUiThread{ actionMode?.finish() }
                    }
                }
            }
        }
    }

    private fun removeSelectedImagesFromAlbum() {
        picturesAdapter?.let { adapter ->
            val selectedPictures = adapter.getSelectedPictures()
            val catId = cat?.id
            catId?.let {
                PiwigoSession.removePicturesFromAlbum(selectedPictures, catId) {
                    refreshPictures()
                    activity?.runOnUiThread{ actionMode?.finish() }
                }
            }

        }
    }

    private fun addSelectedImagesToCat() {
        picturesAdapter?.let { adapter ->
            val selectedPictures = adapter.getSelectedPictures()
            selectCategory { c ->
                c?.let {
                    PiwigoSession.addPicturesToCategory(selectedPictures, it) {
                        refreshPictures()
                        activity?.runOnUiThread{ actionMode?.finish() }
                    }
                }
            }
        }
    }

    private fun deleteSelectedImages() {
        picturesAdapter?.let { adapter ->
            val selectedPictures = adapter.getSelectedPictures()
            AlertDialog.Builder(requireContext(), R.style.AlertStyle)
                .setTitle("Delete images")
                .setMessage("Are you sure you want to delete these images?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes") { dialog, whichButton ->
                    PiwigoSession.deletePictures(selectedPictures, this)
                }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun selectCategory(callback: (cat: Category?) -> Unit) {
        val builder = AlertDialog.Builder(requireContext(), R.style.AlertStyle)
        builder.setTitle("Select an album")

        val labels = mutableListOf<String>()
        val catIDs = mutableListOf<Int>()
        CategoriesManager.categories.forEach { c ->
            labels.add(c.name)
            catIDs.add(c.id)
        }

        builder.setSingleChoiceItems(labels.toTypedArray(), -1) { dialog: DialogInterface, i: Int ->
            dialog.dismiss()
            callback(CategoriesManager.fromID(catIDs[i]))
        }

        builder.setNeutralButton("Cancel") { dialog: DialogInterface, i: Int ->
            dialog.cancel()
            callback(null)
        }

        builder.create().show()
    }

    fun setFilter(query: String) {
        filterQuery = query
        onFilterChanged()
    }

    private fun getFilteredPics(query: String) : MutableList<Picture> {
        if(query.isEmpty()) {
            return pics
        }

        var queryLow = query.lowercase()
        return pics.filter { p ->

            var isInFilter = false

            p.getCategories(recursive = true).forEach { c ->
                if(c.name.lowercase().contains(queryLow)) {
                    isInFilter = true
                }
            }

            if(!isInFilter && p.name.lowercase().contains(queryLow)) {
                isInFilter = true
            }

            isInFilter
        }.toMutableList()
    }

    override fun onImagesReady(catId: Int?) {
        val currentCategory = cat
        if(currentCategory == null || catId == currentCategory.id || catId == null) {
            pics = CategoriesManager.getPictures(cat?.id).toMutableList()
            onFilterChanged()
        }
    }

    override fun onCategoriesReady() {}

    class PicturesListAdapter(private val fragment: ImageListFragment) :
        RecyclerView.Adapter<PicturesListAdapter.ViewHolder>(),
        SectionIndexer,
        PopupTextProvider {

        class PictureItem(val picture: Picture) {
            var checked = false
        }

        abstract class SelectionListener {
            abstract fun onSelectionEnabled()
        }

        var selectionListener: SelectionListener? = null

        private val mContext = fragment.requireContext()
        var selecting: Boolean = false

        var sections: MutableList<Date> = mutableListOf()

        val picsComparator = Comparator<PictureItem> { p0, p1 -> p1.picture.creationDate.compareTo(p0.picture.creationDate)}
            .thenBy { p -> p.picture.name }
            .thenBy { p -> p.picture.id }

        var sortedPictures = SortedList(PictureItem::class.java, object : SortedList.Callback<PictureItem>() {

            override fun onInserted(position: Int, count: Int) {
                notifyItemRangeInserted(position, count)
                fragment.onItemsChanged()
            }

            override fun onRemoved(position: Int, count: Int) {
                notifyItemRangeRemoved(position, count)
                fragment.onItemsChanged()
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                notifyItemMoved(fromPosition, toPosition)
                fragment.onItemsChanged()
            }

            override fun onChanged(position: Int, count: Int) {
                notifyItemRangeChanged(position, count)
                fragment.onItemsChanged()
            }

            override fun compare(o1: PictureItem?, o2: PictureItem?): Int {
                return picsComparator.compare(o1, o2)
            }

            override fun areContentsTheSame(oldItem: PictureItem?, newItem: PictureItem?): Boolean {
                return oldItem?.equals(newItem) ?: false
            }

            override fun areItemsTheSame(item1: PictureItem?, item2: PictureItem?): Boolean {
                return item1?.picture?.id == item2?.picture?.id
            }
        })

        private fun showImageFullscreen(position: Int) {
            CategoriesManager.currentlyDisplayedList = mutableListOf()
            for(i in 0 until sortedPictures.size()) {
                CategoriesManager.currentlyDisplayedList?.add(sortedPictures[i].picture)
            }

            val intent = Intent(mContext,ImageViewerActivity::class.java)
            intent.putExtra("img_index", position)
            ContextCompat.startActivity(mContext, intent, null)
        }

        fun getSelectedPictures() : List<Picture> {
            val selectedPictures : MutableList<Picture> = mutableListOf()
            for(i in 0 until sortedPictures.size()) {
                val p = sortedPictures[i]
                if(p.checked) { selectedPictures.add(p.picture) }
            }
            return selectedPictures
        }

        fun replaceAll(models: List<PictureItem?>) {
            sortedPictures.beginBatchedUpdates()
            for (i in sortedPictures.size() - 1 downTo 0) {
                val model = sortedPictures.get(i)
                if (!models.contains(model)) {
                    sortedPictures.remove(model)
                }
            }
            sortedPictures.addAll(models)
            updateSections()
            sortedPictures.endBatchedUpdates()
        }

        private fun updateSections() {
            sections.clear()
            for(i in 0 until sortedPictures.size()) {
                val date = sortedPictures[i].picture.creationDate
                if(!sections.contains(date)) {
                    sections.add(date)
                }
            }
        }

        private fun setItemChecked(index: Int, checked: Boolean) {
            if(checked) {
                if(!selecting) {
                    selecting = true
                    selectionListener?.onSelectionEnabled()
                }
            }
            notifyDataSetChanged()
            sortedPictures[index].checked = checked
        }

        fun exitSelectionMode() {
            selecting = false
            for(i in 0 until sortedPictures.size()) {
                sortedPictures[i].checked = false
            }
            notifyDataSetChanged()
        }

        // stores and recycles views as they are scrolled off screen
        class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var icon: ImageView = itemView.findViewById(R.id.image_tile_image)
            var checkBox: CheckBox = itemView.findViewById(R.id.image_tile_checkbox)
            var wideButton: ImageButton = itemView.findViewById(R.id.image_tile_wide)
            var tile: CardView = itemView.findViewById(R.id.cardview)
        }

        override fun getSections(): Array<Any> {
            return sections.toTypedArray()
        }

        override fun getPositionForSection(p0: Int): Int {
            val section = sections[p0]
            var index = -1
            for(i in 0 until sortedPictures.size()) {
                if(section >= sortedPictures[i].picture.creationDay) {
                    index = i
                    break
                }
            }
            return index
        }

        override fun getSectionForPosition(p0: Int): Int {
            val sections = sections
            val picDate = sortedPictures[p0].picture.creationDay
            return sections.indexOfFirst { s -> s <= picDate }
        }

        override fun getPopupText(position: Int): String {
            val simpleDate = SimpleDateFormat("dd MMMM yyyy")
            return simpleDate.format(sortedPictures[position].picture.creationDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view: View = LayoutInflater.from(fragment.requireContext()).inflate(R.layout.image_tile, null, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, p0: Int) {
            val p = sortedPictures[p0]
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
            //tile.layoutParams = tileLayoutParams

            wideButton.visibility = if(selecting) View.VISIBLE else View.INVISIBLE
            wideButton.setOnClickListener {
                showImageFullscreen(p0)
            }

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

        override fun getItemCount(): Int {
            return sortedPictures.size()
        }

        override fun getItemId(position: Int): Long {
            return sortedPictures[position].picture.id.toLong()
        }

        override fun getItemViewType(position: Int): Int {
            return position
        }
    }

}