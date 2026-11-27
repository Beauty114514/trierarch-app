package app.trierarch.input;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Maps direct finger input to an absolute pointer, preserving tap and long-press. */
public final class TouchscreenInputHandler {
    private static final long LONG_PRESS_MILLIS = 500L;

    private final PointerEventSink sink;
    private final int touchSlop;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float downX;
    private float downY;
    private boolean dragging;
    private boolean longPressed;
    private boolean scrolling;
    private boolean suppressTap;
    private int heldButton;
    private float lastScrollX;
    private float lastScrollY;

    private final Runnable longPress;

    public TouchscreenInputHandler(Context context, PointerEventSink sink) {
        this.sink = sink;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        longPress = () -> {
            if (dragging) return;
            longPressed = true;
            heldButton = 3;
            sink.setButton(heldButton, true);
        };
    }

    public boolean onTouchEvent(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                longPressed = false;
                scrolling = false;
                suppressTap = false;
                lastScrollX = downX;
                lastScrollY = downY;
                sink.moveAbsolute(downX, downY);
                handler.postDelayed(longPress, LONG_PRESS_MILLIS);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() >= 2) {
                    handler.removeCallbacks(longPress);
                    releaseHeldButton();
                    dragging = false;
                    longPressed = false;
                    scrolling = true;
                    suppressTap = true;
                    lastScrollX = centroidX(event);
                    lastScrollY = centroidY(event);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (scrolling && event.getPointerCount() >= 2) {
                    float centerX = centroidX(event);
                    float centerY = centroidY(event);
                    sink.scroll(lastScrollX - centerX, lastScrollY - centerY,
                            PointerEventSink.ScrollSource.FINGER);
                    lastScrollX = centerX;
                    lastScrollY = centerY;
                    return true;
                }
                if (scrolling || suppressTap) return true;
                float x = event.getX();
                float y = event.getY();
                if (!dragging && (Math.abs(x - downX) > touchSlop ||
                        Math.abs(y - downY) > touchSlop)) {
                    dragging = true;
                    handler.removeCallbacks(longPress);
                    if (heldButton == 0) {
                        heldButton = 1;
                        sink.setButton(heldButton, true);
                    }
                }
                sink.moveAbsolute(x, y);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (scrolling) {
                    scrolling = false;
                    suppressTap = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
                finish();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancel();
                return true;
            default:
                return false;
        }
    }

    private static float centroidX(MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) sum += event.getX(i);
        return sum / event.getPointerCount();
    }

    private static float centroidY(MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) sum += event.getY(i);
        return sum / event.getPointerCount();
    }

    private void releaseHeldButton() {
        if (heldButton == 0) return;
        sink.setButton(heldButton, false);
        heldButton = 0;
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        return false;
    }

    public boolean onHoverEvent(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) cancel();
        return false;
    }

    public void cancel() {
        handler.removeCallbacks(longPress);
        if (heldButton != 0) {
            sink.setButton(heldButton, false);
            heldButton = 0;
        }
        dragging = false;
        longPressed = false;
        scrolling = false;
        suppressTap = false;
        sink.cancel();
    }

    private void finish() {
        handler.removeCallbacks(longPress);
        if (longPressed) {
            if (heldButton != 0) sink.setButton(heldButton, false);
        } else if (dragging) {
            if (heldButton != 0) sink.setButton(heldButton, false);
        } else if (!suppressTap && !scrolling) {
            sink.setButton(1, true);
            sink.setButton(1, false);
        }
        heldButton = 0;
        dragging = false;
        longPressed = false;
        scrolling = false;
        suppressTap = false;
    }
}
