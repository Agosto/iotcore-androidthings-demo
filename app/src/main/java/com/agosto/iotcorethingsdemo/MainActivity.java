package com.agosto.iotcorethingsdemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.TextView;

import com.agosto.iotcorethings.DeviceConfigServer;
import com.agosto.iotcorethings.DeviceKeys;
import com.agosto.iotcorethings.DeviceSettings;
import com.agosto.iotcorethings.EddystoneAdvertiser;
import com.agosto.iotcorethings.IotCoreDeviceConfig;
import com.agosto.iotcorethings.IotCoreMqtt;
import com.google.gson.Gson;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private DeviceConfigServer mWebServer;
    private EddystoneAdvertiser mEddystoneAdvertiser;
    String mLastPublish = "";

    DeviceSettings mDeviceSettings;
    DeviceKeys mDeviceKeys;
    MqttClient mMqttClient;
    Handler mPublishHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //resetDevice();
        setTitle(getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME + " build " + BuildConfig.VERSION_CODE);
        mDeviceSettings = DeviceSettings.fromContext(this);
        if (!checkBluetoothSupport()) {
            finish();
        }
        mDeviceKeys = new DeviceKeys();
        mDeviceSettings.encodedPublicKey = "-----BEGIN CERTIFICATE-----\n"+mDeviceKeys.encodedCertificate()+"-----END CERTIFICATE-----\n";
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        mDeviceSettings.ipAddress = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        mWebServer = new DeviceConfigServer(8080, mDeviceSettings);
    }

    /**
     * update the TextViews
     */
    public void updateSettingsUI() {
        TextView textView = findViewById(R.id.deviceId);
        textView.setText(mDeviceSettings.deviceId);
        textView = findViewById(R.id.server);
        textView.setText("http://" + mDeviceSettings.ipAddress + ":8080");
        textView = findViewById(R.id.registryId);
        textView.setText(mDeviceSettings.registryId);
        textView = findViewById(R.id.projectId);
        textView.setText(mDeviceSettings.projectId);
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

        mEddystoneAdvertiser = new EddystoneAdvertiser(bluetoothAdapter);
        return true;
    }


    @Override
    public void onResume() {
        super.onResume();
        updateSettingsUI();
        enableConfigServer(!mDeviceSettings.isConfigured());
        IntentFilter intentFilter = new IntentFilter(DeviceSettings.ACTION_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(onUpdateReceiver,intentFilter);
        if(mDeviceSettings.isConfigured()) {
            connectIotCore();
        }
    }

    void enableConfigServer(boolean enable) {
        String url;
        if(enable) {
            mWebServer.start();
            url = "http://" + mDeviceSettings.ipAddress;
        } else {
            mWebServer.stop();
            url = "http://" + mDeviceSettings.deviceId;
        }
        if(mEddystoneAdvertiser !=null) {
            mEddystoneAdvertiser.stopAdvertising();
            mEddystoneAdvertiser.startAdvertising(url);
        }
    }

    @Override
    public void onPause() {
        mWebServer.stop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onUpdateReceiver);
        if(mEddystoneAdvertiser !=null)
            mEddystoneAdvertiser.stopAdvertising();
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


    /**
     * connects to Iot Core via Mqtt, subscribes to config topic, and starts publishing to telemetry topic
     */
    protected void connectIotCore() {
        try {
            mMqttClient = IotCoreMqtt.connect(mDeviceSettings.projectId, mDeviceSettings.registryId, mDeviceSettings.deviceId, mDeviceKeys.getPrivateKey());
            mMqttClient.subscribe(IotCoreMqtt.configTopic(mDeviceSettings.deviceId), new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String json = new String(message.getPayload());
                    IotCoreDeviceConfig deviceConfig = new Gson().fromJson(json, IotCoreDeviceConfig.class);
                    Log.d(TAG,deviceConfig.toString());
                    enableConfigServer(deviceConfig.configServerOn);
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
                try {
                    int numMessages = 10;
                    // Publish numMessages messages to the MQTT bridge, at a rate of 1 per second.
                    for (int i = 1; i <= numMessages; ++i) {
                        String payload = String.format(Locale.getDefault(),"%s/%s-payload-%d", mDeviceSettings.registryId, mDeviceSettings.deviceId, i);
                        System.out.format("Publishing message %d/%d: '%s'\n", i, numMessages, payload);

                        // Publish "payload" to the MQTT topic. qos=1 means at least once delivery. Cloud IoT Core
                        // also supports qos=0 for at most once delivery.
                        MqttMessage message = new MqttMessage(payload.getBytes());
                        message.setQos(1);
                        mMqttClient.publish(IotCoreMqtt.telemetryTopic(mDeviceSettings.deviceId), message);
                        Thread.sleep(1000);
                    }
                    mLastPublish = new Date().toString();
                    updateSettingsUI();
                } catch (InterruptedException | MqttException e) {
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

    protected void resetDevice() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().clear().apply();
        DeviceKeys.deleteKeys();
    }

}

