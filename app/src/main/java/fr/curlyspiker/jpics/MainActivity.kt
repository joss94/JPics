package fr.curlyspiker.jpics

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var accountButton: ImageButton
    private lateinit var bottomView: BottomNavigationView

    private lateinit var albumsFragment: ExplorerFragment
    private lateinit var allImagesFragment: AllImagesFragment
    private lateinit var searchFragment: SearchFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.my_toolbar))

        bottomView = findViewById(R.id.bottom_nav)
        bottomView.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.all_pictures -> showAllImagesTab()
                R.id.albums -> showAlbumsTab()
                R.id.search -> showSearchTab()
            }
            true
        }

        accountButton = findViewById(R.id.account_button)
        accountButton.setOnClickListener {
            val intent = Intent(this, AccountActivity::class.java)
            ContextCompat.startActivity(this, intent, null)
        }

        if (savedInstanceState == null) {
            allImagesFragment = AllImagesFragment()
            searchFragment = SearchFragment()
            if(PiwigoSession.logged) {
                albumsFragment = ExplorerFragment(CategoriesManager.fromID(0))
                bottomView.selectedItemId = R.id.albums
            } else {
                PiwigoSession.login("joss", "Cgyn76&cgyn76") {
                    albumsFragment = ExplorerFragment(CategoriesManager.fromID(0))
                    bottomView.selectedItemId = R.id.albums
                }
            }
        } else {
            allImagesFragment = (this.supportFragmentManager.findFragmentByTag("all_images") as AllImagesFragment?) ?: AllImagesFragment()
            searchFragment = (this.supportFragmentManager.findFragmentByTag("search") as SearchFragment?) ?: SearchFragment()
            albumsFragment = (this.supportFragmentManager.findFragmentByTag("albums") as ExplorerFragment?) ?: ExplorerFragment(CategoriesManager.fromID(0))
        }

        CategoriesManager.refreshCategories { CategoriesManager.refreshAllPictures {} }
    }

    private fun showAllImagesTab() {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, allImagesFragment, "all_images")
        transaction.commitAllowingStateLoss()
    }

    private fun showAlbumsTab() {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, albumsFragment, "albums")
        transaction.commitAllowingStateLoss()
    }

    private fun showSearchTab() {
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, searchFragment, "search")
        transaction.commitAllowingStateLoss()
    }
}