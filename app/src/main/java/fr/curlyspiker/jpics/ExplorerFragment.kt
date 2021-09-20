package fr.curlyspiker.jpics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import java.io.File
import java.io.FileOutputStream


class ExplorerFragment : Fragment() {

    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var categoriesRecycleViewAdapter: CategoryListAdapter

    private lateinit var picturesGridView: GridView
    private lateinit var picturesGidViewAdapter: PicturesListAdapter

    private lateinit var albumPathLayout: LinearLayout

    private val onBackPressHandler = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            changeCategory(categoriesRecycleViewAdapter.currentCategory.getParent())
        }
    }

    val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) { downloadImagesPermissionGranted() }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_explorer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        albumPathLayout = view.findViewById(R.id.album_path_layout)

        categoriesRecyclerView = view.findViewById(R.id.categories_grid_view)
        picturesGridView = view.findViewById(R.id.pictures_grid_view)

        categoriesRecycleViewAdapter = CategoryListAdapter(this)
        categoriesRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        categoriesRecyclerView.adapter  = categoriesRecycleViewAdapter

        picturesGidViewAdapter = PicturesListAdapter(this)
        picturesGridView.adapter  = picturesGidViewAdapter
        picturesGridView.choiceMode = GridView.CHOICE_MODE_MULTIPLE_MODAL
        picturesGridView.setMultiChoiceModeListener(object: AbsListView.MultiChoiceModeListener {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                val menuInflater: MenuInflater? = mode?.menuInflater
                menuInflater?.inflate(R.menu.explorer_menu, menu)
                picturesGidViewAdapter.selecting = true

                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, p1: Menu?): Boolean {
                mode?.customView?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
                return true
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                return when (item?.itemId) {
                    R.id.action_download -> {
                        downloadSelectedImages()
                        mode?.finish() // Action picked, so close the CAB
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(p0: ActionMode?) {
                picturesGidViewAdapter.selecting = false
            }

            override fun onItemCheckedStateChanged(
                p0: ActionMode?,
                p1: Int,
                p2: Long,
                p3: Boolean
            ) {

            }

        })

        checkStatus()
        login("joss", "Cgyn76&cgyn76")

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressHandler)
        onBackPressHandler.isEnabled = false
    }

    fun downloadSelectedImages() {
        if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            downloadImagesPermissionGranted()
        }
        else {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun downloadImagesPermissionGranted() {
        val ids = picturesGridView.checkedItemPositions
        for(i in 0 until ids.size()) {
            if(ids.valueAt(i)) {

                val pic = picturesGidViewAdapter.pictures[i]

                val target = object : com.squareup.picasso.Target {

                    override fun onBitmapLoaded(bitmap: Bitmap, arg1: LoadedFrom?) {
                        try {
                            var file: File?
                            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path + "/JPics")
                            if(!folder.exists()) {
                                folder.mkdirs()
                            }
                            file = File(folder.path + File.separator + pic.name + ".jpg")
                            file.createNewFile()
                            val ostream = FileOutputStream(file)
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, ostream)
                            ostream.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onBitmapFailed(errorDrawable: Drawable?) {
                        Log.d("TAG", "Error during download !")
                    }
                    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                }
                Picasso.with(requireContext()).load(pic.fullResUrl).into(target)
            }
        }
    }

    fun changeCategory(c : Category?) {
        c?.let {
            //PiwigoServerHelper.cancelAllOngoing()

            categoriesRecycleViewAdapter.currentCategory = c
            picturesGidViewAdapter.currentCategory = c

            onBackPressHandler.isEnabled = c.id != 0

            categoriesRecyclerView.visibility = if(c.getChildren().isNotEmpty()) View.VISIBLE else View.INVISIBLE

            albumPathLayout.removeAllViews()
            val parents = c.getHierarchy()
            for(p in parents) {
                val textView = TextView(requireContext())
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                textView.textSize = 20.0f
                textView.text = "${p.name} > "
                textView.ellipsize = TextUtils.TruncateAt.END
                textView.maxLines = 1
                textView.setOnClickListener {
                    changeCategory(p)
                }
                albumPathLayout.addView(textView)
            }
        }
    }


    private fun login(username: String, password: String) {

        Log.d("JP", "Trying to connect as $username...")
        val params: MutableMap<String, String> = HashMap()
        params["method"] = "pwg.session.login"
        params["username"] = username
        params["password"] = password

        PiwigoServerHelper.volleyPost(params) { rsp ->
            if (rsp.optBoolean("result", false)) {
                Log.d("JP", "Login successful !")
                checkStatus()
                getCategories()
            }
            else {
                Log.d("JP", "Connection failed: ${rsp.optString("message", "Unknown error")}")
            }
        }
    }

    private fun checkStatus() {
        PiwigoServerHelper.volleyGet("pwg.session.getStatus") { rsp ->
            val status = rsp.getJSONObject("result")
            Log.d("JP", "Connected user: ${status.optString("username", "unknown")}")
        }
    }

    private fun getCategories() {
        PiwigoServerHelper.volleyGet("pwg.categories.getList&recursive=true") { rsp ->
            if (rsp.optString("stat") == "ok") {
                val result = rsp.getJSONObject("result")
                val categories = result.optJSONArray("categories")
                categories?.let {

                    // Add categories to CategoriesManager
                    for(i in 0 until it.length()) {
                        Log.d("JP", "Found category: ${it.optJSONObject(i).optString("name")}")
                        CategoriesManager.addCategory(Category.fromJson(it.optJSONObject(i)))
                    }
                }

                changeCategory(CategoriesManager.fromID(0))
            }
        }
    }

    class CategoryListAdapter(private val fragment: ExplorerFragment) : RecyclerView.Adapter<CategoryListAdapter.ViewHolder>() {

        private var mContext = fragment.requireContext()
        var inflater: LayoutInflater = LayoutInflater.from(fragment.requireContext())
        private var categories: List<Category> = listOf()

        var currentCategory: Category = CategoriesManager.fromID(0)!!
            set(value) {
                field = value
                refreshCategories()
            }

        private fun refreshCategories() {
            categories = currentCategory.getChildren()
            notifyDataSetChanged()
        }

        class ViewHolder (view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.cateogry_tile_image)
            val title: TextView = view.findViewById(R.id.cateogry_tile_title)
            val elementsLabel: TextView = view.findViewById(R.id.cateogry_tile_number_of_elements)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemView = inflater.inflate(R.layout.category_tile, parent, false)
            return ViewHolder(itemView)
        }

        override fun onBindViewHolder(vh: ViewHolder, position: Int) {
            val category = categories[position]

            vh.title.text = category.name
            val nSubAlbums = category.getChildren().size
            vh.elementsLabel.text = if (nSubAlbums > 0) "$nSubAlbums sub-albums" else ""

            if (category.thumbnailUrl.isNotEmpty()) {
                Picasso.with(mContext).load(category.thumbnailUrl).into(vh.icon)
            }

            vh.icon.setOnClickListener {
                fragment.changeCategory(category)
            }
        }

        override fun getItemCount(): Int {
            return categories.size
        }
    }

    class PicturesListAdapter(private val fragment: ExplorerFragment) : BaseAdapter() {

        private val mContext = fragment.requireContext()

        private var inflater: LayoutInflater = LayoutInflater.from(fragment.requireContext())

        private var selectedItems: MutableList<Int> = mutableListOf()

        var pictures : MutableList<Picture> = mutableListOf()

        var selecting: Boolean = false
            set(value) {
                field=value
                notifyDataSetChanged()
            }

        var currentCategory: Category = CategoriesManager.fromID(0)!!
            set(value) {
                field = value
                refreshPictures()
            }

        private fun refreshPictures() {

            pictures.clear()
            notifyDataSetChanged()

            val params = "&cat_id=${currentCategory.id}&per_page=${currentCategory.nPictures}"
            PiwigoServerHelper.volleyGet("pwg.categories.getImages$params") { rsp ->
                if(rsp.optString("stat") == "ok") {
                    val result = rsp.optJSONObject("result")
                    val images = result?.optJSONArray("images")

                    images?.let {
                        for (i in 0 until it.length()) {
                            pictures.add(Picture.fromJson(it.optJSONObject(i)))
                        }
                        notifyDataSetChanged()
                    }
                }
            }
        }

        override fun getCount(): Int {
            return pictures.size
        }

        override fun getItem(p0: Int): Any {
            return pictures[p0]
        }

        override fun getItemId(p0: Int): Long {
            return (getItem(p0) as Picture).id.toLong()
        }

        override fun getView(p0: Int, p1: View?, parent: ViewGroup?): View {

            val v: View = p1 ?: inflater.inflate(R.layout.image_tile, null) // inflate the layout
            if(p1 == null) {
                val icon: ImageView = v.findViewById(R.id.image_tile_image) as ImageView
                val checkbox: CheckBox = v.findViewById(R.id.image_tile_checkbox) as CheckBox
                val tile: CheckableRelativeLayout = v.findViewById(R.id.tile)
                v.tag = ViewHolder(icon, checkbox, tile)
            }

            val gridView = parent as GridView

            val vh = v.tag as ViewHolder
            val icon: ImageView = vh.icon
            val checkbox : CheckBox = vh.checkbox

            val picture = getItem(p0) as Picture
            Picasso.with(mContext).load(picture.thumbnailUrl).into(vh.icon)

            checkbox.visibility = if(selecting) View.VISIBLE else View.INVISIBLE
            checkbox.isChecked = gridView.isItemChecked(p0)

            icon.setOnClickListener {
                val intent = Intent(mContext,ImageViewerActivity::class.java)
                intent.putExtra("cat_id", currentCategory.id)
                intent.putExtra("img_index", p0)
                startActivity(mContext, intent, null)
            }

            icon.setOnLongClickListener {
                checkbox.isChecked = true
                true
            }

            checkbox.setOnCheckedChangeListener { compoundButton, b ->
                gridView.setItemChecked(p0, b)
            }

            return v
        }

        class ViewHolder (val icon: ImageView, val checkbox: CheckBox, val tile: CheckableRelativeLayout)
    }


    class GridViewItem : androidx.appcompat.widget.AppCompatImageView {
        constructor(context: Context) : super(context)
        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
        constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

        public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec) // This is the key that will make the height equivalent to its width
        }
    }

    class GridViewItemHorizontal : androidx.appcompat.widget.AppCompatImageView {
        constructor(context: Context) : super(context)
        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
        constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

        public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(heightMeasureSpec, heightMeasureSpec) // This is the key that will make the height equivalent to its width
        }
    }

    class CheckableRelativeLayout(context: Context?, attrs: AttributeSet?) : RelativeLayout(context!!, attrs), Checkable {

        private var mChecked = false

        override fun setChecked(checked: Boolean) {
            mChecked = checked
            refreshDrawableState()
        }

        override fun isChecked(): Boolean {
            return mChecked
        }

        override fun toggle() {
            isChecked = !mChecked
        }

        // MAOR CODE
        override fun onCreateDrawableState(extraSpace: Int): IntArray? {

            val drawableState = super.onCreateDrawableState(extraSpace + 1)
            if (isChecked) {
                mergeDrawableStates(drawableState, intArrayOf(android.R.attr.state_checked))
            }
            return drawableState
        }
    }

}