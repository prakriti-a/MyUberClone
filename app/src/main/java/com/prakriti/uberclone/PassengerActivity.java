package com.prakriti.uberclone;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.DeleteCallback;
import com.parse.FindCallback;
import com.parse.LogOutCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class PassengerActivity extends FragmentActivity implements OnMapReadyCallback, View.OnClickListener {

    private GoogleMap mMap;
    // we use GPS Provider for this app

    private LocationManager locationManager; // service
    private LocationListener locationListener; // interface to access passenger location

    private static final int PASSENGER_REQ_CODE = 3000;

    private Button btnPassengerRequest, btnLogoutPassenger, btnCheckForUpdates;
    private boolean isRideCancelled = true;
    private boolean isRideAccepted = false;

    private Timer updatesTimer; // so var is alive thru lifetime of program

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map1);
        mapFragment.getMapAsync(this);

        btnPassengerRequest = findViewById(R.id.btnPassengerRequest);
        btnPassengerRequest.setOnClickListener(this);

        btnLogoutPassenger = findViewById(R.id.btnLogoutPassenger);
        btnLogoutPassenger.setOnClickListener(this);

        btnCheckForUpdates = findViewById(R.id.btnCheckForUpdates);
        btnCheckForUpdates.setOnClickListener(this);

        // get ride requests of passenger from server
        ParseQuery<ParseObject> rideRequestQuery = ParseQuery.getQuery("RequestRide");
        rideRequestQuery.whereEqualTo("username", ParseUser.getCurrentUser().getUsername());

        rideRequestQuery.findInBackground(new FindCallback<ParseObject>() {
            @Override
            public void done(List<ParseObject> objects, ParseException e) {
                if(objects.size() > 0 && e == null) {
                    // valid requests exist
                    isRideCancelled = false;
                    btnPassengerRequest.setText(R.string.cancelRideRequest);
                    getDriverUpdates(); // show update when user logs in and active request is present
                }
            }
        });

    }

    //    Manipulates the map once available
