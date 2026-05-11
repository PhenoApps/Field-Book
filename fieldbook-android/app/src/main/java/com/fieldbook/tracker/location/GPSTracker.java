package com.fieldbook.tracker.location;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

public class GPSTracker extends Service implements LocationListener {

    // The minimum distance to change Updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters
    // The minimum time between updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES = 1000 * 5; // 5 seconds (changed #477 v5.4 from 1 minute)
    private final Context mContext;
    // Declaring a Location Manager
    protected LocationManager locationManager;
    // flag for GPS status
    boolean isGPSEnabled = false;
    // flag for network status
    boolean isNetworkEnabled = false;
    // flag for GPS status
    boolean canGetLocation = false;
    Location location; // location
    double latitude; // latitude
    double longitude; // longitude
    double altitude;
    float declination;

    GPSTrackerListener mListener = null;

    public interface GPSTrackerListener {
        void onLocationChanged(@NonNull Location location);
    }

    public GPSTracker(Context context) {
        this.mContext = context;
        getLocation(MIN_DISTANCE_CHANGE_FOR_UPDATES, MIN_TIME_BW_UPDATES);
    }

    public GPSTracker(Context context, GPSTrackerListener listener, long minDistance, long minTime) {
        this.mContext = context;
        this.mListener = listener;

        getLocation(minDistance, minTime);
    }

    private Location getLastLocation(long minDistance, long minTime) {
        try {
            locationManager = (LocationManager) mContext
                    .getSystemService(LOCATION_SERVICE);

            // getting GPS status
            isGPSEnabled = locationManager
                    .isProviderEnabled(LocationManager.GPS_PROVIDER);

            // getting network status
            isNetworkEnabled = locationManager
                    .isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (isGPSEnabled || isNetworkEnabled) {
                this.canGetLocation = true;

                // First get location from Network Provider
                if (isNetworkEnabled) {

                    if (locationManager != null) {

                        locationManager.requestLocationUpdates(
                                LocationManager.NETWORK_PROVIDER,
                                minTime,
                                minDistance, this);

                        location = locationManager
                                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (location != null) {
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                            onLocationChanged(location);
                        }
                    }

                    // if GPS Enabled get lat/long using GPS Services
                    if (isGPSEnabled) {
                        if (location == null) {
                            if (locationManager != null) {

                                locationManager.requestLocationUpdates(
                                        LocationManager.GPS_PROVIDER,
                                        minTime,
                                        minDistance, this);

                                location = locationManager
                                        .getLastKnownLocation(LocationManager.GPS_PROVIDER);

                                if (location != null) {
                                    latitude = location.getLatitude();
                                    longitude = location.getLongitude();
                                    onLocationChanged(location);
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

        }

        return location;
    }

    public Location getLastLocation() {
        return location;
    }

    public Location getLocation() {
        return getLastLocation(MIN_DISTANCE_CHANGE_FOR_UPDATES, MIN_TIME_BW_UPDATES);
    }

    public Location getLocation(long minDistance, long minTime) {
        return getLastLocation(minDistance, minTime);
    }

    /**
     * Function to get latitude
     */
    public double getLatitude() {
        if (location != null) {
            latitude = location.getLatitude();
        }

        return latitude;
    }

    /**
     * Function to get longitude
     */
    public double getLongitude() {
        if (location != null) {
            longitude = location.getLongitude();
        }

        return longitude;
    }

    /**
     * Function to get the alittude
     */
    public double getAltitude() {
        if (location != null) {
            altitude = location.getAltitude();
        }

        return altitude;
    }

    /**
     * Function to get the declination.
     * Angle between true north and magnetic north
     */
    public float getDeclination() {
        if (location != null) {
            declination = new GeomagneticField(
                (float) getLatitude(),
                (float) getLongitude(),
                (float) getAltitude(),
                System.currentTimeMillis()).getDeclination();
        }

        return declination;
    }

    /**
     * Function to check GPS/wifi enabled
     *
     * @return boolean
     */
    public boolean canGetLocation() {
        return this.canGetLocation;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mListener != null) {

            if (location != null) {

                mListener.onLocationChanged(location);

            }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
}