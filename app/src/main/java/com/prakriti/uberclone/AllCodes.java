package com.prakriti.uberclone;

import android.widget.EditText;

public class AllCodes {

    // check for empty fields submitted
    public static boolean isFieldNull(EditText field) {
        if (field.getText().toString().trim().equalsIgnoreCase("")) {
            field.setError("This field cannot be blank");
            field.requestFocus();
            return true;
        }
        return false;
        // equals() compares contents, == compares objects
    }
}
