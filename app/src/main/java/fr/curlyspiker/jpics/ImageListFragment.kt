package fr.curlyspiker.jpics

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
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
import kotlin.collections.ArrayList


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

class ImageListFragment (startCat: Category? = CategoriesManager.fromID(0)) :
    Fragment(), CategoriesManagerListener, PiwigoSession.UploadImageListener {

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

    private var cat: Category? = null

    private var pics = mutableListOf<Int>()

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
                        val menuRes = if(cat?.id == CategoriesManager.getArchiveCat()?.id) R.menu.explorer_menu_archive else R.menu.explorer_menu
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
                                    R.id.action_add_to_cat -> addImagesToCat(selectedPics)
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

        CategoriesManager.addListener(this)

        onFilterChanged = {
            lifecycleScope.launch(Dispatchers.IO) {
                val filteredPics = getFilteredPics(filterQuery)
                val newPicsItems = mutableListOf<PicturesListAdapter.PictureItem>()
                filteredPics.forEach { id -> newPicsItems.add(PicturesListAdapter.PictureItem(id)) }

                activity?.runOnUiThread {
                    picturesAdapter?.replaceAll(newPicsItems)
                }
            }
        }
        onFilterChanged()
        onItemsChanged()

        onImagesReady(cat?.id)
        refreshPictures()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        CategoriesManager.removeListener(this)
    }

    fun onItemsChanged() {
        val hasItems = picturesAdapter?.itemCount?:0 > 0
        picturesView.visibility = if(hasItems) View.VISIBLE else View.GONE
        noImageView.visibility = if(hasItems) View.GONE else View.VISIBLE
    }

    fun setCategory(c: Category?) {
        cat = c
        onImagesReady(cat?.id)
    }

    private fun refreshPictures(callback : () -> Unit = {}) {
        InstantUploadManager.getInstance(requireContext()).checkForNewImages()
        activity?.runOnUiThread {
            refreshButton.isEnabled = false
            CategoriesManager.refreshPictures(cat?.id) {
                activity?.runOnUiThread {
                    refreshButton.isEnabled = true
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
            // Need to show the progress bar now because it takes quite some time to load the images from disk
            activity?.runOnUiThread {
                progressLayout.visibility = View.VISIBLE
                progressBar.progress = 0
                progressTitle.text = getString(R.string.read_from_disk)
            }

            val progressListener = this
            lifecycleScope.launch {
                val images = mutableListOf<PiwigoSession.ImageUploadData>()
                uris.forEach { uri ->
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(requireContext().contentResolver, uri)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                    }

                    var filename = ""
                    var date = Calendar.getInstance().time.time

                    val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
                    if(cursor != null) {
                        cursor.moveToFirst()

                        val dateIndex: Int = cursor.getColumnIndexOrThrow("last_modified")
                        date = cursor.getString(dateIndex).toLong()

                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        filename = cursor.getString(nameIndex)

                        cursor.close()
                    }


                    images.add(PiwigoSession.ImageUploadData(bitmap, filename, Date(date)))
                }

                PiwigoSession.addImages(images, c, progressListener)
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
            progressTitle.text = getString(R.string.process_images).format(progressInt)
        }
    }

    fun downloadImages(pictures: List<Int>) {

        fun downloadImagesPermissionGranted() {
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path + "/JPics"
            onStarted()
            var downloaded = 0
            pictures.forEach { pic ->
                CategoriesManager.pictures.getValue(pic).saveToLocal(requireContext(), path) {
                    downloaded += 1
                    onProgress(downloaded.toFloat() / pics.size)
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
                val excludeList = mutableListOf<Category>()
                cat?.let { excludeList.add(it) }
                selectCategory(excludeList) { c ->
                    PiwigoSession.movePictures(pictures, checkedItem == 0, c) {
                        refreshPictures()
                        CategoriesManager.refreshPictures(c.id)
                        activity?.runOnUiThread{ actionMode?.finish() }
                    }
                }
            }

        builder.create().show()
    }

    private fun removeImagesFromAlbum(pictures: List<Int>) {
        val removablePictures = pictures.filter { id -> CategoriesManager.getCategoriesOf(id).size > 1 }
        if(removablePictures.size != pictures.size) {
            Toast.makeText(requireContext(), "Some pictures could not be removed because they only belong to this album", Toast.LENGTH_LONG).show()
        }

        cat?.let { c ->
            PiwigoSession.removePicturesFromAlbum(removablePictures, c.id) {
                refreshPictures()
                activity?.runOnUiThread{ actionMode?.finish() }
            }
        }

    }

    private fun addImagesToCat(pictures: List<Int>) {
        selectCategory { c ->
            PiwigoSession.movePictures(pictures, true, c, this) {
                refreshPictures()
                activity?.runOnUiThread{ actionMode?.finish() }
            }
        }
    }

    private fun deleteImages(pictures: List<Int>) {
        AlertDialog.Builder(requireContext(), R.style.AlertStyle)
            .setTitle("Delete images")
            .setMessage("Are you sure you want to delete these images? You will be able to restore them from the archive")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ -> PiwigoSession.archivePictures(pictures, this) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteDefinitiveImages(pictures: List<Int>) {
        AlertDialog.Builder(requireContext(), R.style.AlertStyle)
            .setTitle("Delete images")
            .setMessage("Are you sure you want to delete these images? This will be definitive !")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ -> PiwigoSession.deletePictures(pictures, this) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun restoreImages(pictures: List<Int>) {
        AlertDialog.Builder(requireContext(), R.style.AlertStyle)
            .setTitle("Restore images")
            .setMessage("Are you sure you want to restore these images?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes") { _, _ -> PiwigoSession.restorePictures(pictures, this) }
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
            Picasso.with(requireContext()).load(CategoriesManager.pictures.getValue(p).fullResUrl).into(target)
        }
    }

    private fun editImagesCreationDate(pictures: List<Int>) {
        fun setPictureCreationDate(index: Int, creationDate: Long) {
            if(index >= pictures.size) {
                refreshPictures()
                onCompleted()
                return
            }
            onProgress(index.toFloat() / pictures.size)
            PiwigoSession.setPictureInfo(pictures[index], creationDate = creationDate)  {
                setPictureCreationDate(index + 1, creationDate)
            }
        }
        Utils.getDatetime(parentFragmentManager, CategoriesManager.pictures.getValue(pictures[0]).creationDate) {
            onStarted()
            setPictureCreationDate(0, it)
        }

        actionMode?.finish()
    }

    private fun selectCategory(excludeList : List<Category> = listOf(), callback: (cat: Category) -> Unit) {
        val dialog = CategoryPicker(requireContext())
        dialog.setOnCategorySelectedCallback(callback)
        dialog.excludeCategories(excludeList)
        dialog.show()
    }

    fun setFilter(query: String) {
        filterQuery = query
        onFilterChanged()
    }

    private fun getFilteredPics(query: String) : List<Int> {
        if(query.isEmpty()) { return pics }

        val queryLow = query.lowercase()
        val filteredTags = PiwigoSession.getAllTags().filter { t -> t.name.lowercase().contains(queryLow) }
        val filteredCats = CategoriesManager.categories.filter { c -> c.name.lowercase().contains(queryLow) }

        return pics.filter { id ->
            val pic = CategoriesManager.pictures.getValue(id)
            pic.name.lowercase().contains(queryLow) || filteredTags.intersect(pic.getTags()).isNotEmpty() ||
                CategoriesManager.getCategoriesOf(id, recursive = true).intersect(filteredCats).isNotEmpty()

        }
    }

    override fun onImagesReady(catId: Int?) {
        val currentCategory = cat
        if(catId == currentCategory?.id) {
            pics = CategoriesManager.getPictures(cat?.id).toMutableList()
            onFilterChanged()
        }
    }

    override fun onCategoriesReady() {}

    class PicturesListAdapter(private val fragment: ImageListFragment) :
        RecyclerView.Adapter<PicturesListAdapter.ViewHolder>(),
        SectionIndexer,
        PopupTextProvider {

        class PictureItem(val id: Int) {
            var checked = false
            fun picture() : Picture {
                return CategoriesManager.pictures.getValue(id)
            }
        }

        abstract class SelectionListener {
            abstract fun onSelectionEnabled()
        }

        var selectionListener: SelectionListener? = null

        private val mContext = fragment.requireContext()
        var selecting: Boolean = false

        private var sections: MutableList<Date> = mutableListOf()


        val picsComparator = Comparator<PictureItem> { p0, p1 -> p1.picture().creationDate.compareTo(p0.picture().creationDate)}
            .thenBy { p -> p.picture().name }
            .thenBy { p -> p.id }

        private var sortedPictures = SortedList(PictureItem::class.java, object : SortedList.Callback<PictureItem>() {

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
                return item1?.id == item2?.id
            }
        })

        private fun showImageFullscreen(position: Int) {
            CategoriesManager.currentlyDisplayedList.clear()
            for(i in 0 until sortedPictures.size()) {
                CategoriesManager.currentlyDisplayedList.add(sortedPictures[i].id)
            }

            val intent = Intent(mContext,ImageViewerActivity::class.java)
            intent.putExtra("img_index", position)
            ContextCompat.startActivity(mContext, intent, null)
        }

        fun getSelectedPictures() : List<Int> {
            val selectedPictures : MutableList<Int> = mutableListOf()
            for(i in 0 until sortedPictures.size()) {
                val p = sortedPictures[i]
                if(p.checked) { selectedPictures.add(p.id) }
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
                val date = sortedPictures[i].picture().creationDate
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
            sortedPictures[index].checked = checked
            notifyItemChanged(index)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun exitSelectionMode() {
            selecting = false
            for(i in 0 until sortedPictures.size()) {
                sortedPictures[i].checked = false
            }
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun selectAll() {
            selecting = true
            for(i in 0 until sortedPictures.size()) {
                sortedPictures[i].checked = true
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
                if(section >= sortedPictures[i].picture().creationDay) {
                    index = i
                    break
                }
            }
            return index
        }

        override fun getSectionForPosition(p0: Int): Int {
            val sections = sections
            val picDate = sortedPictures[p0].picture().creationDay
            return sections.indexOfFirst { s -> s <= picDate }
        }

        override fun getPopupText(position: Int): String {
            val simpleDate = SimpleDateFormat("dd MMMM yyyy", Locale.US)
            return simpleDate.format(sortedPictures[position].picture().creationDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view: View = LayoutInflater.from(fragment.requireContext()).inflate(R.layout.image_tile, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, p0: Int) {
            val p = sortedPictures[p0]
            Picasso.with(mContext).load(p.picture().thumbnailUrl).into(holder.icon)

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
            return sortedPictures[position].picture().id.toLong()
        }

        override fun getItemViewType(position: Int): Int {
            return position
        }
    }

}