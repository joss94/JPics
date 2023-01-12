package fr.curlyspiker.jpics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CategoryListAdapter(private val fragment: ImageListFragment) :
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
            Glide.with(fragment.requireContext()).load(item.cat.thumbnailUrl).into(vh.icon)
        } else {
            vh.icon.setImageDrawable(AppCompatResources.getDrawable(fragment.requireContext(), R.drawable.image_icon))
        }

        vh.icon.setOnClickListener {
            if(!selecting) {
                //fragment.changeCategory(item.cat.catId)
                fragment.setCategory(item.cat.catId)
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