package com.agosto.iotcorethingsdemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.agosto.iotcorethings.DeviceEvents;
import com.agosto.iotcorethings.DeviceKeys;
import com.agosto.iotcorethings.DeviceSettings;
import com.agosto.iotcorethings.IotCoreDeviceConfig;
import com.agosto.iotcorethings.IotCoreMqtt;
import com.agosto.iotcorethings.IotCoreProvisioning;
import com.google.android.things.contrib.driver.apa102.Apa102;
import com.google.android.things.contrib.driver.pwmspeaker.Speaker;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.pio.Gpio;
import com.google.gson.Gson;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    String mLastPublish = "";
    IotCoreProvisioning mIotCoreProvisioning;
    MqttClient mMqttClient;
    Handler mPublishHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME + " build " + BuildConfig.VERSION_CODE);
        // need to restart app to get permission the first time
        if (checkBluetoothSupport()) {
            mIotCoreProvisioning = IotCoreProvisioning.getInstance(this);
        } else {
            // TODO: reboot? or restart app.
            ledStripOn(10000, Color.RED);
            finish();
        }
    }

    /**
     * update the TextViews
     */
    public void updateSettingsUI() {
        DeviceSettings deviceSettings = mIotCoreProvisioning.getDeviceSettings();
        TextView textView = findViewById(R.id.deviceId);
        textView.setText(deviceSettings.deviceId);
        textView = findViewById(R.id.server);
        textView.setText(getString(R.string.config_url,deviceSettings.ipAddress));
        textView = findViewById(R.id.registryId);
        textView.setText(deviceSettings.registryId);
        textView = findViewById(R.id.projectId);
        textView.setText(deviceSettings.projectId);
        textView = findViewById(R.id.publishDate);
        textView.setText(mLastPublish);
    }

    /**
     * checks for bluetooth support and starts EddystoneAdvertiser.\
     * if support is not enabled, will attempt to enable it
     * @return true if bluetooth support is enable
     */
    private boolean checkBluetoothSupport() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if(bluetoothManager==null) {
            Log.w(TAG, "Failed to get BluetoothManager");
            return false;
        }

        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
        }

        if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            Log.d(TAG,"BLE advertising not supported on this device");
        }

        return true;
    }


    @Override
    public void onResume() {
        super.onResume();
        updateSettingsUI();
        mIotCoreProvisioning.resume();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(onUpdateReceiver,new IntentFilter(DeviceEvents.DEVICE_PROVISIONED));
        localBroadcastManager.registerReceiver(onIdentifyRequest,new IntentFilter(DeviceEvents.IDENTIFY_REQUEST));
        if(mIotCoreProvisioning.isConfigured()) {
            connectIotCore();
        } else {
            ledStripOn(5000,Color.BLUE);
            blueLedOn(5000);
        }
    }

    @Override
    public void onPause() {
        mIotCoreProvisioning.pause();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.unregisterReceiver(onUpdateReceiver);
        localBroadcastManager.unregisterReceiver(onIdentifyRequest);
        disconnectTotCore();
        super.onPause();
    }

    private BroadcastReceiver onUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateSettingsUI();
            connectIotCore();
        }
    };

    private BroadcastReceiver onIdentifyRequest = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ledStripOn(5000);
            greenLedOn(5000);
            blueLedOn(4000);
            redLedOn(3000);

        }
    };


    /**
     * connects to Iot Core via Mqtt, subscribes to config topic, and starts publishing to telemetry topic
     */
    protected void connectIotCore() {
        DeviceSettings deviceSettings = mIotCoreProvisioning.getDeviceSettings();
        DeviceKeys deviceKeys = mIotCoreProvisioning.getDeviceKeys();
        try {
            mMqttClient = IotCoreMqtt.connect(deviceSettings.projectId, deviceSettings.registryId, deviceSettings.deviceId, deviceKeys.getPrivateKey());
            mMqttClient.subscribe(IotCoreMqtt.configTopic(deviceSettings.deviceId), new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String json = new String(message.getPayload());
                    IotCoreDeviceConfig deviceConfig = new Gson().fromJson(json, IotCoreDeviceConfig.class);
                    Log.d(TAG,json);
                    mIotCoreProvisioning.enableConfigServer(deviceConfig.configServerOn);
                    mPublishHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            ledStripOn(5000,Color.YELLOW);
                            blueLedOn(5000);
                        }
                    });
                }
            });
            startPublishing(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * disconnects from Mqtt and stops publishing to telemetry topic
     */
    protected void disconnectTotCore() {
        if(mMqttClient != null) {
            mPublishHandler.removeCallbacks(null);
            try {
                mMqttClient.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * publish data to telemetry topic
     * @param delayMs milliseconds to delay next publishing
     */
    protected void startPublishing(int delayMs) {
        mPublishHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ledStripOn(5000, Color.GREEN);
                greenLedOn(5000);
                DeviceSettings deviceSettings = mIotCoreProvisioning.getDeviceSettings();
                try {
                    String payload = String.format(Locale.getDefault(),"%s %s", deviceSettings.deviceId, getISO8601StringForDate(new Date()));
                    Log.d(TAG,"Publishing message: " + payload);
                    MqttMessage message = new MqttMessage(payload.getBytes());
                    message.setQos(1);
                    mMqttClient.publish(IotCoreMqtt.telemetryTopic(deviceSettings.deviceId), message);
                    mLastPublish = new Date().toString();
                    updateSettingsUI();
                } catch (MqttException e) {
                    Log.w(TAG,e.toString());
                    Log.d(TAG, "reconnecting...");
                    connectIotCore();
                    //e.printStackTrace();
                    return;
                }
                // start again;
                startPublishing(60000);
            }
        },delayMs);
    }

    private static String getISO8601StringForDate(Date date) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ledStripOff();
        if(mLedstrip!=null) {
            try {
                mLedstrip.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mLedstrip = null;
        }
    }

    // TODO: move rainbow hat methods into another class.

    private Apa102 mLedstrip;

    public void ledStripOn(int offDelay) {
        ledStripOn(offDelay,0);
    }

    public void ledStripOn(int offDelay, int color) {
        Handler handler = new Handler();
        try {
            if(mLedstrip==null)
                mLedstrip = RainbowHat.openLedStrip();
            mLedstrip.setBrightness(5);
            int[] rainbow = new int[RainbowHat.LEDSTRIP_LENGTH];
            for (int i = 0; i < rainbow.length; i++) {
                rainbow[i] = color == 0 ? Color.HSVToColor(255, new float[]{i * 360.f / rainbow.length, 1.0f, 1.0f}) : color;
            }
            mLedstrip.write(rainbow);
            mLedstrip.write(rainbow);
// Close the device when done.
            //ledstrip.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ledStripOff();
            }
        },offDelay);
    }

    public void ledStripOff() {
        if(mLedstrip==null) {
            return;
        }
        try {
            //Apa102 ledstrip = RainbowHat.openLedStrip();
            mLedstrip.setBrightness(0);
            int[] rainbow = new int[RainbowHat.LEDSTRIP_LENGTH];
            for (int i = 0; i < rainbow.length; i++) {
                rainbow[i] = Color.BLACK;
            }
            mLedstrip.write(rainbow);
            mLedstrip.write(rainbow);

// Close the device when done.

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void redLedOn(int delay) {
        try {
            ledOn(RainbowHat.openLedRed(), delay);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void blueLedOn(int delay) {
        try {
            ledOn(RainbowHat.openLedBlue(), delay);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void greenLedOn(int delay) {
        try {
            ledOn(RainbowHat.openLedGreen(), delay);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void ledOn(final Gpio led, int delay) throws IOException {
        led.setValue(true);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    led.setValue(false);
                    led.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        },delay);
    }

}

