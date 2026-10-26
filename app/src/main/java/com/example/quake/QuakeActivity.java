package com.example.quake;

import org.libsdl.app.SDLActivity;

public class QuakeActivity extends SDLActivity {

    /** Game files go in: /storage/emulated/0/Quake/id1/pak0.pak */
    static final String BASE_DIR = "/storage/emulated/0/Quake";

    @Override
    protected void loadLibraries() {
        // hidapi only exists in some SDL2 builds, so don't fail if it's missing
        try {
            System.loadLibrary("hidapi");
        } catch (UnsatisfiedLinkError ignored) {
        }
        System.loadLibrary("SDL2");
        System.loadLibrary("main");   // our Quake engine; pulls in libGL (gl4es)
    }

    @Override
    protected String[] getArguments() {
        return new String[] { "-basedir", BASE_DIR, "-condebug" };
    }
}
