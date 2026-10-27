package com.example.quake;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

import org.libsdl.app.SDLActivity;

/** Transparent on-screen controls drawn on top of the game. */
public class TouchOverlay extends View {

    private static final int MAX_POINTERS = 32;

    private static final int ROLE_NONE = 0;
    private static final int ROLE_STICK = 1;
    private static final int ROLE_LOOK = 2;
    private static final int ROLE_BUTTON = 3;

    private static class Btn {
        final String label;
        final int key;          // 0 = not a key button
        final boolean look;     // sliding a finger that started here also aims
        float x, y, r;
        boolean pressed;
        Btn(String label, int key, boolean look) {
            this.label = label;
            this.key = key;
            this.look = look;
        }
    }

    private final Btn fire = new Btn("FIRE", KeyEvent.KEYCODE_ENTER, true);
    private final Btn jump = new Btn("JUMP", KeyEvent.KEYCODE_SPACE, true);
    private final Btn weapon = new Btn("WPN", KeyEvent.KEYCODE_E, false);
    private final Btn menu = new Btn("MENU", KeyEvent.KEYCODE_ESCAPE, false);
    private final Btn settingsBtn = new Btn("SET", 0, false);
    private final Btn[] buttons = { fire, jump, weapon, menu, settingsBtn };

    private final int[] role = new int[MAX_POINTERS];
    private final Btn[] heldButton = new Btn[MAX_POINTERS];
    private final float[] btnLastX = new float[MAX_POINTERS];
    private final float[] btnLastY = new float[MAX_POINTERS];

    // movement stick
    private int stickId = -1;
    private float defStickX, defStickY;
    private float stickX, stickY, stickCurX, stickCurY, stickRadius;
    private boolean kUp, kDown, kLeft, kRight, kRun;

    // look
    private int lookId = -1;
    private float lastX, lastY;

    // saved settings
    private final SharedPreferences prefs;
    private float lookSpeed;        // multiplier on top of Quake's own sensitivity
    private float runThreshold;     // fraction of the stick radius where running starts
    private boolean invertLook;

