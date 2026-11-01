package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;

import androidx.cardview.widget.CardView;

/** Legacy card with rounded child clipping, which CardView lacks before API 21. */
public class EchoCardView extends CardView {
    private final Path childClip = new Path();
    private final RectF childBounds = new RectF();

    public EchoCardView(Context context) {
        this(context, null);
    }

    public EchoCardView(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.cardview.R.attr.cardViewStyle);
    }

    public EchoCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Material cards had no default shadow or corner-overlap content insets.
        TypedArray a = getContext().obtainStyledAttributes(
                attrs, androidx.cardview.R.styleable.CardView, defStyleAttr, 0);
        setCardElevation(a.getDimension(
                androidx.cardview.R.styleable.CardView_cardElevation, 0f));
        setMaxCardElevation(Math.max(getCardElevation(), a.getDimension(
                androidx.cardview.R.styleable.CardView_cardMaxElevation, 0f)));
        setPreventCornerOverlap(a.getBoolean(
                androidx.cardview.R.styleable.CardView_cardPreventCornerOverlap, false));
        a.recycle();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Clip children only: keep CardView's background and compatibility shadow intact.
        float horizontalInset = Math.max(0, getPaddingLeft() - getContentPaddingLeft());
        float verticalInset = Math.max(0, getPaddingTop() - getContentPaddingTop());
        childBounds.set(horizontalInset, verticalInset,
                getWidth() - horizontalInset, getHeight() - verticalInset);
        if (childBounds.isEmpty()) {
            super.dispatchDraw(canvas);
            return;
        }
        float radius = Math.min(getRadius(), Math.min(
                childBounds.width(), childBounds.height()) / 2f);
        childClip.reset();
        childClip.addRoundRect(childBounds, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(childClip);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }
}
