package com.prakriti.uberclone;

import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.util.ArrayList;
import java.util.List;

public class ViewLocation extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Button btnAcceptRequest;
    private String requestedPassengerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_location);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map2);
        mapFragment.getMapAsync(this);

        requestedPassengerName = getIntent().getStringExtra("passUsername");

        btnAcceptRequest = findViewById(R.id.btnAcceptRequest);
        btnAcceptRequest.setText("Accept " + requestedPassengerName + "\'s Ride Request");

        btnAcceptRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                assignDriverToPassengerAndViewMap();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        Toast.makeText(this, R.string.view_loc_map, Toast.LENGTH_SHORT).show();
        // get location from passed intents
        LatLng driver = new LatLng(getIntent().getDoubleExtra("driverLat", 0),
                getIntent().getDoubleExtra("driverLong", 0));
        LatLng passenger = new LatLng(getIntent().getDoubleExtra("passLat", 0),
                getIntent().getDoubleExtra("passLong", 0));

        // to view all markers on the map
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        // arraylist of markers
        Marker driverMarker = mMap.addMarker(new MarkerOptions().position(driver).title("Driver: You")); // addMarker() returns type Marker
        Marker passengerMarker = mMap.addMarker(new MarkerOptions().position(passenger).title("Passenger"));
        ArrayList<Marker> markerList = new ArrayList<>();
        markerList.add(driverMarker);
        markerList.add(passengerMarker);

        // iterate over the markers
        for(Marker m : markerList) {
            builder.include(m.getPosition()); // returns LatLng
            // this way the markers will be included on the map
        }
        LatLngBounds bounds = builder.build();

        // adjust camera to show both locations
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 80); // int value is the offset (px)
        mMap.animateCamera(cameraUpdate);
    }


    private void assignDriverToPassengerAndViewMap() {
        Toast.makeText(ViewLocation.this, "Accepting " + requestedPassengerName + "\'s request...", Toast.LENGTH_SHORT).show();
        // assign driver to passenger
        ParseQuery<ParseObject> request = ParseQuery.getQuery("RequestRide");
        request.whereEqualTo("username", requestedPassengerName);

        request.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> objects, ParseException e) {
                if(objects.size()>0 && e == null) {
                    // create another column with assigned driver
                    for(ParseObject obj : objects) {
                        obj.put("requestAccepted", true);
                        obj.put("assignedDriver", ParseUser.getCurrentUser().getUsername());

                        obj.saveInBackground(new SaveCallback() {
                            @Override
                            public void done(ParseException e) {
                                if(e == null) {
                                    // allow driver to navigate to passenger location
                                    // create intent to open google maps -> pass url with lat & long info
                                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://maps.google.com/maps?saddr="
                                            + getIntent().getDoubleExtra("driverLat", 0) + ","
                                            + getIntent().getDoubleExtra("driverLong", 0) + "&daddr="
                                            + getIntent().getDoubleExtra("passLat", 0) + ","
                                            + getIntent().getDoubleExtra("passLong", 0)));
                                    startActivity(mapIntent);
                                }
                                else {
                                    Toast.makeText(ViewLocation.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                }
                else {
                    Toast.makeText(ViewLocation.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}