//    This callback is triggered when the map is ready to be used
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        // anon inner class for listener
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                // called when passenger loc is changed
                updatePassengerCameraLocation(location);
            }
        };

        // add permission check
        if (Build.VERSION.SDK_INT < 23) { // marshmallow or below
            if (ContextCompat.checkSelfPermission(PassengerActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                // min time & min distance is 0, get loc regardless of change in time or distance
            }
        }

        else if (Build.VERSION.SDK_INT >= 23) { // runtime permission
            if (ContextCompat.checkSelfPermission(PassengerActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(PassengerActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PASSENGER_REQ_CODE);
            }
            else { // granted
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                Location nowLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                // updates
                updatePassengerCameraLocation(nowLocation);
            }
        }
    }

    // handle result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PASSENGER_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // check self permission
                if (ContextCompat.checkSelfPermission(PassengerActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

                    // pass updated loc & update on screen
                    Location currentPassLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    updatePassengerCameraLocation(currentPassLocation);
                }
            }
        }
    }

    // to update camera when user changes location
    private void updatePassengerCameraLocation(Location pLocation) {
        if(isRideAccepted == false) {
            LatLng passengerLocation = new LatLng(pLocation.getLatitude(), pLocation.getLongitude());
            mMap.clear(); // avoid multiple markers
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(passengerLocation, 14)); // show user on map & zoom in
            // add marker
            mMap.addMarker(new MarkerOptions().position(passengerLocation).title("You are here")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))); // changing marker color
            // this camera update conflicts with camera update on driver accepting request
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnPassengerRequest: // code for sending or canceling ride request
                userRideRequestAndCancellation();
                break;
            case R.id.btnCheckForUpdates:
                getDriverUpdates(); // check for updates when user taps button
                // edited to check for updates periodically using Timer
                break;
            case R.id.btnLogoutPassenger:
                logOutCurrentUserFromApp();
                break;
        }
    }

    private void logOutCurrentUserFromApp() {
        ParseUser.getCurrentUser().logOutInBackground(new LogOutCallback() {
            @Override
            public void done(ParseException e) {
                if(e == null) {
                    Toast.makeText(PassengerActivity.this, R.string.user_logout, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(PassengerActivity.this, MainActivity.class));
                    finish();
                }
                else {
                    Toast.makeText(PassengerActivity.this, R.string.unable_to_logout, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void userRideRequestAndCancellation() {
        // check if active request is not present
        if(isRideCancelled) { // user can make a request
            // check if we have permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                Location passCurrentLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (passCurrentLoc != null) { // request a ride
                    // create new Parse object
                    ParseObject requestRide = new ParseObject("RequestRide");

                    // send passenger info to driver
                    requestRide.put("username", ParseUser.getCurrentUser().getUsername());
                    // user location as ParseGeoPoint obj
                    ParseGeoPoint passLocation = new ParseGeoPoint(passCurrentLoc.getLatitude(), passCurrentLoc.getLongitude());
                    requestRide.put("passengerLocation", passLocation);

                    requestRide.saveInBackground(new SaveCallback() { // save object
                        @Override
                        public void done(ParseException e) {
                            if (e == null) {
                                Toast.makeText(PassengerActivity.this, R.string.ride_req_sent, Toast.LENGTH_SHORT).show();
                                // now give option to cancel request
                                btnPassengerRequest.setText(R.string.cancelRideRequest);
                                isRideCancelled = false; // since we have a ride request now
                                // call for timed driver updates since request is now saved on server
                                getDriverUpdates();
                            }
                            else {
                                Toast.makeText(PassengerActivity.this, R.string.error_occured, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } else {
                    Toast.makeText(this, R.string.unknown_error, Toast.LENGTH_SHORT).show();
                }
            }
        }
        else {
            // when active ride request is present -> user can now cancel requests
            // create a query for all requests by user & cancel them
            ParseQuery<ParseObject> userRideReqQuery = ParseQuery.getQuery("RequestRide");
            userRideReqQuery.whereEqualTo("username", ParseUser.getCurrentUser().getUsername());

            userRideReqQuery.findInBackground(new FindCallback<ParseObject>() {
                @Override
                public void done(List<ParseObject> requestList, ParseException e) {
                    if(requestList.size()>0 && e == null) {
                        isRideCancelled = true;
                        btnPassengerRequest.setText(R.string.btn_rideRequest);
                        for (ParseObject request : requestList) {
                            request.deleteInBackground(new DeleteCallback() {
                                @Override
                                public void done(ParseException e) {
                                    if(e == null) {
                                        Toast.makeText(PassengerActivity.this, R.string.ride_req_cancelled, Toast.LENGTH_SHORT).show();
                                    }
                                    else {
                                        Toast.makeText(PassengerActivity.this, R.string.error_occured, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    private void getDriverUpdates() {
        // initialise timer to run the updates every 10 seconds
        updatesTimer = new Timer();
        updatesTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // to update the passenger about driver info -> accepted req & distance from driver
                ParseQuery<ParseObject> uberRequest = ParseQuery.getQuery("RequestRide");
                // current passenger's accepted request
                uberRequest.whereEqualTo("username", ParseUser.getCurrentUser().getUsername());
                uberRequest.whereEqualTo("requestAccepted", true);
                uberRequest.whereExists("assignedDriver");

                uberRequest.findInBackground(new FindCallback<ParseObject>() {
                    @Override
                    public void done(List<ParseObject> objects, ParseException e) {
                        if(objects.size()>0 && e==null) {
                            // change camera update on map
                            isRideAccepted = true;

                            for(ParseObject obj : objects) {
                                String driverUsername = obj.getString("assignedDriver");
                                // get passenger's location from RequestRide class
                                ParseGeoPoint passLocation = obj.getParseGeoPoint("passengerLocation");

                                // create query to get driver location from User class
                                ParseQuery<ParseUser> driverQuery = ParseUser.getQuery();
                                driverQuery.whereEqualTo("username", obj.getString("assignedDriver"));

                                driverQuery.findInBackground(new FindCallback<ParseUser>() {
                                    @Override
                                    public void done(List<ParseUser> list, ParseException e) {
                                        if(list.size()>0 && e == null) {
                                            for(ParseUser item : list) {
                                                ParseGeoPoint assignedDriversLocation = item.getParseGeoPoint("driverLocation");
                                                // update location of driver to server so passenger will be periodically notified of driver distance
                                                // updateDriversLocationOnServer();

                                                // get distance between passenger & driver
                                                double distanceInKms = assignedDriversLocation.distanceInKilometersTo(passLocation);
                                                float roundedDistanceInKms = Math.round(distanceInKms * 10)/10;
                                                Toast.makeText(PassengerActivity.this, "Request Accepted!\n" + driverUsername + " is "
                                                        + roundedDistanceInKms + " km away", Toast.LENGTH_LONG).show();

                                                // when driver has reached the passenger, end all this
                                                if(roundedDistanceInKms < 0.01) {
                                                    // delete first request object 'obj' created in first query in this method
                                                    // aka current accepted request obj gotten from the server
                                                    // since request is fullfilled, it can be removed
                                                    obj.deleteInBackground(new DeleteCallback() {
                                                        @Override
                                                        public void done(ParseException e) {
                                                            if(e == null) {
                                                                // once obj is deleted, inform passenger
                                                                Toast.makeText(PassengerActivity.this, R.string.driver_arrived,
                                                                        Toast.LENGTH_SHORT).show();
                                                                // if driver has reached, map doesn't need to show both, but only passenger location
                                                                isRideAccepted = false;
                                                                isRideCancelled = true;
                                                                btnPassengerRequest.setText(R.string.get_another_ride);
                                                            }
                                                        }
                                                    });
                                                }

                                                else { // request is still active
                                                    // once request is accepted, show driver's location on map as well
                                                    // create latLng objects using location or ParseGeoPoint objects
                                                    LatLng driver = new LatLng(assignedDriversLocation.getLatitude(), assignedDriversLocation.getLongitude());
                                                    LatLng passenger = new LatLng(passLocation.getLatitude(), passLocation.getLongitude());

                                                    mMap.clear();
                                                    // to view all markers on the map
                                                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                                    // arraylist of markers
                                                    Marker driverMarker = mMap.addMarker(new MarkerOptions().position(driver).title("Driver"));
                                                    // addMarker() returns type Marker
                                                    Marker passengerMarker = mMap.addMarker(new MarkerOptions().position(passenger).title("Passenger: You")
                                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                                                    ArrayList<Marker> markerList = new ArrayList<>();
                                                    markerList.add(driverMarker);
                                                    markerList.add(passengerMarker);
                                                    // iterate over the markers
                                                    for (Marker m : markerList) {
                                                        builder.include(m.getPosition()); // returns LatLng
                                                        // this way the markers will be included on the map
                                                    }
                                                    LatLngBounds bounds = builder.build();
                                                    // adjust camera to show both locations
                                                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 80); // offset (px)
                                                    mMap.animateCamera(cameraUpdate);
                                                    // this camera update conflicts with the previous camera update on logging in
                                                }
                                            }
                                        }
                                    }
                                });
                            }
                        }
                        else {
                            isRideAccepted = false;
                            Toast.makeText(PassengerActivity.this, R.string.no_drivers, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

            }
        }, 0, 10000); // execute run() every 10 seconds
    }

}