package com.wayme.activity;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.tarek360.instacapture.InstaCapture;
import com.tarek360.instacapture.listener.ScreenCaptureListener;
import com.wayme.R;
import com.wayme.model.ErrorResponse;
import com.wayme.network.ApiParams;
import com.wayme.network.ErrorUtils;
import com.wayme.network.response.WAYResponse;
import com.wayme.utils.Constants;
import com.wayme.utils.FileUtils;
import com.wayme.utils.PermissionUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.OnClick;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by tuanpham on 6/11/16.
 */
public abstract class MapBaseActivity extends BaseActivity implements OnMapReadyCallback,
        GoogleMap.OnMyLocationButtonClickListener, ActivityCompat.OnRequestPermissionsResultCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, ResultCallback<LocationSettingsResult>,
        LocationListener {


    private static final String TAG = "YourLocation";
    private static final int PICK_CONTACT_ACTION = 1000;
    /**
     * Request code for location permission request.
     *
     * @see #onRequestPermissionsResult(int, String[], int[])
     */
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int SMS = 1;
    private static final int WHATSAPP = 0;
    private static final int REQUEST_CHECK_SETTINGS = 10001;

    @BindView(R.id.btn_share) Button btnShare;

    /**
     * Flag indicating whether a requested permission has been denied after returning in
     * {@link #onRequestPermissionsResult(int, String[], int[])}.
     */
    private boolean mPermissionDenied = false;

    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    protected GoogleMap mMap;
    private String contactNumber;
    private File mScreenshotFile;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
//    private boolean mRequestingLocationUpdates = true;
    private boolean mNeedToShowCurrentLocation = true;
    private int selectedCase;
    private ArrayList<String> mSharingOptions;
    protected boolean isShareToFriends = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .addApi(Places.GEO_DATA_API)
                    .build();
        }
        createLocationRequest();
        buildLocationSettingsRequest();
    }

    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Within {@code onPause()}, we pause location updates, but leave the
        // connection to GoogleApiClient intact.  Here, we resume receiving
        // location updates if the user has requested them.
        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    protected void initMapView() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @OnClick(R.id.btn_share)
    public void onBtnShareClicked(View view) {
        isShareToFriends = true;
        createShareDialog();
    }

    private void createShareDialog() {
        mSharingOptions = new ArrayList<>();
        mSharingOptions.add(Constants.SMS_OPTION);
//        mSharingOptions.add(Constants.FACEBOOK_OPTION);

        PackageManager packageMng = getPackageManager();
//        try {
//            packageMng.getPackageInfo(ConstantHelper.PACKAGE_NAME_INSTAGRAM, 0);
//        } catch (Exception e) {
//            instagram_share_view.setVisibility(View.GONE);
//        }
//
//        try {
//            packageMng.getPackageInfo(ConstantHelper.PACKAGE_NAME_WECHAT, 0);
//        } catch (Exception e) {
//            wechat_share_view.setVisibility(View.GONE);
//        }

        try {
            mSharingOptions.add(Constants.WHATSAPP_OPTION);
            packageMng.getPackageInfo(Constants.PACKAGE_NAME_WHATSAPP, 0);
        } catch (PackageManager.NameNotFoundException e) {
            mSharingOptions.remove(Constants.WHATSAPP_OPTION);
        }

//        try {
//            packageMng.getPackageInfo(ConstantHelper.PACKAGE_NAME_PINTEREST, 0);
//        } catch (Exception e) {
//            pinterest_share_view.setVisibility(View.GONE);
//        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Share via");
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        String[] items = mSharingOptions.toArray(new String[mSharingOptions.size()]);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onUserItemSelected(which);
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void onUserItemSelected(int which) {
        switch (mSharingOptions.get(which)) {
            case Constants.SMS_OPTION:
                selectedCase = SMS;
                pickContact();
                break;
            case Constants.WHATSAPP_OPTION:
                selectedCase = WHATSAPP;
                takeScreenShotAndShare();
                break;
        }
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT_ACTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONTACT_ACTION) {
            if (resultCode == RESULT_OK) {
                Uri contactData = data.getData();
                Cursor c = managedQuery(contactData, null, null, null, null);
                if (c.moveToFirst()) {


                    String id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));

                    String hasPhone = c.getString(c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));

                    if (hasPhone.equalsIgnoreCase("1")) {
                        Cursor phones = getContentResolver().query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + id,
                                null, null);
                        phones.moveToFirst();
                        contactNumber = phones.getString(phones.getColumnIndex("data1"));
                        takeScreenShotAndShare();
                    }
                }
            }
        }
    }

    GoogleMap.OnMapLoadedCallback onMapLoadedCallback = new GoogleMap.OnMapLoadedCallback() {
        @Override
        public void onMapLoaded() {
            mMap.snapshot(snapshotReadyCallback);
        }
    };
    GoogleMap.SnapshotReadyCallback snapshotReadyCallback = new GoogleMap.SnapshotReadyCallback() {
        @Override
        public void onSnapshotReady(Bitmap snapshot) {
            File fileScreenShot = FileUtils.getOutputMediaFile();
            View ignoredView = findViewById(R.id.toolbar);
            View btnShareOverlay = findViewById(R.id.iv_btn_share_overlay);
            InstaCapture.getInstance(MapBaseActivity.this).capture(fileScreenShot, ignoredView, btnShareOverlay, btnShare).setScreenCapturingListener(new ScreenCaptureListener() {

                @Override
                public void onCaptureStarted() {
                    //TODO..
                    Log.d(TAG, "Capture started");
                }

                @Override
                public void onCaptureFailed(Throwable e) {
                    //TODO..
                    Log.d(TAG, "Capture failed");
                }

                @Override
                public void onCaptureComplete(File file) {
                    //TODO..
                    mScreenshotFile = file;
                    createWAY();
                }
            });
        }
    };
    protected void takeScreenShotAndShare() {
        mMap.setOnMapLoadedCallback(onMapLoadedCallback);

    }

    private void createWAY() {
        if (mLastLocation != null) {
            showProgressDialog(R.string.loading);
            Map<String, String> params = new HashMap<>();
            params.put(ApiParams.LAT, String.valueOf(mLastLocation.getLatitude()));
            params.put(ApiParams.LNG, String.valueOf(mLastLocation.getLongitude()));
            params.put(ApiParams.IMAGE64, getImageBase64());
            Call<WAYResponse> call = service.createWAY(params);
            call.enqueue(mCreateWAYCallback);
        }
    }

    private String getImageBase64() {

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(mScreenshotFile);
//            inputStream = getContentResolver().openInputStream(mScreenshotUri);
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

            // this is storage overwritten on each iteration with bytes
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];

            // we need to know how may bytes were read to write them to the byteBuffer
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            inputStream.close();
            return Base64.encodeToString(byteBuffer.toByteArray(), Base64.DEFAULT);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Callback<WAYResponse> mCreateWAYCallback = new Callback<WAYResponse>() {
        @Override
        public void onResponse(Call<WAYResponse> call, Response<WAYResponse> response) {
            dismissProgressDialog();
            if (response.isSuccessful() && response.errorBody() == null) {
                WAYResponse wayResponse = response.body();
                if (isShareToFriends)
                    shareToFriends(wayResponse);
                else
                    shareToSocialChannels(wayResponse);
            } else {
                ErrorResponse errorResponse = ErrorUtils.parseError(response);
                showToastMessage("Login failed : " + errorResponse.getMessage());
            }
        }

        @Override
        public void onFailure(Call<WAYResponse> call, Throwable t) {
            dismissProgressDialog();
            showToastMessage("Failed, " + t.getMessage());
        }
    };

    private void shareToFriends(WAYResponse wayResponse) {
        switch (selectedCase) {
            case SMS:
                sendMMS(wayResponse.getWayId());
                break;
            case WHATSAPP:
                shareToWhatsapp(wayResponse.getWayId());
                break;
        }
    }

    private void shareToSocialChannels(WAYResponse wayResponse) {
        Intent waIntent = new Intent(Intent.ACTION_SEND);
        waIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mScreenshotFile));
        waIntent.putExtra(Intent.EXTRA_TEXT, String.format(Constants.SHARE_LINK, wayResponse.getWayId()));
        waIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        waIntent.setType("image/jpeg");
        startActivity(Intent.createChooser(waIntent, getResources().getText(R.string.share_via)));
    }

    private void sendMMS(String wayId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) // At least KitKat
        {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("wayId", String.format(Constants.SHARE_LINK, wayId));
            clipboard.setPrimaryClip(clip);

            String defaultSmsPackageName = Telephony.Sms.getDefaultSmsPackage(this); //Need to change the build to API 19
            Intent mmsIntent = new Intent(Intent.ACTION_SEND);
            mmsIntent.setType("image/jpeg");
            mmsIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mScreenshotFile));
            mmsIntent.putExtra("address", contactNumber);
            mmsIntent.putExtra(Intent.EXTRA_TEXT, String.format(Constants.SHARE_LINK, wayId));
            //if no default app is configured, then choose any app that support this intent.
            if (defaultSmsPackageName != null) {
                mmsIntent.setPackage(defaultSmsPackageName);
            }
            Toast.makeText(this, getString(R.string.shared_link_copied), Toast.LENGTH_LONG).show();
            startActivity(mmsIntent);
        } else // For early versions, do what worked for you before.
        {
            Intent mmsIntent = new Intent(Intent.ACTION_VIEW);
            mmsIntent.setType("vnd.android-dir/mms-sms");
            mmsIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mScreenshotFile));
            mmsIntent.putExtra("address", this.contactNumber);
            mmsIntent.putExtra("sms_body", "message");
            mmsIntent.setType("image/png");
            startActivity(mmsIntent);
        }

    }

    private void shareToWhatsapp(String wayId) {

        Intent waIntent = new Intent(Intent.ACTION_SEND);
        waIntent.setPackage("com.whatsapp");
        waIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(mScreenshotFile.getAbsolutePath()));
        waIntent.putExtra(Intent.EXTRA_TEXT, String.format(Constants.SHARE_LINK, wayId));
        waIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        waIntent.setType("image/jpeg");
        startActivity(waIntent);
    }

    // Map and location
    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;
        mMap.setOnMyLocationButtonClickListener(this);
        enableMyLocation();
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public boolean onMyLocationButtonClick() {
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation();
        } else {
            // Display the missing permission error dialog when the fragments resume.
            mPermissionDenied = true;
        }
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d("location activity", "on Google Api connected");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);
            if (mLastLocation != null) {
                Log.d("location activity", "last location is null");
                updateMapLastKnownLocation();
            }
        }
        startLocationUpdates();
        checkLocationSettings();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d("Your Location ", "Map connection failed!");
    }

    // Credit to
    // https://github.com/googlesamples/android-play-location/blob/master/LocationSettings/app/src/main/java/com/google/android/gms/location/sample/locationsettings/MainActivity.java

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    protected void checkLocationSettings() {
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(
                        mGoogleApiClient,
                        mLocationSettingsRequest
                );
        result.setResultCallback(this);
    }

    @Override
    public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
        final Status status = locationSettingsResult.getStatus();
        switch (status.getStatusCode()) {
            case LocationSettingsStatusCodes.SUCCESS:
                Log.i(TAG, "All location settings are satisfied.");
                startLocationUpdates();
                break;
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to" +
                        "upgrade location settings ");

                try {
                    // Show the dialog by calling startResolutionForResult(), and check the result
                    // in onActivityResult().
                    status.startResolutionForResult(MapBaseActivity.this, REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException e) {
                    Log.i(TAG, "PendingIntent unable to execute request.");
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog " +
                        "not created.");
                break;
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient,
                mLocationRequest,
                this
        ).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
//                mRequestingLocationUpdates = true;
            }
        });
    }

    private void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient,
                this
        ).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
//                mRequestingLocationUpdates = false;
            }
        });
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        updateMapLastKnownLocation();
    }

    private void updateMapLastKnownLocation() {
        if (mNeedToShowCurrentLocation) {
            LatLng currentDeviceLocationLatLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentDeviceLocationLatLng, 17f));

            if (!Geocoder.isPresent()) {
                Toast.makeText(this, R.string.no_geocoder_available,
                        Toast.LENGTH_LONG).show();
                return;
            }
            mNeedToShowCurrentLocation = false;
        }
        onLastKnownLocationUpdated();
    }

    protected abstract void onLastKnownLocationUpdated();
}
