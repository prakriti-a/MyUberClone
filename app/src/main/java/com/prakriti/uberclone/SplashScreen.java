package com.prakriti.uberclone;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class SplashScreen extends Activity {

    private static boolean splashLoaded = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(!splashLoaded) {
            setContentView(R.layout.activity_splash_screen);
            int secondsDelayed = 1;
            // Handler is used to create new thread in background, for heavy ops (not on UI thread)
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() { // run() called on UI thread
                    startActivity(new Intent(SplashScreen.this, MainActivity.class));
                    finish(); // destroy current activity from activity stack
                }
            }, secondsDelayed * 1000); // perform the run() tasks after secondsDelayed in milliseconds
            // here, one second
            splashLoaded = true;
        }
        else { // when Splash is loaded
            Intent goToMainActivity = new Intent(SplashScreen.this, MainActivity.class);
            // flag brings instance of MainActivity to the front of the stack
            goToMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(goToMainActivity);
            finish();
        }
    }
}