package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import dev.brahmkshatriya.echo.R;

/** Wrapping filter group, preserving single/multiple selection and required selection. */
public class EchoChipGroup extends ViewGroup {
    public interface OnCheckedStateChangeListener { void onCheckedChanged(EchoChipGroup group, List<Integer> ids); }
    private boolean singleSelection, selectionRequired, updating;
    private int horizontalSpacing, verticalSpacing;
    private OnCheckedStateChangeListener listener;
    public EchoChipGroup(Context context) { this(context, null); }
    public EchoChipGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EchoChipGroup);
        singleSelection = a.getBoolean(R.styleable.EchoChipGroup_singleSelection, false);
        selectionRequired = a.getBoolean(R.styleable.EchoChipGroup_selectionRequired, false);
        horizontalSpacing = a.getDimensionPixelSize(R.styleable.EchoChipGroup_chipSpacingHorizontal, 0);
        verticalSpacing = a.getDimensionPixelSize(R.styleable.EchoChipGroup_chipSpacingVertical, 0);
        a.recycle();
    }
    @Override public void onViewAdded(View child) {
        super.onViewAdded(child);
        if (!(child instanceof EchoChip)) return;
        EchoChip chip = (EchoChip) child;
        if (chip.getId() == NO_ID) chip.setId(androidx.core.view.ViewCompat.generateViewId());
        chip.addOnCheckedChangeListener((button, checked) -> {
            if (updating) return;
            updating = true;
            if (checked && singleSelection) {
                for (int i = 0; i < getChildCount(); i++) {
                    View other = getChildAt(i);
                    if (other != button && other instanceof EchoChip) ((EchoChip) other).setChecked(false);
                }
            } else if (!checked && selectionRequired && getCheckedChipIds().isEmpty()) button.setChecked(true);
            updating = false;
            if (listener != null) listener.onCheckedChanged(this, getCheckedChipIds());
        });
    }
    public void check(int id) { View view = findViewById(id); if (view instanceof EchoChip) ((EchoChip) view).setChecked(true); }
    public void clearCheck() {
        updating = true;
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i) instanceof EchoChip) ((EchoChip) getChildAt(i)).setChecked(false);
        updating = false;
        if (listener != null) listener.onCheckedChanged(this, getCheckedChipIds());
    }
    public int getCheckedChipId() { List<Integer> ids = getCheckedChipIds(); return ids.isEmpty() ? NO_ID : ids.get(0); }
    public List<Integer> getCheckedChipIds() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i) instanceof EchoChip && ((EchoChip) getChildAt(i)).isChecked()) ids.add(getChildAt(i).getId());
        return ids;
    }
    public void setSingleSelection(boolean value) { singleSelection = value; }
    public boolean isSingleSelection() { return singleSelection; }
    public void setSelectionRequired(boolean value) { selectionRequired = value; }
    public void setOnCheckedStateChangeListener(OnCheckedStateChangeListener listener) { this.listener = listener; }
    @Override protected LayoutParams generateDefaultLayoutParams() { return new MarginLayoutParams(-2, -2); }
    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) { return new MarginLayoutParams(getContext(), attrs); }
    @Override protected LayoutParams generateLayoutParams(LayoutParams params) { return new MarginLayoutParams(params); }
    @Override protected boolean checkLayoutParams(LayoutParams params) { return params instanceof MarginLayoutParams; }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(widthSpec);
        int available = width - getPaddingLeft() - getPaddingRight(), rowWidth = 0, rowHeight = 0, usedWidth = 0, usedHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i); if (child.getVisibility() == GONE) continue;
            measureChildWithMargins(child, widthSpec, 0, heightSpec, 0);
            MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
            int w = child.getMeasuredWidth() + params.leftMargin + params.rightMargin;
            int h = child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
            if (rowWidth > 0 && rowWidth + horizontalSpacing + w > available) {
                usedWidth = Math.max(usedWidth, rowWidth); usedHeight += rowHeight + verticalSpacing; rowWidth = rowHeight = 0;
            }
            rowWidth += (rowWidth > 0 ? horizontalSpacing : 0) + w;
            rowHeight = Math.max(rowHeight, h);
        }
        setMeasuredDimension(resolveSize(Math.max(usedWidth, rowWidth) + getPaddingLeft() + getPaddingRight(), widthSpec),
                resolveSize(usedHeight + rowHeight + getPaddingTop() + getPaddingBottom(), heightSpec));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = getPaddingLeft(), y = getPaddingTop(), rowHeight = 0;
        boolean rtl = androidx.core.view.ViewCompat.getLayoutDirection(this) == androidx.core.view.ViewCompat.LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i); if (child.getVisibility() == GONE) continue;
            MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
            int w = child.getMeasuredWidth() + params.leftMargin + params.rightMargin;
            int h = child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
            if (x > getPaddingLeft() && x + w > right - left - getPaddingRight()) { x = getPaddingLeft(); y += rowHeight + verticalSpacing; rowHeight = 0; }
            int childLeft = rtl ? getWidth() - x - params.rightMargin - child.getMeasuredWidth() : x + params.leftMargin;
            child.layout(childLeft, y + params.topMargin, childLeft + child.getMeasuredWidth(), y + params.topMargin + child.getMeasuredHeight());
            x += w + horizontalSpacing; rowHeight = Math.max(rowHeight, h);
        }
    }
}
