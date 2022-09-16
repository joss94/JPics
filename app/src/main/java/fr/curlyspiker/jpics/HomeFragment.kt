package fr.curlyspiker.jpics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.google.android.material.bottomnavigation.BottomNavigationView


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

    private val albumsFragment = ExplorerFragment(0)
    private val searchFragment = SearchFragment()
    private val allFragment = AllImagesFragment()
    private var activeFragment: Fragment = albumsFragment

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        if (!allFragment.isAdded) {
            val transaction = childFragmentManager.beginTransaction()
            transaction.add(R.id.fragment_container, allFragment, "all_fragment")
            transaction.commit()
        }

        if (!searchFragment.isAdded) {
            val transaction = childFragmentManager.beginTransaction()
            transaction.add(R.id.fragment_container, searchFragment, "search_fragment")
            transaction.commit()
        }

        if (!albumsFragment.isAdded) {
            val transaction = childFragmentManager.beginTransaction()
            transaction.add(R.id.fragment_container, albumsFragment, "explorer_fragment")
            transaction.commit()
        }

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

        showAlbumsTab()
    }

    private fun showAllImagesTab() {
        childFragmentManager.beginTransaction()
            .hide(searchFragment)
            .hide(albumsFragment)
            .show(allFragment).commit()
        activeFragment = allFragment
    }

    private fun showAlbumsTab() {
        childFragmentManager.beginTransaction()
            .hide(allFragment)
            .hide(searchFragment)
            .show(albumsFragment).commit()
        activeFragment = albumsFragment
    }

    private fun showSearchTab() {
        childFragmentManager.beginTransaction()
            .hide(allFragment)
            .hide(albumsFragment)
            .show(searchFragment).commit()
        activeFragment = searchFragment
    }
}