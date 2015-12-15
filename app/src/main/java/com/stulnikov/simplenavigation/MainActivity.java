package com.stulnikov.simplenavigation;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.flurry.android.FlurryAgent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;


public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final static int LOCATION_SETTINGS_REQUEST = 9876;
    private static final int MILLISECONDS_PER_SECOND = 1000;
    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;
    private static final long UPDATE_INTERVAL = MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;
    private static final long FASTEST_INTERVAL = MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;
    private static final int FULL_CIRCLE_ANGLE = 360;
    private static final int REQUEST_CODE_ACCESS_FINE_LOCATION = 2;

    private GoogleApiClient mLocationClient;
    private GoogleMap mMap;

    private MenuItem mFromItem;
    private MenuItem mToItem;

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

        setUpLocationRequest();
        mLocationClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        tryInitMap();
        initLayerButton();
        checkLocationAccess();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        FlurryAgent.onStartSession(this, getString(R.string.flurry_api_key));
        if (servicesConnected()) {
            mLocationClient.connect();
        }
    }

    @Override
    protected void onStop() {
        if (mLocationClient.isConnected()) {
            mLocationClient.unregisterConnectionCallbacks(this);
        }
        mLocationClient.disconnect();
        FlurryAgent.onEndSession(this);
        super.onStop();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(1000); // Update location every second

        if (checkPermission(false)) {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mLocationClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
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
        Log.d(TAG, "onLocationChanged");
        mCurrentLocation = location;
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult");
        switch (requestCode) {
            case CONNECTION_FAILURE_RESOLUTION_REQUEST:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        mLocationClient.connect();
                        break;
                }
                break;
            case LOCATION_SETTINGS_REQUEST:
                checkLocationAccess();
                break;
        }
    }

    /**
     * check if location providers were enabled
     *
     * @return true if any provider is enabled
     */
    private boolean checkLocationAccess() {
        String locationProviders = null;
        int locationMode = 0;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            locationProviders = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        } else {
            try {
                locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
        }
        if (TextUtils.isEmpty(locationProviders) && locationMode == Settings.Secure.LOCATION_MODE_OFF) {
            showNoLocationDialog();
            return false;
        } else {
            return true;
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
        mFromItem = menu.findItem(R.id.action_add_from);
        mToItem = menu.findItem(R.id.action_add_to);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_add_from:
                currentPoint = PointType.FROM;
                mToItem.setIcon(R.drawable.ic_action_directions);
                mFromItem.setIcon(R.drawable.ic_action_place_selected);
                showPickDialog();
                break;
            case R.id.action_add_to:
                currentPoint = PointType.TO;
                mFromItem.setIcon(R.drawable.ic_action_place);
                mToItem.setIcon(R.drawable.ic_action_directions_selected);
                showPickDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initLayerButton() {
        FloatingActionButton floatingActionButton = (FloatingActionButton) findViewById(R.id.layer_options);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentMapType = mMap.getMapType();
                if (currentMapType == GoogleMap.MAP_TYPE_NORMAL) {
                    mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                } else if (currentMapType == GoogleMap.MAP_TYPE_SATELLITE) {
                    mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                } else if (currentMapType == GoogleMap.MAP_TYPE_HYBRID) {
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                }
            }
        });
    }

    private void animateCamera(Location location) {
        LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, mMap.getCameraPosition().zoom));
    }

    private boolean checkPermission(boolean silent) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            if (!silent) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    showPermissionRationale();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_CODE_ACCESS_FINE_LOCATION);
                }
            }
            return false;
        }
    }

    private void showPermissionRationale() {
        Snackbar.make(findViewById(R.id.layer_button_coordinator), R.string.permission_required_title, Snackbar.LENGTH_LONG)
                .setAction(R.string.settings, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        gotoAppSettings();
                    }
                })
                .show();
    }


    private void gotoAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void tryInitMap() {
        final SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            Log.d(TAG, "Map fragment is not null");
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    Log.d(TAG, "Attempt to get Map");
                    mMap = mapFragment.getMap();
                    if (mMap != null) {
                        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
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

        mMap.setMyLocationEnabled(checkPermission(true));

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                pickLocation(latLng);
            }
        });

        mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                if (mCurrentLocation != null) {
                    animateCamera(mCurrentLocation);
                } else {
                    if (checkLocationAccess()) {
                        showLocationNotAvailableDialog();
                    }
                }
                return true;
            }
        });
    }

    private void showFromMarker(LatLng latLng) {
        Log.d(TAG, "showFromMarker: " + latLng.toString());
        LatLng toLatLng;
        if (mToMarker != null) {
            toLatLng = new LatLng(mToMarker.getPosition().latitude, mToMarker.getPosition().longitude);
        } else if (mCurrentLocation != null) {
            toLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        } else {
            toLatLng = new LatLng(0, 0);
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
        } else if (mCurrentLocation != null) {
            fromLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        } else {
            fromLatLng = new LatLng(0, 0);
        }
        if (mToMarker != null && mToMarker.isVisible()) {
            mToMarker.remove();
        }
        float[] distanceArr = new float[5];
        Location.distanceBetween(fromLatLng.latitude, fromLatLng.longitude,
                latLng.latitude, latLng.longitude, distanceArr);
        int distance = (int) distanceArr[0];
        float bearing = distanceArr[1];
        if (bearing < 0) {
            bearing = FULL_CIRCLE_ANGLE + bearing;
        }
        mToMarker = mMap.addMarker(new MarkerOptions()
                .title(getString(R.string.to_point))
                .snippet(getString(R.string.destination, distance, bearing))
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

    private void showNoLocationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.no_location_title);
        builder.setMessage(R.string.no_location_message);
        builder.setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), LOCATION_SETTINGS_REQUEST);
            }
        });
        builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        builder.create().show();
    }

    private void showLocationNotAvailableDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.no_location_title);
        builder.setMessage(R.string.location_not_ready_message);
        builder.setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), LOCATION_SETTINGS_REQUEST);
            }
        });
        builder.setNegativeButton(R.string.wait, null);
        builder.create().show();
    }

    private void showPickDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.fr_pick_dialog, (ViewGroup) findViewById(R.id.root_view), false);
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
                    if (mCurrentLocation != null) {
                        LatLng latLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
                        pickLocation(latLng);
                    } else {
                        if (checkLocationAccess()) {
                            showLocationNotAvailableDialog();
                        }
                    }
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
