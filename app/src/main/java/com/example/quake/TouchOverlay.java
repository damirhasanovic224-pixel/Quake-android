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

    private static final float DEF_STICK_X = 0.13f;
    private static final float DEF_STICK_Y = 0.72f;

    private static class Btn {
        final String id;
        final String label;
        final int key;          // 0 = not a key button
        final boolean look;     // sliding a finger that started here also aims
        final float relR;       // size relative to the base button size
        final float defX, defY; // default position, as a fraction of the screen
        float fx, fy;           // current position, as a fraction of the screen
        float x, y, r;          // current position and size in pixels
        boolean pressed;
        Btn(String id, String label, int key, boolean look, float relR, float defX, float defY) {
            this.id = id;
            this.label = label;
            this.key = key;
            this.look = look;
            this.relR = relR;
            this.defX = defX;
            this.defY = defY;
            this.fx = defX;
            this.fy = defY;
        }
    }

    private final Btn fire = new Btn("fire", "FIRE", KeyEvent.KEYCODE_ENTER, true, 1.0f, 0.92f, 0.80f);
    private final Btn jump = new Btn("jump", "JUMP", KeyEvent.KEYCODE_SPACE, true, 1.0f, 0.80f, 0.87f);
    private final Btn weapon = new Btn("wpn", "WPN", KeyEvent.KEYCODE_E, false, 0.8f, 0.92f, 0.52f);
    private final Btn menu = new Btn("menu", "MENU", KeyEvent.KEYCODE_ESCAPE, false, 0.7f, 0.50f, 0.10f);
    private final Btn settingsBtn = new Btn("set", "SET", 0, false, 0.6f, 0.07f, 0.13f);
    private final Btn[] buttons = { fire, jump, weapon, menu, settingsBtn };

    private final int[] role = new int[MAX_POINTERS];
    private final Btn[] heldButton = new Btn[MAX_POINTERS];
    private final float[] btnLastX = new float[MAX_POINTERS];
    private final float[] btnLastY = new float[MAX_POINTERS];

    // movement stick
    private int stickId = -1;
    private float stickFx = DEF_STICK_X;
    private float stickFy = DEF_STICK_Y;
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
    private float sizeScale;        // size of all controls
    private boolean invertLook;

    // screens drawn on top of the game
    private boolean panelOpen = false;
    private boolean editMode = false;

    // settings panel
    private final RectF panel = new RectF();
    private final RectF lookMinus = new RectF(), lookPlus = new RectF();
    private final RectF runMinus = new RectF(), runPlus = new RectF();
    private final RectF sizeMinus = new RectF(), sizePlus = new RectF();
    private final RectF invertBox = new RectF();
    private final RectF moveBox = new RectF(), closeBox = new RectF();
    private float titleY, rowLookY, rowRunY, rowSizeY, rowInvertY;

    // layout editor
    private final RectF editDone = new RectF(), editReset = new RectF();
    private int editId = -1;
    private Btn editBtn = null;
    private boolean editStick = false;
    private float editOffX, editOffY;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelLeft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelCenter = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TouchOverlay(Context context) {
        super(context);
        setWillNotDraw(false);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(4f);
        ringPaint.setColor(0x88FFFFFF);
        textPaint.setColor(0xDDFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        panelLeft.setColor(0xFFFFFFFF);
        panelLeft.setTextAlign(Paint.Align.LEFT);
        panelCenter.setColor(0xFFFFFFFF);
        panelCenter.setTextAlign(Paint.Align.CENTER);

        prefs = context.getSharedPreferences("controls", Context.MODE_PRIVATE);
        lookSpeed = prefs.getFloat("look", 1.3f);
        runThreshold = prefs.getFloat("run", 0.55f);
        invertLook = prefs.getBoolean("invert", false);
        sizeScale = prefs.getFloat("size", 1.0f);
        for (Btn b : buttons) {
            b.fx = prefs.getFloat("fx_" + b.id, b.defX);
            b.fy = prefs.getFloat("fy_" + b.id, b.defY);
        }
        stickFx = prefs.getFloat("fx_stick", DEF_STICK_X);
        stickFy = prefs.getFloat("fy_stick", DEF_STICK_Y);
    }

    // ---- layout ----

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layout();
    }

    private void layout() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float m = Math.min(w, h);
        float base = 0.11f * m * sizeScale;

        for (Btn b : buttons) {
            b.r = b.relR * base;
            b.x = b.fx * w;
            b.y = b.fy * h;
        }
        stickRadius = 1.5f * base;
        defStickX = stickFx * w;
        defStickY = stickFy * h;
        if (stickId < 0) {
            stickX = defStickX;
            stickY = defStickY;
            stickCurX = stickX;
            stickCurY = stickY;
        }
        textPaint.setTextSize(0.45f * base);
        panelLeft.setTextSize(0.048f * m);
        panelCenter.setTextSize(0.048f * m);

        // settings panel
        panel.set(0.18f * w, 0.08f * h, 0.82f * w, 0.92f * h);
        float pw = panel.width();
        float ph = panel.height();
        float bs = 0.10f * h;
        titleY = panel.top + 0.07f * ph;
        rowLookY = panel.top + 0.22f * ph;
        rowRunY = panel.top + 0.37f * ph;
        rowSizeY = panel.top + 0.52f * ph;
        rowInvertY = panel.top + 0.67f * ph;
        float plusCx = panel.right - 0.07f * pw - bs / 2f;
        float minusCx = plusCx - bs * 1.5f;
        setRect(lookPlus, plusCx, rowLookY, bs);
        setRect(lookMinus, minusCx, rowLookY, bs);
        setRect(runPlus, plusCx, rowRunY, bs);
        setRect(runMinus, minusCx, rowRunY, bs);
        setRect(sizePlus, plusCx, rowSizeY, bs);
        setRect(sizeMinus, minusCx, rowSizeY, bs);
        setRect(invertBox, plusCx, rowInvertY, bs);
        float bottomY = panel.top + 0.88f * ph;
        moveBox.set(panel.left + 0.05f * pw, bottomY - bs / 2f,
                    panel.left + 0.58f * pw, bottomY + bs / 2f);
        closeBox.set(panel.left + 0.64f * pw, bottomY - bs / 2f,
                     panel.left + 0.95f * pw, bottomY + bs / 2f);

        // layout editor buttons (screen centre)
        float bw = 0.16f * w;
        float bh = 0.10f * h;
        editDone.set(0.49f * w - bw, 0.5f * h - bh / 2f, 0.49f * w, 0.5f * h + bh / 2f);
        editReset.set(0.51f * w, 0.5f * h - bh / 2f, 0.51f * w + bw, 0.5f * h + bh / 2f);
    }

    private static void setRect(RectF rect, float cx, float cy, float size) {
        rect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
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

    private void sendLook(float dx, float dy) {
        if (dx == 0f && dy == 0f) return;
        float sy = invertLook ? -lookSpeed : lookSpeed;
        SDLActivity.onNativeMouse(0, MotionEvent.ACTION_MOVE,
                dx * lookSpeed, dy * sy, true);
    }

    // ---- saving ----

    private void saveAll() {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putFloat("look", lookSpeed);
        ed.putFloat("run", runThreshold);
        ed.putBoolean("invert", invertLook);
        ed.putFloat("size", sizeScale);
        for (Btn b : buttons) {
            ed.putFloat("fx_" + b.id, b.fx);
            ed.putFloat("fy_" + b.id, b.fy);
        }
        ed.putFloat("fx_stick", stickFx);
        ed.putFloat("fy_stick", stickFy);
        ed.apply();
    }

    // ---- touch handling ----

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (editMode) {
            editTouch(e);
            return true;
        }

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

    // ---- settings panel ----

    private void panelTap(float x, float y) {
        if (lookMinus.contains(x, y)) {
            lookSpeed = Math.max(0.2f, Math.round((lookSpeed - 0.2f) * 10f) / 10f);
        } else if (lookPlus.contains(x, y)) {
            lookSpeed = Math.min(4.0f, Math.round((lookSpeed + 0.2f) * 10f) / 10f);
        } else if (runMinus.contains(x, y)) {
            runThreshold = Math.max(0.30f, Math.round((runThreshold - 0.05f) * 100f) / 100f);
        } else if (runPlus.contains(x, y)) {
            runThreshold = Math.min(0.95f, Math.round((runThreshold + 0.05f) * 100f) / 100f);
        } else if (sizeMinus.contains(x, y)) {
            sizeScale = Math.max(0.6f, Math.round((sizeScale - 0.1f) * 10f) / 10f);
            layout();
        } else if (sizePlus.contains(x, y)) {
            sizeScale = Math.min(1.6f, Math.round((sizeScale + 0.1f) * 10f) / 10f);
            layout();
        } else if (invertBox.contains(x, y)) {
            invertLook = !invertLook;
        } else if (moveBox.contains(x, y)) {
            panelOpen = false;
            editMode = true;
        } else if (closeBox.contains(x, y) || !panel.contains(x, y)) {
            panelOpen = false;
        }
        saveAll();
    }

    // ---- layout editor ----

    private void editTouch(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float x = e.getX();
                float y = e.getY();
                if (editDone.contains(x, y)) {
                    editMode = false;
                    panelOpen = true;
                } else if (editReset.contains(x, y)) {
                    resetLayout();
                } else {
                    startEditDrag(e.getPointerId(0), x, y);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (editId >= 0) {
                    int i = e.findPointerIndex(editId);
                    if (i >= 0) moveEditDrag(e.getX(i), e.getY(i));
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (editId >= 0) {
                    editId = -1;
                    editBtn = null;
                    editStick = false;
                    saveAll();
                }
                break;
            default:
                break;
        }
        invalidate();
    }

    private void startEditDrag(int id, float x, float y) {
        for (Btn b : buttons) {
            float bx = x - b.x;
            float by = y - b.y;
            if (bx * bx + by * by <= b.r * b.r * 1.5f) {
                editId = id;
                editBtn = b;
                editStick = false;
                editOffX = b.x - x;
                editOffY = b.y - y;
                return;
            }
        }
        float sx = x - defStickX;
        float sy = y - defStickY;
        if (sx * sx + sy * sy <= stickRadius * stickRadius) {
            editId = id;
            editBtn = null;
            editStick = true;
            editOffX = defStickX - x;
            editOffY = defStickY - y;
        }
    }

    private void moveEditDrag(float x, float y) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float nx = clamp((x + editOffX) / w, 0.04f, 0.96f);
        float ny = clamp((y + editOffY) / h, 0.06f, 0.94f);
        if (editBtn != null) {
            editBtn.fx = nx;
            editBtn.fy = ny;
        } else if (editStick) {
            stickFx = nx;
            stickFy = ny;
        }
        layout();
    }

    private void resetLayout() {
        for (Btn b : buttons) {
            b.fx = b.defX;
            b.fy = b.defY;
        }
        stickFx = DEF_STICK_X;
        stickFy = DEF_STICK_Y;
        sizeScale = 1.0f;
        layout();
        saveAll();
    }

    // ---- drawing ----

    @Override
    protected void onDraw(Canvas c) {
        if (editMode) c.drawColor(0x66000000);

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

        if (editMode) {
            c.drawText("Drag the controls where you want them",
                    getWidth() * 0.5f, getHeight() * 0.38f, panelCenter);
            drawSmallButton(c, editDone, "DONE");
            drawSmallButton(c, editReset, "RESET");
        }
        if (panelOpen) drawPanel(c);
    }

    private void drawPanel(Canvas c) {
        fillPaint.setColor(0xEE101010);
        c.drawRoundRect(panel, 30f, 30f, fillPaint);
        c.drawRoundRect(panel, 30f, 30f, ringPaint);

        float left = panel.left + 0.05f * panel.width();
        float off = panelLeft.getTextSize() * 0.35f;

        c.drawText("Settings", panel.centerX(), titleY + off, panelCenter);

        c.drawText(String.format(Locale.US, "Look speed: %.1fx", lookSpeed),
                left, rowLookY + off, panelLeft);
        drawSmallButton(c, lookMinus, "-");
        drawSmallButton(c, lookPlus, "+");

        c.drawText(String.format(Locale.US, "Run when pushed: %d%%", Math.round(runThreshold * 100f)),
                left, rowRunY + off, panelLeft);
        drawSmallButton(c, runMinus, "-");
        drawSmallButton(c, runPlus, "+");

        c.drawText(String.format(Locale.US, "Control size: %d%%", Math.round(sizeScale * 100f)),
                left, rowSizeY + off, panelLeft);
        drawSmallButton(c, sizeMinus, "-");
        drawSmallButton(c, sizePlus, "+");

        c.drawText("Invert look up/down", left, rowInvertY + off, panelLeft);
        drawSmallButton(c, invertBox, invertLook ? "ON" : "OFF");

        drawSmallButton(c, moveBox, "MOVE CONTROLS");
        drawSmallButton(c, closeBox, "CLOSE");
    }

    private void drawSmallButton(Canvas c, RectF r, String label) {
        fillPaint.setColor(0x44FFFFFF);
        c.drawRoundRect(r, 16f, 16f, fillPaint);
        c.drawRoundRect(r, 16f, 16f, ringPaint);
        c.drawText(label, r.centerX(), r.centerY() + panelCenter.getTextSize() * 0.35f, panelCenter);
    }
}
