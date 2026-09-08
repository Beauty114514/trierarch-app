package com.termux.x11;

import android.content.Context;
import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.nio.charset.StandardCharsets;

import androidx.annotation.Keep;

import app.trierarch.input.PointerInputRouter;
import app.trierarch.input.PhysicalKeyEvent;
import app.trierarch.input.PhysicalKeyboardRouter;
import app.trierarch.input.AndroidImeController;
import app.trierarch.input.AndroidImeEvent;
import com.termux.x11.input.X11PointerEventSink;

import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;

/**
 * A deliberately small SurfaceView facade for the fixed Lorie JNI ABI.
 *
 * It owns rendering and the X11 pointer input path. Keyboard,
 * clipboard, scaling controls and external-display policy remain separate.
 */
@Keep
public final class LorieView extends SurfaceView {
    private long nativeHandle;
    private int latestWidth;
    private int latestHeight;
    private final PointerInputRouter inputRouter;
    private final PhysicalKeyboardRouter keyboardRouter;
    private final AndroidImeController androidIme;
    private String composingText = "";
    private int imeBatchEditDepth;

    public LorieView(Context context) {
        super(context);
        inputRouter = new PointerInputRouter(context,
                new X11PointerEventSink((x, y, button, down, relative) ->
                        sendMouseEvent(nativeHandle, x, y, button, down, relative)),
                this::setCursorVisible);
        keyboardRouter = new PhysicalKeyboardRouter(event -> {
            if (nativeHandle == 0 || !isConnected()) return false;
            return sendKeyEvent(
                    nativeHandle,
                    event.getScanCode(),
                    event.getKeyCode(),
                    event.getAction() == PhysicalKeyEvent.Action.DOWN
            );
        });
        androidIme = new AndroidImeController(this, this::handleAndroidImeEvent);
        nativeHandle = nativeInit();
        setFocusable(true);
        setFocusableInTouchMode(true);
        getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                holder.setFormat(5); // HAL_PIXEL_FORMAT_BGRA_8888
            }

            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                latestWidth = width;
                latestHeight = height;
                if (nativeHandle != 0) {
                    LorieView.this.surfaceChanged(nativeHandle, holder.getSurface());
                    publishSize();
                }
            }

            @Override public void surfaceDestroyed(SurfaceHolder holder) {
                if (nativeHandle != 0) LorieView.this.surfaceChanged(nativeHandle, null);
            }
        });
    }

    public void attachConnection(int fileDescriptor) {
        if (nativeHandle == 0) return;
        connect(nativeHandle, fileDescriptor);
        publishSize();
    }

    public boolean isConnected() {
        return nativeHandle != 0 && connected(nativeHandle);
    }

    public void setCursorVisible(boolean visible) {
        if (nativeHandle != 0) setCursorVisible(nativeHandle, visible);
    }

    /** Releases external keys before this view stops being the desktop target. */
    public void releasePressedKeys() {
        keyboardRouter.releaseAll(SystemClock.uptimeMillis());
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyboardRouter.dispatchAndroidEvent(event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyboardRouter.dispatchAndroidEvent(event)) return true;
        return super.onKeyUp(keyCode, event);
    }

    @Override public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return androidIme.createInputConnection(outAttrs);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (nativeHandle == 0) return true;
        boolean handled = inputRouter.onTouchEvent(this, event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP) androidIme.showKeyboard();
        return handled;
    }

    /**
     * Android commits are final Unicode text. Lorie's EVENT_UNICODE path maps
     * each UTF-8 code point to an X11 keysym (adding a dynamic XKB mapping when
     * needed), so this path does not require a guest X11 input-method daemon.
     * Editing controls remain key events because they are not document text.
     */
    private void handleAndroidImeEvent(AndroidImeEvent event) {
        if (nativeHandle == 0 || !isConnected()) return;
        if (event instanceof AndroidImeEvent.BeginBatchEdit) {
            imeBatchEditDepth++;
        } else if (event instanceof AndroidImeEvent.EndBatchEdit) {
            if (imeBatchEditDepth > 0) imeBatchEditDepth--;
        } else if (event instanceof AndroidImeEvent.CommitText) {
            replaceComposingText(((AndroidImeEvent.CommitText) event).getText(), false);
        } else if (event instanceof AndroidImeEvent.SetComposingText) {
            replaceComposingText(((AndroidImeEvent.SetComposingText) event).getText(), true);
        } else if (event instanceof AndroidImeEvent.FinishComposingText) {
            composingText = "";
        } else if (event instanceof AndroidImeEvent.DeleteSurroundingText) {
            composingText = "";
            AndroidImeEvent.DeleteSurroundingText delete = (AndroidImeEvent.DeleteSurroundingText) event;
            sendEditingKeys(KeyEvent.KEYCODE_DEL, delete.getBeforeLength());
            sendEditingKeys(KeyEvent.KEYCODE_FORWARD_DEL, delete.getAfterLength());
        } else if (event instanceof AndroidImeEvent.DeleteSurroundingTextInCodePoints) {
            composingText = "";
            AndroidImeEvent.DeleteSurroundingTextInCodePoints delete = (AndroidImeEvent.DeleteSurroundingTextInCodePoints) event;
            sendEditingKeys(KeyEvent.KEYCODE_DEL, delete.getBeforeLength());
            sendEditingKeys(KeyEvent.KEYCODE_FORWARD_DEL, delete.getAfterLength());
        } else if (event instanceof AndroidImeEvent.SetSelection) {
            AndroidImeEvent.SetSelection selection = (AndroidImeEvent.SetSelection) event;
            if (imeBatchEditDepth == 0 && selection.getStart() == selection.getEnd()) {
                if (selection.getStart() < 1) sendKeyPress(KeyEvent.KEYCODE_DPAD_LEFT);
                else if (selection.getStart() > 1) sendKeyPress(KeyEvent.KEYCODE_DPAD_RIGHT);
            }
        } else if (event instanceof AndroidImeEvent.KeyEvent) {
            AndroidImeEvent.KeyEvent key = (AndroidImeEvent.KeyEvent) event;
            if (key.getAction() == KeyEvent.ACTION_DOWN && isImeControlKey(key.getKeyCode())) {
                sendKeyPress(key.getKeyCode());
            }
        } else if (event instanceof AndroidImeEvent.EditorAction) {
            sendEditorAction(((AndroidImeEvent.EditorAction) event).getActionCode());
        }
    }

    /** Mirrors Termux:X11's replacement model for Android IME preedit text. */
    private void replaceComposingText(String replacement, boolean keepComposing) {
        if (replacement.startsWith(composingText)) {
            sendCommittedText(replacement.substring(composingText.length()));
        } else if (composingText.startsWith(replacement)) {
            sendEditingKeys(KeyEvent.KEYCODE_DEL, codePointCount(composingText) - codePointCount(replacement));
        } else {
            sendEditingKeys(KeyEvent.KEYCODE_DEL, codePointCount(composingText));
            sendCommittedText(replacement);
        }
        composingText = keepComposing ? replacement : "";
    }

    private static int codePointCount(String text) {
        return text.codePointCount(0, text.length());
    }

    private void sendCommittedText(String text) {
        StringBuilder committed = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\n' || character == '\r' || character == '\u2028' || character == '\u2029') {
                sendCommittedText(committed);
                committed.setLength(0);
                sendKeyPress(KeyEvent.KEYCODE_ENTER);
                if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
            } else if (character == '\t') {
                sendCommittedText(committed);
                committed.setLength(0);
                sendKeyPress(KeyEvent.KEYCODE_TAB);
            } else if (character == '\b') {
                sendCommittedText(committed);
                committed.setLength(0);
                sendKeyPress(KeyEvent.KEYCODE_DEL);
            } else if (character == 0x7f) {
                sendCommittedText(committed);
                committed.setLength(0);
                sendKeyPress(KeyEvent.KEYCODE_FORWARD_DEL);
            } else if (character == 0x1b) {
                sendCommittedText(committed);
                committed.setLength(0);
                sendKeyPress(KeyEvent.KEYCODE_ESCAPE);
            } else if (character >= ' ') {
                committed.append(character);
            }
        }
        sendCommittedText(committed);
    }

    private void sendCommittedText(StringBuilder text) {
        if (text.length() != 0) sendTextEvent(nativeHandle, text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void sendEditingKeys(int keyCode, int count) {
        for (int index = 0; index < count; index++) sendKeyPress(keyCode);
    }

    private void sendKeyPress(int keyCode) {
        sendKeyEvent(nativeHandle, 0, keyCode, true);
        sendKeyEvent(nativeHandle, 0, keyCode, false);
    }

    private static boolean isImeControlKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_TAB:
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_FORWARD_DEL:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MOVE_HOME:
            case KeyEvent.KEYCODE_MOVE_END:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return true;
            default:
                return false;
        }
    }

    private void sendEditorAction(int actionCode) {
        switch (actionCode) {
            case EditorInfo.IME_ACTION_DONE:
            case EditorInfo.IME_ACTION_GO:
            case EditorInfo.IME_ACTION_NEXT:
            case EditorInfo.IME_ACTION_PREVIOUS:
            case EditorInfo.IME_ACTION_SEARCH:
            case EditorInfo.IME_ACTION_SEND:
                sendKeyPress(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                break;
        }
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if (nativeHandle == 0) return true;
        return inputRouter.onGenericMotionEvent(this, event) || super.onGenericMotionEvent(event);
    }

    @Override public boolean onHoverEvent(MotionEvent event) {
        if (nativeHandle == 0) return true;
        return inputRouter.onHoverEvent(this, event) || super.onHoverEvent(event);
    }

    @Override protected void onDetachedFromWindow() {
        inputRouter.cancel(this);
        releasePressedKeys();
        if (nativeHandle != 0) {
            nativeDestroy(nativeHandle);
            nativeHandle = 0;
        }
        super.onDetachedFromWindow();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            inputRouter.cancel(this);
            releasePressedKeys();
        }
    }

    @Override protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (!gainFocus) releasePressedKeys();
    }

    private void publishSize() {
        if (nativeHandle == 0 || latestWidth <= 0 || latestHeight <= 0) return;
        setViewport(nativeHandle, 0, 0, latestWidth, latestHeight, latestWidth, latestHeight, 0);
        sendWindowChange(nativeHandle, latestWidth, latestHeight, refreshRate(), "builtin");
    }

    private int refreshRate() {
        return getDisplay() == null ? 60 : Math.max(1, Math.round(getDisplay().getRefreshRate()));
    }

    // Called by Lorie's renderer. Trierarch currently renders at the SurfaceView bounds.
    @Keep public static void setRendererViewport(
        int x, int y, int width, int height, float left, float top, float sourceWidth, float sourceHeight
    ) {}

    // Called by Lorie's native event loop; clipboard is intentionally deferred.
    @Keep void setClipboardText(String text) {}
    @Keep void requestClipboard() {}
    @Keep void resetIme() {}

    @FastNative private native long nativeInit();
    @FastNative private native void nativeDestroy(long handle);
    @FastNative private native void surfaceChanged(long handle, Surface surface);
    @FastNative private native void setViewport(long handle, int x, int y, int width, int height, int expectedWidth, int expectedHeight, int hidden);
    @FastNative private native void setCursorVisible(long handle, boolean visible);
    @FastNative private native void setRendererZoom(long handle, int percent);
    @FastNative private native void setFiltering(long handle, int filtering);
    @FastNative private static native void connect(long handle, int fileDescriptor);
    @CriticalNative private static native boolean connected(long handle);
    @FastNative private static native void startLogcat(long handle, int fileDescriptor);
    @FastNative private static native void setClipboardSyncEnabled(long handle, boolean enabled, boolean ignored);
    @FastNative private native void sendClipboardAnnounce(long handle);
    @FastNative private native void sendClipboardEvent(long handle, byte[] text);
    @FastNative private static native void sendWindowChange(long handle, int width, int height, int framerate, String name);
    @FastNative private native void sendMouseEvent(long handle, float x, float y, int button, boolean down, boolean relative);
    @FastNative private native void sendTouchEvent(long handle, int action, int id, int x, int y);
    @FastNative private native void sendStylusEvent(long handle, float x, float y, int pressure, int tiltX, int tiltY, int orientation, int buttons, boolean eraser, boolean mouse);
    @FastNative private static native void requestStylusEnabled(long handle, boolean enabled);
    @FastNative private static native void sendLockKeysState(long handle, int state);
    @FastNative private native boolean sendKeyEvent(long handle, int scanCode, int keyCode, boolean down);
    @FastNative private native void sendTextEvent(long handle, byte[] text);
    @CriticalNative private static native boolean requestConnection(long handle);
    @FastNative private static native long getLastInputTimestamp();
    @FastNative private static native void markUserActivity();

    static {
        System.loadLibrary("Xlorie");
    }
}
