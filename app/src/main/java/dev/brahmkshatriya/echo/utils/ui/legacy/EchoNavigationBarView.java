package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.view.menu.MenuBuilder;
import dev.brahmkshatriya.echo.R;

/** Menu-backed accessible navigation with checked state, icons and rail layout. */
public class EchoNavigationBarView extends LinearLayout {
    public interface OnItemSelectedListener { boolean onNavigationItemSelected(MenuItem item); }
    private final MenuBuilder menu;
    private OnItemSelectedListener listener;
    private int selected;
    private ColorStateList iconTint;
    public EchoNavigationBarView(Context context) { this(context, null); }
    public EchoNavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        menu = new MenuBuilder(context);
        android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EchoNavigationBarView);
        int menuId = a.getResourceId(R.styleable.EchoNavigationBarView_menu, 0);
        iconTint = a.getColorStateList(R.styleable.EchoNavigationBarView_itemIconTint);
        a.recycle();
        if (menuId != 0) new SupportMenuInflater(context).inflate(menuId, menu);
        rebuild();
    }
    public Menu getMenu() { return menu; }
    public int getSelectedItemId() { return selected; }
    public void setSelectedItemId(int id) { select(id, false); }
    public void setOnItemSelectedListener(OnItemSelectedListener listener) { this.listener = listener; }
    public ColorStateList getItemIconTintList() { return iconTint; }
    public void setItemIconTintList(ColorStateList tint) { iconTint = tint; rebuild(); }
    protected void rebuild() {
        removeAllViews();
        if (menu == null) return;
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) continue;
            EchoButton button = new EchoButton(getContext());
            button.setId(item.getItemId());
            button.setText(item.getTitle());
            button.setContentDescription(item.getTitle());
            button.setCheckable(true);
            button.setChecked(item.getItemId() == selected);
            button.setToggleCheckedStateOnClick(false);
            button.setIcon(item.getIcon());
            button.setIconGravity(16);
            if (iconTint != null) button.setIconTint(iconTint);
            button.setTextSize(12);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setOnClickListener(view -> select(item.getItemId(), true));
            addView(button, getOrientation() == HORIZONTAL ? new LayoutParams(0, -1, 1) : new LayoutParams(-1, -2));
        }
    }
    private void select(int id, boolean notify) {
        MenuItem item = menu.findItem(id);
        if (item == null || !item.isEnabled() || notify && listener != null && !listener.onNavigationItemSelected(item)) return;
        selected = id;
        for (int i = 0; i < menu.size(); i++) menu.getItem(i).setChecked(menu.getItem(i).getItemId() == id);
        for (int i = 0; i < getChildCount(); i++) ((EchoButton) getChildAt(i)).setChecked(getChildAt(i).getId() == id);
    }
}
