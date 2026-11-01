package dev.brahmkshatriya.echo.utils.ui.legacy;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.SeekBar;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.graphics.drawable.DrawableCompat;
import java.util.ArrayList;
import java.util.List;
import dev.brahmkshatriya.echo.R;

/** Float-valued, step-aware slider backed by the legacy SeekBar. */
public class EchoSlider extends AppCompatSeekBar {
    private float valueFrom = 0f, valueTo = 1f, stepSize = 0f, value;
    private final List<OnChangeListener> changes = new ArrayList<>();
    private final List<OnSliderTouchListener> touches = new ArrayList<>();
    private boolean updating;
    private ColorStateList thumbColors;

    public EchoSlider(Context context) { this(context, null); }
    public EchoSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EchoSlider);
        valueFrom = a.getFloat(R.styleable.EchoSlider_valueFrom, 0f);
        valueTo = a.getFloat(R.styleable.EchoSlider_valueTo, 1f);
        stepSize = a.getFloat(R.styleable.EchoSlider_stepSize, 0f);
        float initial = a.getFloat(R.styleable.EchoSlider_value, valueFrom);
        a.recycle();
        setMax(10000);
        setValue(initial);
        setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!updating) updateValue(valueFrom + (valueTo - valueFrom) * progress / getMax(), fromUser);
            }
            public void onStartTrackingTouch(SeekBar bar) {
                for (OnSliderTouchListener l : new ArrayList<>(touches)) l.onStartTrackingTouch(EchoSlider.this);
            }
            public void onStopTrackingTouch(SeekBar bar) {
                for (OnSliderTouchListener l : new ArrayList<>(touches)) l.onStopTrackingTouch(EchoSlider.this);
            }
        });
    }
    private void updateValue(float next, boolean fromUser) {
        if (Float.isNaN(next) || Float.isInfinite(next)) return;
        if (stepSize > 0f) next = valueFrom + Math.round((next - valueFrom) / stepSize) * stepSize;
        next = Math.max(valueFrom, Math.min(valueTo, next));
        boolean changed = Float.compare(value, next) != 0;
        value = next;
        updating = true;
        setProgress(valueTo <= valueFrom ? 0 : Math.round((value - valueFrom) / (valueTo - valueFrom) * getMax()));
        updating = false;
        if (changed) for (OnChangeListener l : new ArrayList<>(changes)) l.onValueChange(this, value, fromUser);
    }
    /** Set all bounds before quantization and notify only for the final value. */
    public void configure(float from, float to, float step, float initial) {
        if (Float.isNaN(from) || Float.isInfinite(from)
                || Float.isNaN(to) || Float.isInfinite(to)
                || Float.isNaN(step) || Float.isInfinite(step))
            throw new IllegalArgumentException("Slider bounds must be finite");
        if (to < from || step < 0f) throw new IllegalArgumentException("Invalid slider range");
        valueFrom = from;
        valueTo = to;
        stepSize = step;
        updateValue(initial, false);
    }
    public float getValue() { return value; }
    public void setValue(float value) { updateValue(value, false); }
    public float getValueFrom() { return valueFrom; }
    public void setValueFrom(float from) { valueFrom = from; if (valueTo < from) valueTo = from; setValue(value); }
    public float getValueTo() { return valueTo; }
    public void setValueTo(float to) { valueTo = Math.max(valueFrom, to); setValue(value); }
    public float getStepSize() { return stepSize; }
    public void setStepSize(float step) { stepSize = Math.max(0f, step); setValue(value); }
    public ColorStateList getThumbTintList() { return thumbColors; }
    public void setThumbTintList(ColorStateList colors) {
        thumbColors = colors;
        if (getThumb() != null) {
            android.graphics.drawable.Drawable d = DrawableCompat.wrap(getThumb()).mutate();
            DrawableCompat.setTintList(d, colors);
            setThumb(d);
        }
    }
    public void addOnChangeListener(OnChangeListener l) { changes.add(l); }
    public void clearOnChangeListeners() { changes.clear(); }
    public void addOnSliderTouchListener(OnSliderTouchListener l) { touches.add(l); }
    public interface OnChangeListener { void onValueChange(EchoSlider slider, float value, boolean fromUser); }
    public interface OnSliderTouchListener {
        void onStartTrackingTouch(EchoSlider slider);
        void onStopTrackingTouch(EchoSlider slider);
    }
}
