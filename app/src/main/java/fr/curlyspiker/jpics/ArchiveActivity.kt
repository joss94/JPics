package fr.curlyspiker.jpics

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.view.WindowCompat

class ArchiveActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_archive)
        setSupportActionBar(findViewById(R.id.my_toolbar))

        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()

        val cat = CategoriesManager.getArchiveCat()
        val imagesListFragment = ImageListFragment(cat)
        transaction.replace(R.id.image_list_fragment, imagesListFragment)
        transaction.commitAllowingStateLoss()

        imagesListFragment.onImagesReady(cat?.id)
    }
}