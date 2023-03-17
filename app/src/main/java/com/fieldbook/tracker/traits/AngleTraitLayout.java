package com.fieldbook.tracker.traits;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.widget.TextView;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;

//TODO this isn't showing in new trait creator
public class AngleTraitLayout extends BaseTraitLayout {
    SensorManager sensorManager;
    Sensor accelerometer;
    Sensor magnetometer;

    TextView pitchTv;
    TextView rollTv;
    TextView azimutTv;
    SensorEventListener mEventListener;

    public AngleTraitLayout(Context context) {
        super(context);
    }

    public AngleTraitLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AngleTraitLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setNaTraitsText() {
    }

    @Override
    public String type() {
        return "angle";
    }

    @Override
    public int layoutId() {
        return R.layout.trait_angle;
    }

    @Override
    public void init(Activity act) {
        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        pitchTv = act.findViewById(R.id.pitch);
        rollTv = act.findViewById(R.id.roll);
        azimutTv = act.findViewById(R.id.azimuth);

        mEventListener = new SensorEventListener() {
            float[] mGravity;
            float[] mGeomagnetic;
            Float azimut;
            Float pitch;
            Float roll;

            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }

            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                    mGravity = event.values;
                if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                    mGeomagnetic = event.values;
                if (mGravity != null && mGeomagnetic != null) {
                    float R[] = new float[9];
                    float I[] = new float[9];
                    boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
                    if (success) {
                        float orientation[] = new float[3];
                        SensorManager.getOrientation(R, orientation);
                        azimut = orientation[0]; // orientation contains: azimut, pitch and roll
                        pitch = orientation[1];
                        roll = orientation[2];

                        pitchTv.setText(Double.toString(Math.toDegrees(pitch)));
                        rollTv.setText(Double.toString(Math.toDegrees(roll)));
                        azimutTv.setText(Double.toString(Math.toDegrees(azimut)));
                    }
                }
            }
        };
    }

    @Override
    public void afterLoadNotExists(CollectActivity act) {
        super.afterLoadNotExists(act);
        sensorManager.registerListener(mEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(mEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void deleteTraitListener() {
        ((CollectActivity) getContext()).removeTrait();
    }
}