    // settings panel
    private boolean panelOpen = false;
    private final RectF panel = new RectF();
    private final RectF lookMinus = new RectF(), lookPlus = new RectF();
    private final RectF runMinus = new RectF(), runPlus = new RectF();
    private final RectF invertBox = new RectF(), closeBox = new RectF();
    private float rowLookY, rowRunY, rowInvertY, titleY;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leftText = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TouchOverlay(Context context) {
        super(context);
        setWillNotDraw(false);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(4f);
        ringPaint.setColor(0x88FFFFFF);
        textPaint.setColor(0xDDFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        leftText.setColor(0xFFFFFFFF);
        leftText.setTextAlign(Paint.Align.LEFT);

        prefs = context.getSharedPreferences("controls", Context.MODE_PRIVATE);
        lookSpeed = prefs.getFloat("look", 1.3f);
        runThreshold = prefs.getFloat("run", 0.55f);
        invertLook = prefs.getBoolean("invert", false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float r = 0.11f * Math.min(w, h);
        fire.r = r;          fire.x = w - 1.5f * r;   fire.y = h - 1.8f * r;
        jump.r = r;          jump.x = w - 4.0f * r;   jump.y = h - 1.2f * r;
        weapon.r = 0.8f * r; weapon.x = w - 1.5f * r; weapon.y = h - 4.4f * r;
        menu.r = 0.7f * r;   menu.x = w * 0.5f;       menu.y = menu.r * 1.3f;
        settingsBtn.r = 0.6f * r; settingsBtn.x = 1.4f * r; settingsBtn.y = 1.2f * r;

        stickRadius = 1.5f * r;
        defStickX = 2.6f * r;
        defStickY = h - 2.6f * r;
        if (stickId < 0) {
            stickX = defStickX;
            stickY = defStickY;
            stickCurX = stickX;
            stickCurY = stickY;
        }
        textPaint.setTextSize(0.45f * r);
        leftText.setTextSize(0.5f * r);

        // settings panel layout
        panel.set(0.18f * w, 0.10f * h, 0.82f * w, 0.90f * h);
        float ph = panel.height();
        float bs = 0.13f * h;
        titleY = panel.top + 0.12f * ph;
        rowLookY = panel.top + 0.34f * ph;
        rowRunY = panel.top + 0.53f * ph;
        rowInvertY = panel.top + 0.72f * ph;
        float plusCx = panel.right - 0.07f * panel.width() - bs / 2f;
        float minusCx = plusCx - bs * 1.5f;
        setRect(lookPlus, plusCx, rowLookY, bs);
        setRect(lookMinus, minusCx, rowLookY, bs);
        setRect(runPlus, plusCx, rowRunY, bs);
        setRect(runMinus, minusCx, rowRunY, bs);
        setRect(invertBox, plusCx, rowInvertY, bs);
        closeBox.set(panel.centerX() - 0.12f * w, panel.top + 0.80f * ph,
                     panel.centerX() + 0.12f * w, panel.top + 0.80f * ph + bs);
    }

    private static void setRect(RectF rect, float cx, float cy, float size) {
        rect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
    }

    // ---- sending input to SDL ----

    private static void keyDown(int code) {
        SDLActivity.onNativeKeyDown(code);
    }

    private static void keyUp(int code) {
        SDLActivity.onNativeKeyUp(code);
    }

    private static boolean setKey(int code, boolean was, boolean now) {
        if (now && !was) keyDown(code);
        else if (!now && was) keyUp(code);
        return now;
    }

    private void saveSettings() {
        prefs.edit()
             .putFloat("look", lookSpeed)
             .putFloat("run", runThreshold)
             .putBoolean("invert", invertLook)
             .apply();
    }

    // ---- touch handling ----

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int idx = e.getActionIndex();
        int action = e.getActionMasked();

        if (panelOpen) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                panelTap(e.getX(idx), e.getY(idx));
                invalidate();
            }
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                pointerDown(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    pointerMove(e.getPointerId(i), e.getX(i), e.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                pointerUp(e.getPointerId(idx));
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    private void releaseAll() {
        for (int id = 0; id < MAX_POINTERS; id++) pointerUp(id);
    }

    private void pointerDown(int id, float x, float y) {
        if (id < 0 || id >= MAX_POINTERS) return;
        for (Btn b : buttons) {
            float dx = x - b.x;
            float dy = y - b.y;
            if (dx * dx + dy * dy <= b.r * b.r * 1.4f) {
                if (b == settingsBtn) {
                    releaseAll();
                    panelOpen = true;
                    return;
                }
                b.pressed = true;
                heldButton[id] = b;
                role[id] = ROLE_BUTTON;
                btnLastX[id] = x;
                btnLastY[id] = y;
                keyDown(b.key);
                return;
            }
        }
        if (x < getWidth() * 0.45f) {
            if (stickId < 0) {
                stickId = id;
                role[id] = ROLE_STICK;
                stickX = x;
                stickY = y;
                stickCurX = x;
                stickCurY = y;
            }
        } else if (lookId < 0) {
            lookId = id;
            role[id] = ROLE_LOOK;
            lastX = x;
            lastY = y;
        }
    }

    private void pointerMove(int id, float x, float y) {
        if (id < 0 || id >= MAX_POINTERS) return;
        if (role[id] == ROLE_STICK) {
            stickCurX = x;
            stickCurY = y;
            float dx = x - stickX;
            float dy = y - stickY;
            float dead = stickRadius * 0.3f;
            float runAt = stickRadius * runThreshold;
            kUp = setKey(KeyEvent.KEYCODE_DPAD_UP, kUp, dy < -dead);
            kDown = setKey(KeyEvent.KEYCODE_DPAD_DOWN, kDown, dy > dead);
            kLeft = setKey(KeyEvent.KEYCODE_DPAD_LEFT, kLeft, dx < -dead);
            kRight = setKey(KeyEvent.KEYCODE_DPAD_RIGHT, kRight, dx > dead);
            kRun = setKey(KeyEvent.KEYCODE_SHIFT_LEFT, kRun, dy < -runAt);
        } else if (role[id] == ROLE_LOOK) {
            float dx = x - lastX;
            float dy = y - lastY;
            lastX = x;
            lastY = y;
            sendLook(dx, dy);
        } else if (role[id] == ROLE_BUTTON) {
            Btn b = heldButton[id];
            if (b != null && b.look) {
                float dx = x - btnLastX[id];
                float dy = y - btnLastY[id];
                btnLastX[id] = x;
                btnLastY[id] = y;
                sendLook(dx, dy);
            }
        }
    }

    private void sendLook(float dx, float dy) {
        if (dx == 0f && dy == 0f) return;
        float sy = invertLook ? -lookSpeed : lookSpeed;
        SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE,
                dx * lookSpeed, dy * sy, true);
    }

    private void pointerUp(int id) {
        if (id < 0 || id >= MAX_POINTERS) return;
        int r = role[id];
        if (r == ROLE_BUTTON) {
            Btn b = heldButton[id];
            if (b != null) {
                b.pressed = false;
                keyUp(b.key);
            }
            heldButton[id] = null;
        } else if (r == ROLE_STICK) {
            stickId = -1;
            kUp = setKey(KeyEvent.KEYCODE_DPAD_UP, kUp, false);
            kDown = setKey(KeyEvent.KEYCODE_DPAD_DOWN, kDown, false);
            kLeft = setKey(KeyEvent.KEYCODE_DPAD_LEFT, kLeft, false);
            kRight = setKey(KeyEvent.KEYCODE_DPAD_RIGHT, kRight, false);
            kRun = setKey(KeyEvent.KEYCODE_SHIFT_LEFT, kRun, false);
            stickX = defStickX;
            stickY = defStickY;
            stickCurX = stickX;
            stickCurY = stickY;
        } else if (r == ROLE_LOOK) {
            lookId = -1;
        }
        role[id] = ROLE_NONE;
    }

    private void panelTap(float x, float y) {
        if (lookMinus.contains(x, y)) {
            lookSpeed = Math.max(0.2f, Math.round((lookSpeed - 0.2f) * 10f) / 10f);
        } else if (lookPlus.contains(x, y)) {
            lookSpeed = Math.min(4.0f, Math.round((lookSpeed + 0.2f) * 10f) / 10f);
        } else if (runMinus.contains(x, y)) {
            runThreshold = Math.max(0.30f, Math.round((runThreshold - 0.05f) * 100f) / 100f);
        } else if (runPlus.contains(x, y)) {
            runThreshold = Math.min(0.95f, Math.round((runThreshold + 0.05f) * 100f) / 100f);
        } else if (invertBox.contains(x, y)) {
            invertLook = !invertLook;
        } else if (closeBox.contains(x, y) || !panel.contains(x, y)) {
            panelOpen = false;
        }
        saveSettings();
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas c) {
        // movement stick
        fillPaint.setColor(stickId >= 0 ? 0x33FFFFFF : 0x1AFFFFFF);
        c.drawCircle(stickX, stickY, stickRadius, fillPaint);
        c.drawCircle(stickX, stickY, stickRadius, ringPaint);
        float kx = stickCurX - stickX;
        float ky = stickCurY - stickY;
        float len = (float) Math.sqrt(kx * kx + ky * ky);
        if (len > stickRadius) {
            kx = kx * stickRadius / len;
            ky = ky * stickRadius / len;
        }
        fillPaint.setColor(kRun ? 0x99FFFFFF : 0x66FFFFFF);
        c.drawCircle(stickX + kx, stickY + ky, stickRadius * 0.4f, fillPaint);

        // buttons
        for (Btn b : buttons) {
            fillPaint.setColor(b.pressed ? 0x77FFFFFF : 0x33FFFFFF);
            c.drawCircle(b.x, b.y, b.r, fillPaint);
            c.drawCircle(b.x, b.y, b.r, ringPaint);
            c.drawText(b.label, b.x, b.y + textPaint.getTextSize() * 0.35f, textPaint);
        }

        if (panelOpen) drawPanel(c);
    }

    private void drawPanel(Canvas c) {
        fillPaint.setColor(0xEE101010);
        c.drawRoundRect(panel, 30f, 30f, fillPaint);
        c.drawRoundRect(panel, 30f, 30f, ringPaint);

        float left = panel.left + 0.05f * panel.width();
        float off = leftText.getTextSize() * 0.35f;

        c.drawText("Settings", panel.centerX(), titleY + off, textPaint);

        c.drawText(String.format(Locale.US, "Look speed: %.1fx", lookSpeed),
                left, rowLookY + off, leftText);
        drawSmallButton(c, lookMinus, "-");
        drawSmallButton(c, lookPlus, "+");

        c.drawText(String.format(Locale.US, "Run when pushed: %d%%", Math.round(runThreshold * 100f)),
                left, rowRunY + off, leftText);
        drawSmallButton(c, runMinus, "-");
        drawSmallButton(c, runPlus, "+");

        c.drawText("Invert look up/down", left, rowInvertY + off, leftText);
        drawSmallButton(c, invertBox, invertLook ? "ON" : "OFF");

        drawSmallButton(c, closeBox, "CLOSE");
    }

    private void drawSmallButton(Canvas c, RectF r, String label) {
        fillPaint.setColor(0x44FFFFFF);
        c.drawRoundRect(r, 16f, 16f, fillPaint);
        c.drawRoundRect(r, 16f, 16f, ringPaint);
        c.drawText(label, r.centerX(), r.centerY() + textPaint.getTextSize() * 0.35f, textPaint);
    }
}
