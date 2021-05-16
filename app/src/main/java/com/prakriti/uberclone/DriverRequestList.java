package com.prakriti.uberclone;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.LogOutCallback;
import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.util.ArrayList;
import java.util.List;

public class DriverRequestList extends AppCompatActivity implements AdapterView.OnItemClickListener {
// populate with requests from server
// location request codes to be put here to get requests close to driver
// implement item click for driver to respond to ride requests, also new map activity to view both locations

    private ListView listviewPassRequests;
    private ArrayList<String> nearbyRequestsList;
    private ArrayAdapter arrayAdapter; // can also use SimpleAdapter

    private Button btnUpdateRequests;

    private LocationManager locationManager;
    private LocationListener locationListener;

    private static final int DRIVER_REQ_CODE = 6000;

    private ArrayList<Double> passengerLat, passengerLong;
    private ArrayList<String> usernameRequestsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_request_list);
        setTitle(R.string.driver_title);

        locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);

        btnUpdateRequests = findViewById(R.id.btnUpdateRequests);
        btnUpdateRequests.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getDriverLocation();
            }
        });

        listviewPassRequests = findViewById(R.id.listviewPassRequests);
        nearbyRequestsList = new ArrayList<>();
        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, nearbyRequestsList);

        listviewPassRequests.setAdapter(arrayAdapter);
        listviewPassRequests.setOnItemClickListener(this);

        passengerLat = new ArrayList<>();
        passengerLong = new ArrayList<>();
        usernameRequestsList = new ArrayList<>();

        nearbyRequestsList.clear();
    }

    private void getDriverLocation() { // permission to access driver's location
        // anon inner class for listener
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                updatePassengerRequestsList(location); // called when loc is changed
            }
        };
        // add permission check at runtime
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, locationListener);
            // get loc every (minTime) ms or when user moves my (minDistance) m -> called off change in time or distance
            updatePassengerRequestsList(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
            // pass updated location
        }
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, DRIVER_REQ_CODE);
            // override onRequestPermissionResults()
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == DRIVER_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // check self permission
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, locationListener);
                }
            }
        }
    }

    private void updatePassengerRequestsList(Location dLocation) {
        // populate listview
        if (dLocation != null) {
            // update listview for every loc update request
            ParseGeoPoint driverLocation = new ParseGeoPoint(dLocation.getLatitude(), dLocation.getLongitude());
            saveDriverLocationToServer(driverLocation); // saving driver location to server

            // create query for RequestRide class on server
            ParseQuery<ParseObject> requestRideQuery = ParseQuery.getQuery("RequestRide");
            requestRideQuery.whereNear("passengerLocation", driverLocation); // nearby location values only
            // also don't show requests that already have an assigned driver, aka, accepted requests
            requestRideQuery.whereDoesNotExist("assignedDriver");
            
            requestRideQuery.findInBackground(new FindCallback<ParseObject>() {
                @Override
                public void done(List<ParseObject> objects, ParseException e) {
                    if (objects.size() > 0 && e == null) {

                        if (nearbyRequestsList.size() > 0) {
                            nearbyRequestsList.clear();
                        }
                        // so that list is not changed unless new request is sent bcoz loc update is called every second
                        if (passengerLat.size() > 0) {
                            passengerLat.clear();
                        }
                        if (passengerLong.size() > 0) {
                            passengerLong.clear();
                        }
                        if(usernameRequestsList.size() > 0) {
                            usernameRequestsList.clear();
                        }

                        for (ParseObject request : objects) {
                            ParseGeoPoint passLoc = request.getParseGeoPoint("passengerLocation");
                            // get distance to passenger
                            Double distanceToPass = driverLocation.distanceInKilometersTo(passLoc);
                            // gets double distance
                            float roundDistanceToPass = Math.round(distanceToPass * 10) / 10; // user friendly value
                            // add to arraylist
                            nearbyRequestsList.add(request.getString("username") + " -> " + roundDistanceToPass + " km away");
                            usernameRequestsList.add(request.getString("username"));

                            // put lat & long in arrays to pass to next map activity on item click
                            passengerLat.add(passLoc.getLatitude());
                            passengerLong.add(passLoc.getLongitude());
                        }
                        arrayAdapter.notifyDataSetChanged(); // notify of changes
                    } else {
                        Toast.makeText(DriverRequestList.this, R.string.empty_requests, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    // driver location needs to be updated even after accepting a request & travelling to passenger's location
    // update periodically once a request has been accepted by driver
    private void saveDriverLocationToServer(ParseGeoPoint loc) {
        ParseUser driver = ParseUser.getCurrentUser();
        // create new column for driver users only
        // this column will be created for current driver
        driver.put("driverLocation", loc);
        driver.saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if(e == null) {
                    Toast.makeText(DriverRequestList.this, R.string.driver_saved, Toast.LENGTH_SHORT).show();
                }
                else {
                    e.printStackTrace();
                }
            }
        });
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // send username & location of tapped request to ViewLocation map activity
        // send user lat & long, as we cannot access the location itself
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location currDriverLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if(currDriverLocation != null) {
                Intent intent = new Intent(this, ViewLocation.class);
                intent.putExtra("driverLat", currDriverLocation.getLatitude());
                intent.putExtra("driverLong", currDriverLocation.getLongitude());
                // passenger location info
                intent.putExtra("passUsername", usernameRequestsList.get(position));
                intent.putExtra("passLat", passengerLat.get(position)); // passenger latitude of tapped position
                intent.putExtra("passLong", passengerLong.get(position));
                startActivity(intent);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.driver_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.driver_logout:
                logOutDriverFromApp();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logOutDriverFromApp() {
        ParseUser.logOutInBackground(new LogOutCallback() {
            @Override
            public void done(ParseException e) {
                if(e == null) {
                    Toast.makeText(DriverRequestList.this, R.string.user_logout, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(DriverRequestList.this, MainActivity.class));
                    finish();
                }
                else {
                    Toast.makeText(DriverRequestList.this, R.string.unable_to_logout, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

}