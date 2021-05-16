package com.prakriti.uberclone;

import android.app.Application;

import com.parse.Parse;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Parse.initialize(new Parse.Configuration.Builder(this)
                .applicationId("RVSfYcPCLFCzIN7slPviyIPQQckcjihk7B0bFBiP")
                .clientKey("l7F21wLwh9ndDTxNMAvUvYtB4NnWFLOU0hfZxZLw")
                .server("https://parseapi.back4app.com/")
                .build()
        );
    }
}
