package com.example.quake;

import android.graphics.Rect;
import android.os.Bundle;
import android.system.Os;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;

import org.libsdl.app.SDLActivity;

public class QuakeActivity extends SDLActivity {

    /** Game files go in: /storage/emulated/0/Quake/id1/pak0.pak */
    static final String BASE_DIR = "/storage/emulated/0/Quake";

    private int screenW = 1920;
    private int screenH = 1080;

    @Override
    public void loadLibraries() {
        applyGl4esEnv();   // must happen before libGL (gl4es) is loaded
        // hidapi only exists in some SDL2 builds, so don't fail if it's missing
        try {
            System.loadLibrary("hidapi");
        } catch (UnsatisfiedLinkError ignored) {
        }
        System.loadLibrary("SDL2");
        System.loadLibrary("GL");     // gl4es (desktop GL -> GLES)
        System.loadLibrary("main");   // our Quake engine
    }

    /** Reads Quake/gl4es_env.txt (one KEY=VALUE per line) and sets those variables for gl4es. */
    private void applyGl4esEnv() {
        try {
            File f = new File(BASE_DIR, "gl4es_env.txt");
            if (!f.exists()) {
                new File(BASE_DIR).mkdirs();
                FileOutputStream out = new FileOutputStream(f);
                out.write(("# gl4es settings, one KEY=VALUE per line. Restart the game after editing.\n"
                         + "LIBGL_BEGINEND=0\n").getBytes("UTF-8"));
                out.close();
            }
            BufferedReader in = new BufferedReader(new FileReader(f));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                Os.setenv(line.substring(0, eq).trim(), line.substring(eq + 1).trim(), true);
            }
            in.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // real screen size, so Quake renders at full resolution
        Rect bounds = getWindowManager().getMaximumWindowMetrics().getBounds();
        screenW = Math.max(bounds.width(), bounds.height());
        screenH = Math.min(bounds.width(), bounds.height());

        // draw under the camera cut-out too
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setAttributes(lp);

        writeAutoexec();
        super.onCreate(savedInstanceState);

        addContentView(new TouchOverlay(this),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                           ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    @Override
    protected String[] getArguments() {
        return new String[] {
            "-basedir", BASE_DIR,
            "-condebug",
            "-fullscreen",
            "-width", String.valueOf(screenW),
            "-height", String.valueOf(screenH)
        };
    }

    /** Key bindings + bigger menus for the touch controls. Rewritten at every start. */
    private void writeAutoexec() {
        try {
            File dir = new File(BASE_DIR, "id1");
            dir.mkdirs();
            String cfg =
                "// Written by the Android port every time the game starts\n"
              + "bind UPARROW \"+forward\"\n"
              + "bind DOWNARROW \"+back\"\n"
              + "bind LEFTARROW \"+moveleft\"\n"
              + "bind RIGHTARROW \"+moveright\"\n"
              + "bind ENTER \"+attack\"\n"
              + "bind SPACE \"+jump\"\n"
              + "bind e \"impulse 10\"\n"
              + "bind SHIFT \"+speed\"\n"
              + "scr_menuscale 3\n"
              + "scr_sbarscale 3\n"
              + "scr_conscale 3\n"
              + "crosshair 1\n"
              + "scr_showfps 1\n";
            FileOutputStream out = new FileOutputStream(new File(dir, "autoexec.cfg"));
            out.write(cfg.getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
        }
    }
}
