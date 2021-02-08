package idv.markkuo.cscblebridge;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;


public class MainActivity extends AppCompatActivity  {
    private static final String TAG = MainActivity.class.getSimpleName();

    private TextView tv_speedSensorState, tv_cadenceSensorState, tv_hrSensorState, tv_runSensorState,
            tv_speedSensorTimestamp, tv_cadenceSensorTimestamp, tv_hrSensorTimestamp,
            tv_runSensorTimestamp, tv_speed, tv_cadence, tv_hr, tv_runSpeed, tv_runCadence, tv_time;
    private Button btn_service;

    private MainActivityReceiver receiver;
    private boolean mBound = false;
    private CSCService mService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Intent mServiceIntent = new Intent(getApplicationContext(), CSCService.class);

        setContentView(R.layout.activity_main);

        tv_speed = findViewById(R.id.SpeedText);
        tv_cadence = findViewById(R.id.CadenceText);
        tv_time = findViewById(R.id.TimeText);
        btn_service = findViewById(R.id.ServiceButton);

        ensureServiceRunning(mServiceIntent);

        receiver = new MainActivityReceiver();
        // register intent from our service
        IntentFilter filter = new IntentFilter();
        filter.addAction("idv.markkuo.cscblebridge.ANTDATA");
        registerReceiver(receiver, filter);
    }

    private void ensureServiceRunning(Intent mServiceIntent) {
        if(mBound) {
            Log.d(TAG, "Service already started");
        } else {
            Log.d(TAG, "Starting Service");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                MainActivity.this.startForegroundService(mServiceIntent);
            else
                MainActivity.this.startService(mServiceIntent);

            // Bind to the service so we can interact with it
            if (!bindService(mServiceIntent, connection, Context.BIND_AUTO_CREATE)) {
                Log.d(TAG, "Failed to bind to service");
            } else {
                mBound = true;
            }
        }
    }

    protected void onResume() {
        Intent mServiceIntent = new Intent(getApplicationContext(), CSCService.class);
        super.onResume();
        ensureServiceRunning(mServiceIntent);
    }


    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,  IBinder service) {
            CSCService.LocalBinder binder = (CSCService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    // Unbind from the service
    void unbindService() {
        if (mBound) {
            unbindService(connection);
            mBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(receiver);
        unbindService();
    }

    private void resetUi() {
        tv_speed.setText(getText(R.string.no_data));
        tv_cadence.setText(getText(R.string.no_data));
        tv_time.setText("--:--");
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        assert manager != null;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (CSCService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // this BroadcastReceiver is used to update UI only
    private class MainActivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String statusBSD = intent.getStringExtra("bsd_service_status"); // bicycle speed
            final String statusBC = intent.getStringExtra("bc_service_status"); // bicycle cadence
//            final String statusHR = intent.getStringExtra("hr_service_status");
//            final String statusRsc = intent.getStringExtra("ss_service_status");
//            final long speedTimestamp = intent.getLongExtra("speed_timestamp", -1);
//            final long cadenceTimestamp = intent.getLongExtra("cadence_timestamp", -1);
//            final long hrTimestamp = intent.getLongExtra("hr_timestamp", -1);
//            final long runTimestamp = intent.getLongExtra("ss_stride_count_timestamp", -1);
            final float speed = intent.getFloatExtra("speed", -1.0f);
            final int cadence = intent.getIntExtra("cadence", -1);
            final int hr = intent.getIntExtra("hr", -1);
//            final float runSpeed = intent.getFloatExtra("ss_speed", -1);
//            final long runStrideCount = intent.getLongExtra("ss_stride_count", -1);
            final Instant instant = Instant.now();  // Current moment in UTC.
            LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

            runOnUiThread(new Runnable() {
                @SuppressLint("DefaultLocale")
                @Override
                public void run() {
                    if(statusBSD != null)
                        tv_speed.setText(statusBSD);
                    else
                    if (speed >= 0.0f)
                        tv_speed.setText(String.format("%.01f", speed) + " km/h");
                    if(statusBC != null)
                        tv_speed.setText(statusBC);
                    else
                    if (cadence >= 0)
                        tv_cadence.setText(String.format("%3d rpm", cadence));
                    if (now != null)
                        tv_time.setText(String.format("%2d:%02d:%02d",
                                now.getHour(), now.getMinute(), now.getSecond()));
                }
            });
        }
    }
}
