package fr.curlyspiker.jpics

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ExplorerViewModel(private var catId: Int) : ViewModel() {

    private val refreshing = MutableLiveData<Boolean>()
    private val category = MutableLiveData<CategoryWithChildren>()
    private var loadJob: Job? = null

    init {
        loadCategory()
    }

    fun setCategory(id: Int) {
        catId = id
        loadCategory()
    }

    private fun loadCategory() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            DatabaseProvider.db.CategoryDao().loadOneByIdWithChildrenFlow(catId)?.collect {
                category.postValue(it)
            }
        }
    }

    fun getCategory(): LiveData<CategoryWithChildren> {
        return category
    }

    fun isRefreshing(): LiveData<Boolean> {
        return refreshing
    }

    fun refreshCategories() {
        refreshing.postValue(true)
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.refreshCategories() {
                refreshing.postValue(false)
            }
        }
    }

    fun setCategoryName(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.setCategoryName(catId, name)
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            PiwigoData.addCategory(name, catId)
        }
    }

    fun moveCategories(cats: List<Int>, dst: Int, listener: PiwigoData.ProgressListener) {
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.moveCategories(cats, dst, listener)
        }
    }

    fun deleteCategories(cats: List<Int>, listener: PiwigoData.ProgressListener) {
        viewModelScope.launch(Dispatchers.IO) {
            PiwigoData.deleteCategories(cats, listener)
        }
    }
}

class ExplorerViewModelFactory(private val catId: Int) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ExplorerViewModel(catId) as T
    }
}

