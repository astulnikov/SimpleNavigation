package com.stulnikov.simplenavigation;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;


public class MainActivity extends ActionBarActivity implements
        GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private static final int MILLISECONDS_PER_SECOND = 1000;
    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;
    private static final long UPDATE_INTERVAL = MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;
    private static final long FASTEST_INTERVAL = MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;

    private LocationClient mLocationClient;
    private GoogleMap mMap;

    private Location mCurrentLocation;
    private Marker mFromMarker;
    private Marker mToMarker;
    private Polyline mTrackLine;

    private PointType currentPoint = PointType.TO;

    private LocationRequest mLocationRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ac_main);

        mLocationClient = new LocationClient(this, this, this);
        tryInitMap();
        setUpLocationRequest();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (servicesConnected()) {
            mLocationClient.connect();
        }
    }

    @Override
    protected void onStop() {
        if (mLocationClient.isConnected()) {
            mLocationClient.removeLocationUpdates(this);
        }
        mLocationClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
        mLocationClient.requestLocationUpdates(mLocationRequest, this);
        mCurrentLocation = mLocationClient.getLastLocation();
    }

    @Override
    public void onDisconnected() {
        Toast.makeText(this, "Disconnected. Please re-connect.",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(
                        this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            showErrorDialog(connectionResult.getErrorCode());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CONNECTION_FAILURE_RESOLUTION_REQUEST:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        mLocationClient.connect();
                        break;
                }
        }
    }

    private boolean servicesConnected() {
        int resultCode =
                GooglePlayServicesUtil.
                        isGooglePlayServicesAvailable(this);
        if (ConnectionResult.SUCCESS == resultCode) {
            Log.d("Location Updates",
                    "Google Play services is available.");
            return true;
        } else {
            showErrorDialog(resultCode);
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.ac_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                return true;
            case R.id.action_add_from:
                currentPoint = PointType.FROM;
                showPickDialog();
                break;
            case R.id.action_add_to:
                currentPoint = PointType.TO;
                showPickDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void animateCamera(Location location) {
        LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, mMap.getCameraPosition().zoom));
    }

    private void tryInitMap() {
        final SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            Log.d(TAG, "Map fragment in not null");
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    Log.d(TAG, "Attempt to get Map");
                    mMap = mapFragment.getMap();
                    if (mMap != null) {
                        setUpMapFragment();
                        handler.removeCallbacksAndMessages(null);
                    } else {
                        handler.postDelayed(this, 500);
                    }
                }
            }, 0);
        }
    }

    private void setUpMapFragment() {

        mMap.setMyLocationEnabled(true);

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                pickLocation(latLng);
            }
        });

        mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                animateCamera(mCurrentLocation);
                return true;
            }
        });
    }

    private void showFromMarker(LatLng latLng) {
        Log.d(TAG, "showFromMarker: " + latLng.toString());
        LatLng toLatLng;
        if (mToMarker != null) {
            toLatLng = new LatLng(mToMarker.getPosition().latitude, mToMarker.getPosition().longitude);
        } else {
            toLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        }
        if (mFromMarker != null && mFromMarker.isVisible()) {
            mFromMarker.remove();
        }
        mFromMarker = mMap.addMarker(new MarkerOptions()
                .title(getString(R.string.from_point))
                .snippet(toLatLng.toString())
                .position(latLng));
        mFromMarker.showInfoWindow();

       showToMarker(toLatLng);
    }

    private void showToMarker(LatLng latLng) {
        Log.d(TAG, "showToMarker: " + latLng.toString());
        LatLng fromLatLng;
        if (mFromMarker != null) {
            fromLatLng = new LatLng(mFromMarker.getPosition().latitude, mFromMarker.getPosition().longitude);
        } else {
            fromLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        }
        if (mToMarker != null && mToMarker.isVisible()) {
            mToMarker.remove();
        }
        float[] distanceArr = new float[5];
        Location.distanceBetween(fromLatLng.latitude, fromLatLng.longitude,
                latLng.latitude, latLng.longitude, distanceArr);
        int distance = (int) distanceArr[0];
        mToMarker = mMap.addMarker(new MarkerOptions()
                .title(getString(R.string.to_point))
                .snippet(getString(R.string.destination, distance, distanceArr[1]))
                .position(latLng));
        mToMarker.showInfoWindow();

        if (mTrackLine != null && mTrackLine.isVisible()) {
            mTrackLine.remove();
        }
        mTrackLine = mMap.addPolyline(new PolylineOptions().geodesic(true)
                .add(fromLatLng)
                .add(latLng)
                .color(Color.RED));
    }

    private void setUpLocationRequest() {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
    }

    private void showErrorDialog(int errorCode) {
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                errorCode,
                this,
                CONNECTION_FAILURE_RESOLUTION_REQUEST);

        if (errorDialog != null) {
            ErrorDialogFragment errorFragment =
                    new ErrorDialogFragment();
            errorFragment.setDialog(errorDialog);
            errorFragment.show(getSupportFragmentManager(), "Location Updates");
        }
    }

    private void showPickDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.fr_pick_dialog, null);
        final EditText latitude = (EditText) dialogView.findViewById(R.id.latitude_edit);
        final EditText longitude = (EditText) dialogView.findViewById(R.id.longitude_edit);
        final CheckBox pickMapCheckBox = (CheckBox) dialogView.findViewById(R.id.pick_from_map_checkbox);
        final CheckBox pickMyCheckBox = (CheckBox) dialogView.findViewById(R.id.use_my_checkbox);
        pickMapCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    pickMyCheckBox.setChecked(false);
                }
                latitude.setEnabled(!isChecked);
                longitude.setEnabled(!isChecked);
            }
        });
        pickMyCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    pickMapCheckBox.setChecked(false);
                }
                latitude.setEnabled(!isChecked);
                longitude.setEnabled(!isChecked);
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setTitle(R.string.pick_location);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!pickMapCheckBox.isChecked() && !pickMyCheckBox.isChecked()) {
                    if (!TextUtils.isEmpty(latitude.getText()) && !TextUtils.isEmpty(longitude.getText())) {
                        LatLng latLng = new LatLng(Double.valueOf(latitude.getText().toString()), Double.valueOf(longitude.getText().toString()));
                        pickLocation(latLng);
                    }
                } else if (pickMyCheckBox.isChecked()) {
                    LatLng latLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
                    pickLocation(latLng);
                } else if (pickMapCheckBox.isChecked()) {
                    //TODO set action item activated and wait till user picks point
                }
            }
        });
        builder.create().show();
    }

    private void pickLocation(LatLng latLng) {
        switch (currentPoint) {
            case FROM:
                showFromMarker(latLng);
                break;
            case TO:
                showToMarker(latLng);
                break;
        }
    }

    public static class ErrorDialogFragment extends DialogFragment {
        private Dialog mDialog;

        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    private enum PointType {
        FROM, TO
    }
}
