package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.brahmkshatriya.echo.R;

/** Single-selection group without Material or API21 framework dependencies. */
public class EchoButtonToggleGroup extends LinearLayout {
    private final List<OnButtonCheckedListener> listeners = new ArrayList<>();
    private final Map<EchoButton, EchoButton.OnCheckedChangeListener> childListeners = new HashMap<>();
    private boolean selectionRequired;
    private boolean updating;
    private int checkedButtonId = View.NO_ID;
    private int initialCheckedId = View.NO_ID;
    private OnHierarchyChangeListener hierarchyListener;

    public EchoButtonToggleGroup(Context context) {
        this(context, null);
    }

    public EchoButtonToggleGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        android.content.res.TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.EchoButtonToggleGroup);
        selectionRequired = a.getBoolean(R.styleable.EchoButtonToggleGroup_selectionRequired, false);
        initialCheckedId = a.getResourceId(R.styleable.EchoButtonToggleGroup_checkedButton, View.NO_ID);
        a.recycle();
        // onViewAdded/onViewRemoved are only dispatched by the framework on API23+.
        super.setOnHierarchyChangeListener(new OnHierarchyChangeListener() {
            @Override public void onChildViewAdded(View parent, View child) {
                attachButton(child);
                if (hierarchyListener != null) hierarchyListener.onChildViewAdded(parent, child);
            }

            @Override public void onChildViewRemoved(View parent, View child) {
                detachButton(child);
                if (hierarchyListener != null) hierarchyListener.onChildViewRemoved(parent, child);
            }
        });
    }

    @Override public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {
        // Keep the internal API18 registration listener installed.
        hierarchyListener = listener;
    }

    public interface OnButtonCheckedListener {
        void onButtonChecked(EchoButtonToggleGroup group, int id, boolean checked);
    }

    public void addOnButtonCheckedListener(OnButtonCheckedListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeOnButtonCheckedListener(OnButtonCheckedListener listener) {
        listeners.remove(listener);
    }

    public int getCheckedButtonId() {
        return checkedButtonId;
    }

    private void attachButton(View child) {
        if (!(child instanceof EchoButton)) return;
        EchoButton button = (EchoButton) child;
        if (childListeners.containsKey(button)) return;
        if (button.getId() == View.NO_ID) button.setId(View.generateViewId());
        button.setCheckable(true);
        EchoButton.OnCheckedChangeListener listener = (changed, checked) -> {
            if (updating) return;
            if (checked) check(changed.getId());
            else if (selectionRequired && checkedButtonId == changed.getId()) {
                updating = true;
                changed.setChecked(true);
                updating = false;
            } else if (checkedButtonId == changed.getId()) clearChecked();
        };
        childListeners.put(button, listener);
        button.addOnCheckedChangeListener(listener);
        if (button.getId() == initialCheckedId || button.isChecked()) check(button.getId());
    }

    private void detachButton(View child) {
        if (child instanceof EchoButton) {
            EchoButton button = (EchoButton) child;
            button.removeOnCheckedChangeListener(childListeners.remove(button));
            if (checkedButtonId == button.getId()) {
                clearChecked();
                if (selectionRequired) {
                    for (int i = 0; i < getChildCount(); i++) {
                        View remaining = getChildAt(i);
                        if (remaining != child && remaining instanceof EchoButton) {
                            check(remaining.getId());
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        if (initialCheckedId != View.NO_ID) check(initialCheckedId);
        initialCheckedId = View.NO_ID;
    }

    public void check(int id) {
        if (id != View.NO_ID) {
            boolean found = false;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof EchoButton && child.getId() == id) found = true;
            }
            if (!found) return;
        }
        int previous = checkedButtonId;
        checkedButtonId = id;
        updating = true;
        try {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof EchoButton) {
                    ((EchoButton) child).setChecked(child.getId() == id);
                }
            }
        } finally {
            updating = false;
        }
        if (previous != id) {
            if (previous != View.NO_ID) notifyChecked(previous, false);
            if (id != View.NO_ID) notifyChecked(id, true);
        }
    }

    public void clearChecked() {
        check(View.NO_ID);
    }

    private void notifyChecked(int id, boolean checked) {
        for (OnButtonCheckedListener listener : new ArrayList<>(listeners)) {
            listener.onButtonChecked(this, id, checked);
        }
    }

    @Override public Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.checkedId = checkedButtonId;
        return state;
    }

    @Override public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState saved = (SavedState) state;
        super.onRestoreInstanceState(saved.getSuperState());
        checkedButtonId = saved.checkedId;
    }

    @Override protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        // Child restoration must not fight selectionRequired or overwrite the group selection.
        updating = true;
        try {
            super.dispatchRestoreInstanceState(container);
        } finally {
            updating = false;
        }
        int restored = checkedButtonId;
        if (restored != View.NO_ID) {
            boolean found = false;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof EchoButton && child.getId() == restored) found = true;
            }
            if (!found) restored = View.NO_ID;
        }
        check(restored);
    }

    public static class SavedState extends BaseSavedState {
        int checkedId;
        SavedState(Parcelable state) { super(state); }
        SavedState(Parcel source) {
            super(source);
            checkedId = source.readInt();
        }
        @Override public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(checkedId);
        }
        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override public SavedState createFromParcel(Parcel source) { return new SavedState(source); }
            @Override public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }
}
