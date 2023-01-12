package fr.curlyspiker.jpics

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CatPickerViewModel: ViewModel() {

    private var catId = 0
    private val currentCat = MutableLiveData<Category>()
    private var excludedCats = listOf<Int>()
    private var children = MutableLiveData<List<Int>>()

    fun getCurrentCat(): LiveData<Category> {
        return currentCat
    }

    fun getChildren(): LiveData<List<Int>> {
        return children
    }

    fun excludeCategories(cats: List<Int>) {
        excludedCats = cats
        loadChildren()
    }

    private fun loadChildren() {
        viewModelScope.launch(Dispatchers.IO) {
            val cat = PiwigoData.getCategoryFromId(catId)
            cat?.let {
                val catChildren = cat.getChildren().filter { id -> !excludedCats.contains(id) }.toMutableList()
                currentCat.postValue(it)
                children.postValue(catChildren)
            }
        }
    }

    fun setCurrentCat(catId: Int) {
        this.catId = catId
        loadChildren()
    }

    fun goToParent() {
        currentCat.value?.parentId?.let { setCurrentCat(it) }
    }
}

class CategoryPicker(private val startCat: Int = 0, private val excludedCats: List<Int>, private val catSelectedCb: (c: Int) -> Unit) : DialogFragment() {

    private val pickerVM: CatPickerViewModel by viewModels()

    private var categoriesAdapter: SelectCategoryListAdapter = SelectCategoryListAdapter(this)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_category_picker, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.cancel_button).setOnClickListener { dismiss() }
        view.findViewById<Button>(R.id.ok_button).setOnClickListener {
            catSelectedCb(categoriesAdapter.getSelectedCat())
            dismiss()
        }

        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        backButton.setOnClickListener {
            pickerVM.goToParent()
        }

        val categoryName = view.findViewById<TextView>(R.id.category_title)

        val categoriesView = view.findViewById<RecyclerView>(R.id.categories_list_view)
        categoriesView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        categoriesView.adapter  = categoriesAdapter

        dialog?.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val back = ColorDrawable(Color.TRANSPARENT)
        val inset = InsetDrawable(back, 100)
        dialog?.window?.setBackgroundDrawable(inset)
        dialog?.window?.setGravity(Gravity.CENTER)

        pickerVM.getCurrentCat().observe(viewLifecycleOwner, Observer { cat ->
            categoryName.text = cat?.name
            backButton.visibility = if (cat?.parentId != null) View.VISIBLE else View.INVISIBLE
            categoriesAdapter.setSelectedCat(cat.catId)
        })

        pickerVM.getChildren().observe(viewLifecycleOwner, Observer{ cats ->
            categoriesAdapter.refresh(cats)
        })

        pickerVM.setCurrentCat(startCat)
        pickerVM.excludeCategories(excludedCats)
    }

    fun excludeCategories(cats: List<Int>) {
        pickerVM.excludeCategories(cats)
    }

    class SelectCategoryListAdapter(private val picker: CategoryPicker) :
        RecyclerView.Adapter<SelectCategoryListAdapter.ViewHolder>(){

        private var catIDs = listOf<Int>()
        private var selectedCategory: Int = 0

        fun getSelectedCat() : Int {
            return selectedCategory
        }

        fun setSelectedCat(catId: Int) {
            selectedCategory = catId
        }

        fun refresh(cats: List<Int>) {
            catIDs = cats
            notifyDataSetChanged()
        }

        class ViewHolder (view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.cateogry_tile_image)
            val title: TextView = view.findViewById(R.id.cateogry_tile_title)
            val openCatButton: ImageButton = view.findViewById(R.id.open_cat_button)
            val elementsLabel: TextView = view.findViewById(R.id.cateogry_tile_number_of_elements)
            val mainLayout: LinearLayout = view.findViewById(R.id.main_layout)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(picker.context).inflate(R.layout.category_row, parent, false))
        }

        override fun onBindViewHolder(vh: ViewHolder, position: Int) {
            val catId = catIDs[position]
            val category = PiwigoData.getCategoryFromId(catId)
            category?.let {
                vh.title.text = category.name
                val nSubAlbums = category.getChildren().size
                vh.elementsLabel.text = if (nSubAlbums > 0) "$nSubAlbums sub-albums" else ""

                if (category.thumbnailUrl.isNotEmpty()) {
                    Glide.with(picker.requireContext()).load(category.thumbnailUrl).into(vh.icon)
                } else {
                    vh.icon.setImageDrawable(AppCompatResources.getDrawable(picker.requireContext(), R.drawable.image_icon))
                }
                vh.itemView.setBackgroundColor(picker.requireContext().getColor(if(selectedCategory == catId) R.color.dark_blue else R.color.transparent))

                vh.mainLayout.setOnClickListener {
                    selectedCategory = catId
                    notifyDataSetChanged()
                }

                vh.openCatButton.visibility = if(category.getChildren().isNotEmpty()) View.VISIBLE else View.INVISIBLE
                vh.openCatButton.setOnClickListener {
                    picker.pickerVM.setCurrentCat(catId)
                }
            }
        }

        override fun getItemCount(): Int {
            return catIDs.size
        }
    }
}