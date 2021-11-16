package fr.curlyspiker.jpics

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import android.graphics.drawable.InsetDrawable

import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.*


class CategoryPicker(context: Context) : Dialog(context) {

    private var excludedCategories = mutableListOf<Category>()

    private lateinit var categoriesView: RecyclerView
    private lateinit var categoriesAdapter: SelectCategoryListAdapter
    private lateinit var categoryName: TextView
    private lateinit var backButton: ImageButton

    private var catSelectedCb: (c: Category) -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_category_picker)

        findViewById<Button>(R.id.cancel_button).setOnClickListener { dismiss() }
        findViewById<Button>(R.id.ok_button).setOnClickListener {
            catSelectedCb(categoriesAdapter.getSelectedCat())
            dismiss()
        }

        backButton = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            categoriesAdapter.goToParent()
        }

        categoryName = findViewById(R.id.category_title)

        categoriesView = findViewById(R.id.categories_list_view)
        categoriesAdapter = SelectCategoryListAdapter(this)
        categoriesView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        categoriesView.adapter  = categoriesAdapter

        categoriesAdapter.refresh()

        window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val back = ColorDrawable(Color.TRANSPARENT)
        val inset = InsetDrawable(back, 100)
        window?.setBackgroundDrawable(inset)
        window?.setGravity(Gravity.CENTER)
    }

    private fun onCategoryChanged() {
        categoryName.text = categoriesAdapter.getCurrentCat().name
        backButton.visibility = if(categoriesAdapter.getCurrentCat().getParent() == null) View.INVISIBLE else View.VISIBLE
    }

    fun setOnCategorySelectedCallback(cb: (c: Category) -> Unit) {
        catSelectedCb = cb
    }

    fun excludeCategories(cats: List<Category>) {
        excludedCategories = cats.toMutableList()
        if(this::categoriesAdapter.isInitialized){
            categoriesAdapter.refresh()
        }
    }

    class SelectCategoryListAdapter(private val picker: CategoryPicker) :
        RecyclerView.Adapter<SelectCategoryListAdapter.ViewHolder>(){

        private var categories = mutableListOf<Category>()
        private var currentCategory = CategoriesManager.fromID(0)!!
        private var selectedCategory = currentCategory

        fun getCurrentCat() : Category {
            return currentCategory
        }

        fun getSelectedCat() : Category {
            return selectedCategory
        }

        fun goToParent() {
            currentCategory = currentCategory.getParent() ?: currentCategory
            selectedCategory = currentCategory
            refresh()
        }

        fun refresh() {
            currentCategory = CategoriesManager.fromID(currentCategory.id) ?: CategoriesManager.fromID(0)!!
            categories = currentCategory.getChildren().toMutableList().filter { c ->
                picker.excludedCategories.firstOrNull{ other -> c.id == other.id} == null
            }.toMutableList()
            notifyDataSetChanged()
            picker.onCategoryChanged()
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
            val category = categories[position]

            vh.title.text = category.name
            val nSubAlbums = category.getChildren().size
            vh.elementsLabel.text = if (nSubAlbums > 0) "$nSubAlbums sub-albums" else ""

            if (category.getThumbnailUrl().isNotEmpty()) {
                Picasso.with(picker.context).load(category.getThumbnailUrl()).into(vh.icon)
            } else {
                vh.icon.setImageDrawable(AppCompatResources.getDrawable(picker.context, R.drawable.image_icon))
            }

            vh.itemView.setBackgroundColor(picker.context.getColor(if(selectedCategory.id == category.id) R.color.dark_blue else R.color.transparent))

            vh.mainLayout.setOnClickListener {
                selectedCategory = category
                refresh()
            }

            vh.openCatButton.visibility = if(category.getChildren().isNotEmpty()) View.VISIBLE else View.INVISIBLE
            vh.openCatButton.setOnClickListener {
                if(category.getChildren().isNotEmpty()) {
                    currentCategory = category
                    selectedCategory = category
                    refresh()
                }
            }
        }

        override fun getItemCount(): Int {
            return categories.size
        }
    }

}