package fr.curlyspiker.jpics

import android.content.Context
import android.util.AttributeSet

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