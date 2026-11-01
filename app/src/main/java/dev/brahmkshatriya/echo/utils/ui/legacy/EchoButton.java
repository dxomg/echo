package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatButton;

import java.util.ArrayList;
import java.util.List;

import dev.brahmkshatriya.echo.R;

/** API18 button with compatible icons and the app's checked-state contract. */
public class EchoButton extends AppCompatButton implements Checkable {
    private static final int[] CHECKED_STATE = {android.R.attr.state_checked};
    private static final int[] CHECKABLE_STATE = {android.R.attr.state_checkable};
    private final List<OnCheckedChangeListener> listeners = new ArrayList<>();
    private boolean checkable;
    private boolean checked;
    private boolean broadcasting;
    private boolean toggleOnClick = true;
    private Drawable icon;
    private ColorStateList iconTint;
    private int iconSize;
    private int iconGravity;

    public EchoButton(Context context) {
        this(context, null);
    }

    public EchoButton(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.buttonStyle);
    }

    public EchoButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        android.content.res.TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.EchoButton, defStyleAttr, R.style.EchoButton);
        checkable = a.getBoolean(R.styleable.EchoButton_android_checkable, false);
        checked = checkable && a.getBoolean(R.styleable.EchoButton_android_checked, false);
        toggleOnClick = a.getBoolean(R.styleable.EchoButton_toggleCheckedStateOnClick, true);
        iconSize = a.getDimensionPixelSize(R.styleable.EchoButton_iconSize,
                Math.round(24 * getResources().getDisplayMetrics().density));
        iconGravity = a.getInt(R.styleable.EchoButton_iconGravity, 1);
        setCompoundDrawablePadding(a.getDimensionPixelSize(R.styleable.EchoButton_iconPadding, 0));
        int tintId = a.getResourceId(R.styleable.EchoButton_iconTint, 0);
        iconTint = tintId != 0 ? AppCompatResources.getColorStateList(getContext(), tintId)
                : a.getColorStateList(R.styleable.EchoButton_iconTint);
        if (iconTint == null) iconTint = getTextColors();
        int iconId = a.getResourceId(R.styleable.EchoButton_icon, 0);
        a.recycle();
        if (iconId != 0) setIconResource(iconId);
        refreshDrawableState();
    }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(EchoButton button, boolean checked);
    }

    public void addOnCheckedChangeListener(OnCheckedChangeListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeOnCheckedChangeListener(OnCheckedChangeListener listener) {
        listeners.remove(listener);
    }

    public boolean isCheckable() {
        return checkable;
    }

    public void setCheckable(boolean value) {
        if (checkable == value) return;
        if (!value) setChecked(false);
        checkable = value;
        refreshDrawableState();
    }

    public void setToggleCheckedStateOnClick(boolean value) {
        toggleOnClick = value;
    }

    @Override public boolean isChecked() {
        return checked;
    }

    @Override public void setChecked(boolean value) {
        if (!checkable || checked == value) return;
        checked = value;
        refreshDrawableState();
        if (broadcasting) return;
        broadcasting = true;
        try {
            for (OnCheckedChangeListener listener : new ArrayList<>(listeners)) {
                listener.onCheckedChanged(this, checked);
            }
        } finally {
            broadcasting = false;
        }
    }

    @Override public void toggle() {
        setChecked(!checked);
    }

    @Override public boolean performClick() {
        if (isEnabled() && toggleOnClick) toggle();
        return super.performClick();
    }

    @Override protected int[] onCreateDrawableState(int extraSpace) {
        int[] state = super.onCreateDrawableState(extraSpace + 2);
        if (checkable) mergeDrawableStates(state, CHECKABLE_STATE);
        if (checked) mergeDrawableStates(state, CHECKED_STATE);
        return state;
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable value) {
        icon = value == null ? null : value.mutate();
        updateIcon();
    }

    public void setIconResource(int resource) {
        setIcon(resource == 0 ? null : AppCompatResources.getDrawable(getContext(), resource));
    }

    public ColorStateList getIconTint() {
        return iconTint;
    }

    public void setIconTint(ColorStateList value) {
        iconTint = value;
        updateIcon();
    }

    public int getIconSize() {
        return iconSize;
    }

    public void setIconSize(int value) {
        if (value < 0) throw new IllegalArgumentException("Icon size must not be negative");
        iconSize = value;
        updateIcon();
    }

    public int getIconGravity() {
        return iconGravity;
    }

    public void setIconGravity(int value) {
        iconGravity = value;
        updateIcon();
    }

    public int getIconPadding() {
        return getCompoundDrawablePadding();
    }

    public void setIconPadding(int value) {
        setCompoundDrawablePadding(value);
    }

    private void updateIcon() {
        if (icon != null) {
            int width = iconSize == 0 ? Math.max(0, icon.getIntrinsicWidth()) : iconSize;
            int height = iconSize == 0 ? Math.max(0, icon.getIntrinsicHeight()) : iconSize;
            icon.setBounds(0, 0, width, height);
            icon.setState(getDrawableState());
            tintIcon();
        }
        // Relative compound drawables are available since API17.
        setCompoundDrawablesRelative(
                iconGravity == 1 || iconGravity == 2 ? icon : null,
                iconGravity == 16 || iconGravity == 32 ? icon : null,
                iconGravity == 3 || iconGravity == 4 ? icon : null, null);
    }

    private void tintIcon() {
        if (icon == null) return;
        if (iconTint == null) icon.clearColorFilter();
        else icon.setColorFilter(iconTint.getColorForState(getDrawableState(),
                iconTint.getDefaultColor()), PorterDuff.Mode.SRC_IN);
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (icon != null && icon.isStateful()) icon.setState(getDrawableState());
        tintIcon();
    }

    // Kotlin's backgroundTintList must use AppCompat on API18, not View's API21 method.
    @Override public void setBackgroundTintList(ColorStateList tint) {
        setSupportBackgroundTintList(tint);
    }

    @Override public ColorStateList getBackgroundTintList() {
        return getSupportBackgroundTintList();
    }

    @Override public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setChecked(checked);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(checkable);
        info.setChecked(checked);
    }

    @Override public Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.checked = checked;
        return state;
    }

    @Override public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState saved = (SavedState) state;
        super.onRestoreInstanceState(saved.getSuperState());
        setChecked(saved.checked);
    }

    public static class SavedState extends BaseSavedState {
        boolean checked;

        SavedState(Parcelable state) {
            super(state);
        }

        SavedState(Parcel source) {
            super(source);
            checked = source.readInt() != 0;
        }

        @Override public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(checked ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override public SavedState createFromParcel(Parcel source) {
                return new SavedState(source);
            }

            @Override public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
