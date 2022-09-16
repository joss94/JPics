package fr.curlyspiker.jpics

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ExplorerViewModel(private var catId: Int) : ViewModel() {

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
}

class ExplorerViewModelFactory(private val catId: Int) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ExplorerViewModel(catId) as T
    }
}

class ExplorerFragment (startCat: Int? = null) : Fragment() {

    private val explorerVM: ExplorerViewModel by viewModels { ExplorerViewModelFactory(startCat ?: 0) }

    private lateinit var mContext : Context

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

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if(albumTitleEditLayout.isVisible) {
                setAlbumTitleEditMode(false)
            } else {
                val parent = explorerVM.getCategory().value?.category?.parentId ?: -1
                if (PiwigoData.getCategoryFromId(parent) != null) {
                    changeCategory(parent)
                } else {
                    activity?.moveTaskToBack(true)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_explorer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mContext = requireContext()

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
            explorerVM.getCategory().value?.category?.catId?.let {
                (activity as MainActivity).setCategoryName(it, albumTitleEdit.text.toString())
            }
            setAlbumTitleEditMode(false)
        }

        categoriesView = view.findViewById(R.id.categories_grid_view)
        categoriesAdapter = CategoryListAdapter(this)
        categoriesView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        categoriesView.adapter  = categoriesAdapter

        if (savedInstanceState == null) {
            val transaction = childFragmentManager.beginTransaction()
            transaction.replace(R.id.image_list_fragment, imagesListFragment)
            transaction.commitAllowingStateLoss()
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
                            R.id.action_move -> (activity as MainActivity).moveCategories(categoriesAdapter?.getSelectedCategories() ?: listOf())
                            R.id.action_delete -> (activity as MainActivity).deleteCategories(categoriesAdapter?.getSelectedCategories() ?: listOf())
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
            cat?.let {
                categoriesAdapter?.refresh(cat.children)
                updateViews(cat)
                categoriesView.visibility = if(cat.children.isNotEmpty()) View.VISIBLE else View.GONE
            }
        })

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    override fun onPause() {
        super.onPause()
        Log.d("Explorer", "On pause")

        backPressedCallback.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        Log.d("Explorer", "On resume")
        backPressedCallback.isEnabled = true
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
        val parents = PiwigoData.getCategoryParentsTree(cat.category.catId).reversed()
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
            categories = cats.sortedBy { c -> c.globalRank }.map { CategoryItem(it) }.toMutableList()
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

        override fun getItemCount(): Int {
            return categories.size
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