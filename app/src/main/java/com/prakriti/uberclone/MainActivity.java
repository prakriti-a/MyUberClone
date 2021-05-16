package com.prakriti.uberclone;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import com.parse.LogInCallback;
import com.parse.ParseAnonymousUtils;
import com.parse.ParseException;
import com.parse.ParseInstallation;
import com.parse.ParseUser;
import com.parse.SaveCallback;
import com.parse.SignUpCallback;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
// also ask for runtime permissions to transition to map activity

    enum State { SIGNUP, LOGIN }
    private State state;

    private EditText edtSignUpNumber, edtSignUpName;
    private Button btnSignUp, btnOneTimeLogin;
    private RadioButton radio_passenger, radio_driver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        state = State.SIGNUP; // initial state

        edtSignUpNumber = findViewById(R.id.edtSignUpNumber);
        edtSignUpName = findViewById(R.id.edtSignUpName);

        btnSignUp = findViewById(R.id.btnSignUp);
        btnOneTimeLogin = findViewById(R.id.btnOneTimeLogin);

        radio_passenger = findViewById(R.id.radio_passenger);
        radio_driver = findViewById(R.id.radio_driver);

        btnSignUp.setOnClickListener(this);
        btnOneTimeLogin.setOnClickListener(this);

        // ParseInstallation.getCurrentInstallation().saveInBackground();
        if(ParseUser.getCurrentUser() != null) { // transition to passenger activity
            transitionToPassengerOrDriverActivity();
            // finish();
            // if finish() is called here, create return intents for all logout buttons on passenger & driver activities
        }
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnSignUp:
                signUpNewUser();
                break;

            case R.id.btnOneTimeLogin: // anonymous Parse User without username / password
                createAnonymousUser();
                break;
        }
    }


    private void signUpNewUser() {
        if(AllCodes.isFieldNull(edtSignUpName) || AllCodes.isFieldNull(edtSignUpNumber)) {
            return;
        }
        else {
            try {
                String userName = edtSignUpName.getText().toString().trim();
                String userNumber = edtSignUpNumber.getText().toString().trim();

                // SIGN UP new user
                if(state == State.SIGNUP) {
                    if(radio_driver.isChecked() == false && radio_passenger.isChecked() == false) {
                        Toast.makeText(this, "Please specify either Passenger or Driver", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ParseUser appUser = new ParseUser();
                    appUser.setUsername(userName);
                    appUser.setPassword(userNumber);

                    if(radio_driver.isChecked()) {
                        appUser.put("as", "driver");
                    }
                    else if(radio_passenger.isChecked()) {
                        appUser.put("as", "passenger");
                    }

                    appUser.signUpInBackground(new SignUpCallback() {
                        @Override
                        public void done(ParseException e) {
                            if(e == null) {
                                Toast.makeText(MainActivity.this, "Welcome " + userName +"!", Toast.LENGTH_SHORT).show();
                                edtSignUpName.setText("");
                                edtSignUpNumber.setText("");
                                radio_driver.setChecked(false);
                                radio_passenger.setChecked(false);
                                // transition to another activity
                                transitionToPassengerOrDriverActivity();
                                finish();
                            }
                            else {
                                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                e.printStackTrace();
                            }
                        }
                    });
                }
                // till here is SIGN UP state

                // LOG IN existing user
                else if(state == State.LOGIN) {
                    ParseUser.logInInBackground(userName, userNumber, new LogInCallback() {
                        @Override
                        public void done(ParseUser user, ParseException e) {
                            if (e == null && user!=null) {
                                Toast.makeText(MainActivity.this, "Welcome " + userName +"!", Toast.LENGTH_SHORT).show();
                                edtSignUpName.setText("");
                                edtSignUpNumber.setText("");
                                radio_driver.setChecked(false);
                                radio_passenger.setChecked(false);

                                transitionToPassengerOrDriverActivity();
                                finish();
                            }
                            else {
                                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
            catch (Exception e) {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    private void createAnonymousUser() {
        try {
            if (radio_driver.isChecked() == false && radio_passenger.isChecked() == false) {
                Toast.makeText(this, "Please specify either Passenger or Driver", Toast.LENGTH_SHORT).show();
            } else {
                if(ParseUser.getCurrentUser() != null) {
                    Toast.makeText(this, "User is currently logged in", Toast.LENGTH_SHORT).show();
                }
                if (ParseUser.getCurrentUser() == null) {
                    ParseAnonymousUtils.logIn(new LogInCallback() {
                        @Override
                        public void done(ParseUser user, ParseException e) {
                            if (e == null && user != null) {
                                if (radio_driver.isChecked()) {
                                    user.put("as", "driver");
                                } else if (radio_passenger.isChecked()) {
                                    user.put("as", "passenger");
                                }
                                Toast.makeText(MainActivity.this, "Anonymous User created", Toast.LENGTH_SHORT).show();

                                user.saveInBackground(new SaveCallback() { // must call this
                                    @Override
                                    public void done(ParseException e) {
                                        transitionToPassengerOrDriverActivity();
                                        finish();
                                    }
                                });
                            } else {
                                Toast.makeText(MainActivity.this, "Error occured\nPlease try again", Toast.LENGTH_SHORT).show();
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void transitionToPassengerOrDriverActivity() {
        if(ParseUser.getCurrentUser() != null) {
            if(ParseUser.getCurrentUser().getString("as").equals("passenger")) {
                startActivity(new Intent(MainActivity.this, PassengerActivity.class));
                finish();
            }
            //finish();
            else if(ParseUser.getCurrentUser().getString("as").equals("driver")) {
                startActivity(new Intent(MainActivity.this, DriverRequestList.class));
                finish();
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.my_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_logIn:
                if(state == State.SIGNUP) { // change to LOGIN state
                    state = State.LOGIN;
                    item.setTitle(R.string.signUp);
                    btnSignUp.setText(R.string.login);
                }
                else if(state == State.LOGIN) { // change to SIGNUP state
                    state = State.SIGNUP;
                    item.setTitle(R.string.login);
                    btnSignUp.setText(R.string.signUp);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void hideKeyboard(View view) {
        try {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            view.clearFocus();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}