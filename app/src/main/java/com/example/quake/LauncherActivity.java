package com.example.quake;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

/** Checks "All files access" (needed to read pak0.pak), then starts the game. */
public class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Environment.isExternalStorageManager()) {
            startActivity(new Intent(this, QuakeActivity.class));
        } else {
            Toast.makeText(this,
                    "Turn on \"Allow access to manage all files\", then open Quake again",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
        finish();
    }
}
