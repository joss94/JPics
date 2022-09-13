package fr.curlyspiker.jpics

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeViewModel(firstTab: Int) : ViewModel() {

    private val currentTab = MutableLiveData<Int>()

    init {
        currentTab.postValue(firstTab)
    }

    fun setTab(id: Int) {
        if(id != currentTab.value) {
            currentTab.postValue(id)
        }
    }

    fun getTab(): LiveData<Int> {
        return currentTab
    }
}

class HomeViewModelFactory(private val tab: Int) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(tab) as T
    }
}

class HomeFragment : Fragment() {

    private val homeVM: HomeViewModel by viewModels{ HomeViewModelFactory(R.id.albums) }
    private lateinit var bottomView: BottomNavigationView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bottomView = view.findViewById(R.id.bottom_nav)
        bottomView.setOnItemSelectedListener { item ->
            homeVM.setTab(item.itemId)
            true
        }

        homeVM.getTab().observe(viewLifecycleOwner, Observer {
            if(bottomView.selectedItemId != it) {
                bottomView.selectedItemId = it
            }
            when(it) {
                R.id.all_pictures -> showAllImagesTab()
                R.id.albums -> showAlbumsTab()
                R.id.search -> showSearchTab()
            }
        })
    }

    private fun showAllImagesTab() {
        val allImagesFragment = AllImagesFragment()
        val fragmentManager = activity?.supportFragmentManager
        val transaction = fragmentManager?.beginTransaction()
        transaction?.replace(R.id.fragment_container, allImagesFragment, "all_images")
        transaction?.commitAllowingStateLoss()
    }

    private fun showAlbumsTab() {
        val albumsFragment = ExplorerFragment(0)
        val fragmentManager = activity?.supportFragmentManager
        val transaction = fragmentManager?.beginTransaction()
        transaction?.replace(R.id.fragment_container, albumsFragment, "albums")
        transaction?.commitAllowingStateLoss()
    }

    private fun showSearchTab() {
        val searchFragment = SearchFragment()
        val fragmentManager = activity?.supportFragmentManager
        val transaction = fragmentManager?.beginTransaction()
        transaction?.replace(R.id.fragment_container, searchFragment, "search")
        transaction?.commitAllowingStateLoss()
    }
}