class ExplorerFragment (startCat: Int? = null) :
    Fragment(), PiwigoData.ProgressListener {

    private val explorerVM: ExplorerViewModel by viewModels { ExplorerViewModelFactory(startCat ?: 0) }

    private lateinit var mContext : Context
    private lateinit var swipeContainer: SwipeRefreshLayout

    private lateinit var progressLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTitle: TextView

    private lateinit var categoriesView: RecyclerView
    private var categoriesAdapter: CategoryListAdapter? = null

    private lateinit var albumTitleLayout: LinearLayout
    private lateinit var albumTitleEditLayout: LinearLayout
    private lateinit var albumPathLayout: LinearLayout
    private lateinit var albumTitle: TextView
    private lateinit var albumTitleEdit: EditText
    private lateinit var albumEditButton: ImageButton
    private lateinit var albumEditConfirmButton: ImageButton

    private var imagesListFragment: ImageListFragment = ImageListFragment(startCat)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_explorer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mContext = requireContext()

        progressLayout = view.findViewById(R.id.progress_layout)
        progressBar = view.findViewById(R.id.progress_bar)
        progressTitle = view.findViewById(R.id.progress_title)

        swipeContainer = view.findViewById(R.id.swipeContainer)

        albumTitleLayout = view.findViewById(R.id.album_title_layout)
        albumTitleEditLayout = view.findViewById(R.id.album_title_edit_layout)
        albumPathLayout = view.findViewById(R.id.album_path_layout)
        albumTitle = view.findViewById(R.id.album_name)
        albumTitleEdit = view.findViewById(R.id.album_name_edit)
        albumEditButton = view.findViewById(R.id.album_edit_button)
        albumEditConfirmButton = view.findViewById(R.id.album_edit_confirm_button)

        albumEditButton.setOnClickListener {
            setAlbumTitleEditMode(true)
        }

        albumEditConfirmButton.setOnClickListener {
            explorerVM.setCategoryName(albumTitleEdit.text.toString())
            setAlbumTitleEditMode(false)
        }

        categoriesView = view.findViewById(R.id.categories_grid_view)
        categoriesAdapter = CategoryListAdapter(this)
        categoriesView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        categoriesView.adapter  = categoriesAdapter

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(albumTitleEditLayout.isVisible) {
                    setAlbumTitleEditMode(false)
                } else {
                    val parent = explorerVM.getCategory().value?.parent
                    if (parent != null) {
                        changeCategory(parent.catId)
                    } else {
                        activity?.moveTaskToBack(true)
                    }
                }
            }
        })

        val fragmentManager = requireActivity().supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.image_list_fragment, imagesListFragment)
        transaction.commitAllowingStateLoss()

        swipeContainer.setOnRefreshListener {
            swipeContainer.isRefreshing = true
            swipeContainer.visibility = View.VISIBLE
            explorerVM.refreshCategories()
        }

        categoriesAdapter?.selectionListener = object : CategoryListAdapter.SelectionListener {

            var actionModeMenu : Menu? = null

            override fun onSelectionEnabled() {
                requireActivity().startActionMode(object: ActionMode.Callback {

                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        val menuInflater: MenuInflater? = mode?.menuInflater
                        menuInflater?.inflate(R.menu.explorer_category_menu, menu)
                        categoriesAdapter?.selecting = true
                        return true
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        var out = true
                        when (item?.itemId) {
                            R.id.action_move -> moveSelectedCategories()
                            R.id.action_delete -> deleteSelectedCategories()
                            else -> out = false
                        }
                        if(out) mode?.finish()
                        return out
                    }

                    override fun onDestroyActionMode(p0: ActionMode?) {
                        categoriesAdapter?.exitSelectionMode()
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

        explorerVM.getCategory().observe(viewLifecycleOwner, Observer { cat ->
            categoriesAdapter?.refresh(cat.children)
            updateViews(cat)
            swipeContainer.visibility = if(swipeContainer.isRefreshing || cat?.children?.isNotEmpty() == true) View.VISIBLE else View.GONE
        })
        explorerVM.isRefreshing().observe(viewLifecycleOwner, Observer {
            activity?.runOnUiThread {
                swipeContainer.isRefreshing = it
            }
        })
    }

    private fun setAlbumTitleEditMode(editing: Boolean) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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

    private fun updateViews(cat: CategoryWithChildren) {

        albumTitle.text = cat.category.name
        albumTitleEdit.setText(cat.category.name)

        albumPathLayout.removeAllViews()
        val parents = cat.category.getHierarchy().reversed()
        for(i in parents.indices) {
            val p = parents[i]
            val isLast = i == parents.size - 1

            val textView = TextView(mContext)
            textView.setTextColor(ContextCompat.getColor(mContext, R.color.white))
            textView.textSize = 15.0f
            var txt = PiwigoData.getCategoryFromId(p)?.name
            if(!isLast) {
                textView.ellipsize = TextUtils.TruncateAt.END
                txt += " > "
            }
            textView.text = txt
            textView.maxLines = 1
            textView.setOnClickListener { changeCategory(p) }
            albumPathLayout.addView(textView)
        }

        albumEditButton.visibility = if(cat.category.catId != 0) View.VISIBLE else View.GONE

    }

    fun changeCategory(c : Int) {
        explorerVM.setCategory(c)
        imagesListFragment.setCategory(c)
        setAlbumTitleEditMode(false)
    }

    fun addCategory() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(requireContext(), R.style.AlertStyle)
        builder.setTitle("Create a new album")

        val input = EditText(requireContext())
        input.hint = "Name of new album"
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setTextColor(requireContext().getColor(R.color.white))
        input.setHintTextColor(requireContext().getColor(R.color.light_gray))
        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, _ ->
            val name = input.text.toString()
            explorerVM.addCategory(name)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    fun deleteSelectedCategories() {
        categoriesAdapter?.let { adapter ->
            val selectedCategories = adapter.getSelectedCategories()
            AlertDialog.Builder(requireContext(), R.style.AlertStyle)
                .setTitle("Delete categories")
                .setMessage("Are you sure you want to delete these categories?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Yes") { _, _ ->
                    explorerVM.deleteCategories(selectedCategories, this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun moveSelectedCategories() {
        categoriesAdapter?.let { adapter ->
            val selectedCategories = adapter.getSelectedCategories()
            val excludeList = selectedCategories.toMutableList()
            explorerVM.getCategory().value?.let {excludeList.add(it.category.catId)}
            selectCategory(excludeList) { c ->
                c?.let {
                    explorerVM.moveCategories(selectedCategories, c, this)
                }
            }
        }
    }

    private fun selectCategory(excludeList : List<Int> = listOf(), callback: (cat: Int?) -> Unit) {

        val dialog = CategoryPicker(requireContext())
        dialog.setOnCategorySelectedCallback(callback)
        dialog.excludeCategories(excludeList)
        dialog.show()
    }

    override fun onStarted() {
        activity?.runOnUiThread {
            progressLayout.visibility = View.VISIBLE
            progressBar.progress = 0
            progressTitle.text = getString(R.string.processing_categories).format(0)
        }
    }

    override fun onCompleted() {
        activity?.runOnUiThread {
            progressLayout.visibility = View.GONE
        }
    }

    override fun onProgress(progress: Float) {
        activity?.runOnUiThread {
            val progressInt = (progress * 100).toInt()
            progressBar.progress = progressInt
            progressTitle.text = getString(R.string.processing_categories).format(progressInt)
        }
    }

    class CategoryListAdapter(private val fragment: ExplorerFragment) :
        RecyclerView.Adapter<CategoryListAdapter.ViewHolder>(){

        private var categories = mutableListOf<CategoryItem>()

        var selecting: Boolean = false
        var selectionListener: SelectionListener? = null

        class CategoryItem(val cat: Category) {
            var checked = false
        }

        interface SelectionListener {
            fun onSelectionEnabled()
            fun onSelectionChanged()
        }

        fun refresh(cats: List<Category>) {
            categories = cats.map { CategoryItem(it) }.toMutableList()
            updateOnUIThread()
        }

        fun getSelectedCategories() : List<Int> {
            val out = mutableListOf<Int>()
            categories.filter { c -> c.checked }.forEach { out.add(it.cat.catId) }
            return out
        }

        fun exitSelectionMode() {
            selecting = false
            categories.forEach { c -> c.checked = false }
            updateOnUIThread()
        }

        private fun updateOnUIThread() {
            fragment.activity?.runOnUiThread {
                notifyDataSetChanged()
            }
        }

        private fun setItemChecked(index: Int, checked: Boolean) {
            categories[index].checked = checked
            if(checked) {
                if(!selecting) {
                    selecting = true
                    selectionListener?.onSelectionEnabled()
                }
            }
            updateOnUIThread()
            selectionListener?.onSelectionChanged()
        }

        class ViewHolder (view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.cateogry_tile_image)
            val title: TextView = view.findViewById(R.id.cateogry_tile_title)
            val elementsLabel: TextView = view.findViewById(R.id.cateogry_tile_number_of_elements)
            var checkBox: CheckBox = itemView.findViewById(R.id.image_tile_checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(fragment.requireContext()).inflate(R.layout.category_tile, parent, false))
        }

        override fun onBindViewHolder(vh: ViewHolder, position: Int) {
            if(position == categories.size) {
                vh.title.text = fragment.requireContext().getString(R.string.new_album)
                vh.elementsLabel.text = ""
                vh.icon.setImageDrawable(AppCompatResources.getDrawable(fragment.requireContext(), R.drawable.add))
                vh.icon.setOnClickListener { fragment.addCategory() }
            } else {
                val item = categories[position]
                val checkbox : CheckBox = vh.checkBox

                checkbox.visibility = if(selecting) View.VISIBLE else View.INVISIBLE
                checkbox.isChecked = item.checked
                checkbox.setOnClickListener { setItemChecked(position, !item.checked) }

                vh.title.text = item.cat.name
                val nSubAlbums = item.cat.getChildren().size
                vh.elementsLabel.text = if (nSubAlbums > 0) "$nSubAlbums sub-albums" else ""

                if (item.cat.thumbnailUrl.isNotEmpty()) {
                    Picasso.with(fragment.requireContext()).load(item.cat.thumbnailUrl).into(vh.icon)
                } else {
                    vh.icon.setImageDrawable(AppCompatResources.getDrawable(fragment.requireContext(), R.drawable.image_icon))
                }

                vh.icon.setOnClickListener {
                    if(!selecting) {
                        fragment.changeCategory(item.cat.catId)
                    } else {
                        setItemChecked(position, !item.checked)
                    }
                }

                vh.icon.setOnLongClickListener {
                    setItemChecked(position, true)
                    true
                }
            }
        }

        override fun getItemCount(): Int {
            return if(selecting) categories.size else categories.size + 1
        }
    }

    class GridViewItem : androidx.appcompat.widget.AppCompatImageView {

        private var isHorizontal = false

        constructor(context: Context) : super(context)
        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { initializeAttributes(attrs) }
        constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) { initializeAttributes(attrs) }

        private fun initializeAttributes(attrs: AttributeSet?) {
            context.theme.obtainStyledAttributes(attrs, R.styleable.GridViewItem, 0, 0).apply {
                try { isHorizontal = getInt(R.styleable.GridViewItem_listOrientation, 0) == 1 }
                finally { recycle() }
            }
        }

        public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val sz = if (isHorizontal) heightMeasureSpec else widthMeasureSpec
            super.onMeasure(sz, sz)
        }
    }
}