package fr.curlyspiker.jpics

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doOnTextChanged


class SearchFragment : Fragment() {

    private lateinit var keywordsEdit: EditText
    private var imagesListFragment = ImageListFragment(null)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        keywordsEdit = view.findViewById(R.id.keywords_edit)

        val fragmentManager = requireActivity().supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.image_list_fragment, imagesListFragment)
        transaction.commitAllowingStateLoss()

        keywordsEdit.doOnTextChanged { text, _, _, _ -> imagesListFragment.setFilter(text.toString()) }
    }
}