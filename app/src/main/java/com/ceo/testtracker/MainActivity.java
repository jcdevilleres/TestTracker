package com.ceo.testtracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.ceo.testtracker.Constants.SUCCESS_RESULT;

public class MainActivity extends AppCompatActivity{

    private FusedLocationProviderClient mFusedLocationClient;
    boolean mRequestingLocationUpdates;
    Location mCurrentLocation;
    private LocationRequest mLocationRequest = new LocationRequest();
    private LocationCallback mLocationCallback = new LocationCallback();

    Location mLastKnownLocation;

    private static final String TAG = MainActivity.class.getSimpleName();
    private String mAddressOutput;
    private TextView mLocationAddressTextView;
    private AddressResultReceiver mResultReceiver;
    protected Location mLastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(Constants.TAG,"onCreate application!");
        mLocationAddressTextView = findViewById(R.id.location_address_view);

        mResultReceiver = new AddressResultReceiver(new Handler());

        createLocationRequest();
        getLastKnownLocation();
        startLocationUpdates();
    }

    //Go to FirstActivity when START button is clicked
    public void buttonClick(View v) {
        Log.i(Constants.TAG,"Update button clicked!");
        getLastKnownLocation();
        displayAddressOutput();
        fetchAddressButtonHander(v);
    }

    protected void getLastKnownLocation() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        //Verify PERMISSIONS are enabled
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        //Got last known location but go here if it is NULL
                        if (location != null) {
                            //Logic to handle location object, save location and update UI
                            mCurrentLocation = location;
                            updateUILocation(location);
                            //Logic to save in mLastLocation, this will be passed to the intent service teehee
                            mLastLocation = location;
                        }
                    }
                });
    }

    protected void createLocationRequest() {
        //Create a location request with parameters, interval (app preference) = 10s
        //fastest interval (other apps that affect updates) = 5s
        // priority (which location sources to use) = HIGH Accuracy

        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        //Create builder with the Location Request parameters
        // Get Current Location Settings
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);

        //Check if settings must be changed
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                //The client can initialize location requests here.
                Log.i(Constants.TAG,"All location settings satisfied!");
            }
        });

        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                int statusCode = ((ApiException) e).getStatusCode();
                Log.i(Constants.TAG,"Location settings not satisfied!");
                switch (statusCode) {
                    case CommonStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            resolvable.startResolutionForResult(MainActivity.this, 0x1);
                        } catch (IntentSender.SendIntentException sendEx) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.
                        break;
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        mLocationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    //Updated UI with location data
                    updateUILocation(location);
                }
            }
        };

        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                null);
    }

    private void updateUILocation(Location location){
        final TextView latlongText = findViewById(R.id.textView2);
        Log.i(Constants.TAG, String.format("%s,%s", location.getLatitude(), location.getLongitude()));
        latlongText.setText(location.getLatitude() + getString(R.string.comma) + location.getLongitude());
    }

    protected void startIntentService() {
        //Create an intent for passing to the intent service responsible for fetching the address
        Intent intent = new Intent(this, FetchAddressIntentService.class);

        //Pass the result receiver as an extra to the service
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        Log.i(TAG, "Passed to the service: " + Constants.RECEIVER + "," + mResultReceiver);

        //Pass the location data as an extra to the service
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, mLastLocation);
        Log.i(TAG, "Passed to the service: " + Constants.LOCATION_DATA_EXTRA + "," + mLastLocation);

        //Start the service. If the service isn't already running, it is instantiated and started
        //(creating a process for it if needed); if it is running then it remains running. The
        //service kills itself automatically once all intent are processed.
        startService(intent);
        Log.i(TAG, "Start service");
    }

    @SuppressLint("MissingPermission")
    private void fetchAddressButtonHander(View view) {
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        mLastKnownLocation = location;

                        // In some rare cases the location returned can be null
                        if (mLastKnownLocation == null) {
                            return;
                        }

                        if (!Geocoder.isPresent()) {
                            Toast.makeText(MainActivity.this,
                                    R.string.no_geocoder_available,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Start service and update UI to reflect new location
                        startIntentService();
                    }
                });
    }

    public class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            //Display the address string
            //or an error message sent from the intent service
            mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);
            displayAddressOutput();

            //Show a toast message if an address was found
            if (resultCode == Constants.SUCCESS_RESULT) {
                showToast(getString(R.string.address_found));
            }
        }
    }

    private void displayAddressOutput() {
        mLocationAddressTextView.setText(mAddressOutput);
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

}


























