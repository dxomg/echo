package dev.brahmkshatriya.echo.utils.ui.legacy;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** Scrollable legacy tabs with selection/reselection callbacks. */
public class EchoTabLayout extends HorizontalScrollView {
    public interface OnTabSelectedListener {
        void onTabSelected(Tab tab);
        void onTabUnselected(Tab tab);
        void onTabReselected(Tab tab);
    }
    public final class Tab {
        private int position = -1;
        private CharSequence text;
        private EchoButton button;
        public int getPosition() { return position; }
        public CharSequence getText() { return text; }
        public Tab setText(CharSequence value) { text = value; if (button != null) button.setText(value); return this; }
        public Tab setText(int resource) { return setText(getContext().getString(resource)); }
        public void select() { selectTab(this); }
    }
    private final LinearLayout row;
    private final List<Tab> tabs = new ArrayList<>();
    private final List<OnTabSelectedListener> listeners = new ArrayList<>();
    private Tab selected;
    public EchoTabLayout(Context context) { this(context, null); }
    public EchoTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setHorizontalScrollBarEnabled(false);
        row = new LinearLayout(context);
        addView(row, new LayoutParams(-2, -1));
    }
    public Tab newTab() { return new Tab(); }
    public void addTab(Tab tab) { addTab(tab, tabs.isEmpty()); }
    public void addTab(Tab tab, boolean select) {
        tab.position = tabs.size();
        tabs.add(tab);
        EchoButton button = new EchoButton(getContext());
        tab.button = button;
        button.setText(tab.text);
        button.setCheckable(true);
        button.setToggleCheckedStateOnClick(false);
        button.setOnClickListener(view -> selectTab(tab));
        row.addView(button, new LinearLayout.LayoutParams(-2, -1));
        if (select) selectTab(tab);
    }
    public Tab getTabAt(int index) { return index >= 0 && index < tabs.size() ? tabs.get(index) : null; }
    public int getTabCount() { return tabs.size(); }
    public int getSelectedTabPosition() { return selected == null ? -1 : selected.position; }
    public void removeAllTabs() { tabs.clear(); selected = null; row.removeAllViews(); }
    public void addOnTabSelectedListener(OnTabSelectedListener listener) { listeners.add(listener); }
    public void selectTab(Tab tab) {
        if (tab == null) return;
        if (selected == tab) { for (OnTabSelectedListener listener : listeners) listener.onTabReselected(tab); return; }
        Tab old = selected;
        selected = tab;
        if (old != null) { old.button.setChecked(false); for (OnTabSelectedListener listener : listeners) listener.onTabUnselected(old); }
        tab.button.setChecked(true);
        smoothScrollTo(tab.button.getLeft(), 0);
        for (OnTabSelectedListener listener : listeners) listener.onTabSelected(tab);
    }
}
