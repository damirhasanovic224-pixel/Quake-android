package com.example.quake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

/** Transparent on-screen controls drawn on top of the game. */
public class TouchOverlay extends View {

    private static final float LOOK_SPEED = 1.3f;
    private static final int MAX_POINTERS = 32;

    private static final int ROLE_NONE = 0;
    private static final int ROLE_STICK = 1;
    private static final int ROLE_LOOK = 2;
    private static final int ROLE_BUTTON = 3;

    private static class Btn {
        final String label;
        final int key;
        float x, y, r;
        boolean pressed;
        Btn(String label, int key) {
            this.label = label;
            this.key = key;
        }
    }

    private final Btn fire = new Btn("FIRE", KeyEvent.KEYCODE_ENTER);
    private final Btn jump = new Btn("JUMP", KeyEvent.KEYCODE_SPACE);
    private final Btn weapon = new Btn("WPN", KeyEvent.KEYCODE_E);
    private final Btn menu = new Btn("MENU", KeyEvent.KEYCODE_ESCAPE);
    private final Btn[] buttons = { fire, jump, weapon, menu };

    private final int[] role = new int[MAX_POINTERS];
    private final Btn[] heldButton = new Btn[MAX_POINTERS];

    private int stickId = -1;
    private float defStickX, defStickY;
    private float stickX, stickY, stickCurX, stickCurY, stickRadius;
    private boolean kUp, kDown, kLeft, kRight;

    private int lookId = -1;
    private float lastX, lastY;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TouchOverlay(Context context) {
        super(context);
        setWillNotDraw(false);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(4f);
        ringPaint.setColor(0x88FFFFFF);
        textPaint.setColor(0xCCFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float r = 0.11f * Math.min(w, h);
        fire.r = r;     fire.x = w - 1.5f * r;   fire.y = h - 1.8f * r;
        jump.r = r;     jump.x = w - 4.0f * r;   jump.y = h - 1.2f * r;
        weapon.r = 0.8f * r; weapon.x = w - 1.5f * r; weapon.y = h - 4.4f * r;
        menu.r = 0.7f * r;   menu.x = w * 0.5f;  menu.y = menu.r * 1.3f;
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

    // ---- touch handling ----

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int idx = e.getActionIndex();
        switch (e.getActionMasked()) {
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
                for (int id = 0; id < MAX_POINTERS; id++) pointerUp(id);
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    private void pointerDown(int id, float x, float y) {
        if (id < 0 || id >= MAX_POINTERS) return;
        for (Btn b : buttons) {
            float dx = x - b.x;
            float dy = y - b.y;
            if (dx * dx + dy * dy <= b.r * b.r * 1.4f) {
                b.pressed = true;
                heldButton[id] = b;
                role[id] = ROLE_BUTTON;
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
            float t = stickRadius * 0.3f;
            kUp = setKey(KeyEvent.KEYCODE_DPAD_UP, kUp, dy < -t);
            kDown = setKey(KeyEvent.KEYCODE_DPAD_DOWN, kDown, dy > t);
            kLeft = setKey(KeyEvent.KEYCODE_DPAD_LEFT, kLeft, dx < -t);
            kRight = setKey(KeyEvent.KEYCODE_DPAD_RIGHT, kRight, dx > t);
        } else if (role[id] == ROLE_LOOK) {
            float dx = x - lastX;
            float dy = y - lastY;
            lastX = x;
            lastY = y;
            if (dx != 0f || dy != 0f) {
                SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE,
                        dx * LOOK_SPEED, dy * LOOK_SPEED, true);
            }
        }
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
            stickX = defStickX;
            stickY = defStickY;
            stickCurX = stickX;
            stickCurY = stickY;
        } else if (r == ROLE_LOOK) {
            lookId = -1;
        }
        role[id] = ROLE_NONE;
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
        fillPaint.setColor(0x66FFFFFF);
        c.drawCircle(stickX + kx, stickY + ky, stickRadius * 0.4f, fillPaint);

        // buttons
        for (Btn b : buttons) {
            fillPaint.setColor(b.pressed ? 0x77FFFFFF : 0x33FFFFFF);
            c.drawCircle(b.x, b.y, b.r, fillPaint);
            c.drawCircle(b.x, b.y, b.r, ringPaint);
            c.drawText(b.label, b.x, b.y + textPaint.getTextSize() * 0.35f, textPaint);
        }
    }
}
