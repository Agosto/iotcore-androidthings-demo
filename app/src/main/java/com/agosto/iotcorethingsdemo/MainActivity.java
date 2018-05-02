package com.agosto.iotcorethingsdemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.agosto.iotcorethings.DeviceEvents;
import com.agosto.iotcorethings.DeviceKeys;
import com.agosto.iotcorethings.DeviceSettings;
import com.agosto.iotcorethings.IotCoreDeviceConfig;
import com.agosto.iotcorethings.IotCoreMqtt;
import com.agosto.iotcorethings.IotCoreProvisioning;
import com.agosto.iotcorethingsdemo.hats.HatManager;
import com.google.android.things.device.DeviceManager;
import com.google.gson.Gson;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    String mLastPublish = "";
    IotCoreProvisioning mIotCoreProvisioning;
    MqttClient mMqttClient;
    Handler mPublishHandler = new Handler();
    HatManager.HatController mHatController;

    TextView mDebugTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME + " build " + BuildConfig.VERSION_CODE);

        mDebugTextView = findViewById(R.id.debugInfo);
        // change to rainbow hat or blinkt, depending on what you have attached.
        mHatController = HatManager.getConnectedHat(HatManager.BLINKT);

        if (checkBluetoothSupport()) {
            mIotCoreProvisioning = IotCoreProvisioning.getInstance(this);
        } else {
            mHatController.ledStripOn(10000, Color.RED);
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
            addConsoleLog("Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            addConsoleLog( "Bluetooth enabled...starting services");
        }

        /*if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            addConsoleLog("BLE advertising not supported on this device");
        }*/

        return true;
    }


    @Override
    public void onResume() {
        super.onResume();
        updateSettingsUI();
        getConnectionInfo();
        mIotCoreProvisioning.resume(this);
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(onUpdateReceiver,new IntentFilter(DeviceEvents.DEVICE_PROVISIONED));
        localBroadcastManager.registerReceiver(onIdentifyRequest,new IntentFilter(DeviceEvents.IDENTIFY_REQUEST));
        localBroadcastManager.registerReceiver(onResetRequest,new IntentFilter(DeviceEvents.DEVICE_RESET));
        if(mIotCoreProvisioning.isConfigured()) {
            connectIotCore();
        } else {
            mHatController.ledStripOn(5000,Color.BLUE);
        }
    }

    @Override
    public void onPause() {
        mIotCoreProvisioning.pause();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.unregisterReceiver(onUpdateReceiver);
        localBroadcastManager.unregisterReceiver(onIdentifyRequest);
        localBroadcastManager.unregisterReceiver(onResetRequest);
        disconnectTotCore();
        super.onPause();
    }

    private BroadcastReceiver onUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            addConsoleLog("Device has been provisioned");
            updateSettingsUI();
            connectIotCore();
        }
    };

    private BroadcastReceiver onIdentifyRequest = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mHatController.lightShow(5000);
            addConsoleLog("Device has been pinged for identification");
        }
    };

    private BroadcastReceiver onResetRequest = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DeviceManager.getInstance().reboot();
        }
    };


    /**
     * connects to Iot Core via Mqtt, subscribes to config topic, and starts publishing to telemetry topic
     */
    protected void connectIotCore() {
        DeviceSettings deviceSettings = mIotCoreProvisioning.getDeviceSettings();
        DeviceKeys deviceKeys = mIotCoreProvisioning.getDeviceKeys();
        try {
            addConsoleLog("Connecting to IoT Core MQTT");
            mMqttClient = IotCoreMqtt.connect(deviceSettings.projectId, deviceSettings.registryId, deviceSettings.deviceId, deviceKeys.getPrivateKey());
            addConsoleLog("Subscribing config topic");
            mMqttClient.subscribe(IotCoreMqtt.configTopic(deviceSettings.deviceId), new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String json = new String(message.getPayload());
                    IotCoreDeviceConfig deviceConfig = new Gson().fromJson(json, IotCoreDeviceConfig.class);
                    addConsoleLogUI(json);
                    mIotCoreProvisioning.enableConfigServer(deviceConfig.configServerOn);
                    mPublishHandler.post(() -> mHatController.ledStripOn(5000,Color.YELLOW));
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
                mHatController.ledStripOn(5000, Color.GREEN);
                DeviceSettings deviceSettings = mIotCoreProvisioning.getDeviceSettings();
                try {
                    String payload = String.format(Locale.getDefault(),"%s %s", deviceSettings.deviceId, getISO8601StringForDate(new Date()));
                    addConsoleLog("Publishing telemetry message: " + payload);
                    MqttMessage message = new MqttMessage(payload.getBytes());
                    message.setQos(1);
                    mMqttClient.publish(IotCoreMqtt.telemetryTopic(deviceSettings.deviceId), message);
                    mLastPublish = new Date().toString();

                    DeviceState deviceState = new DeviceState();
                    deviceState.currentTime = getISO8601StringForDate(new Date());
                    payload = new Gson().toJson(deviceState);
                    addConsoleLog("Publishing state message: " + payload);
                    message = new MqttMessage(payload.getBytes());
                    message.setQos(1);
                    mMqttClient.publish(IotCoreMqtt.stateTopic(deviceSettings.deviceId), message);

                    updateSettingsUI();

                } catch (MqttException e) {
                    Log.w(TAG,e.toString());
                    addConsoleLog( "reconnecting...");
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
        mHatController.ledStripOff();
        mHatController.close();
    }

    /**
     * get a list of ip addresses for debugging purposes.
     * @return string of ip network address separated by new line charcaters.
     */
    public String localIpAddresses() {
        Enumeration<NetworkInterface> nwis;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            nwis = NetworkInterface.getNetworkInterfaces();
            while (nwis.hasMoreElements()) {

                NetworkInterface ni = nwis.nextElement();
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    stringBuilder.append(String.format(Locale.getDefault(),"%s (%d): %s\n", ni.getDisplayName(), ia.getNetworkPrefixLength(), ia.getAddress()));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    public void getConnectionInfo() {
        String message = getString(R.string.no_connection);
        TextView textView = findViewById(R.id.connnectionState);
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            if (isConnected) {
                message = "Connected to " + activeNetwork.getTypeName() + " " + activeNetwork.getExtraInfo();
            }
        }
        addConsoleLog(message);
        textView.setText(message);
        addConsoleLog(localIpAddresses());
    }

    public void onWifiConnect(View view) {
        EditText editText = findViewById(R.id.ssid);
        String ssid = editText.getText().toString();
        editText = findViewById(R.id.wifiPass);
        String key = editText.getText().toString();
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", ssid);
        wifiConfig.preSharedKey = String.format("\"%s\"", key);

        WifiManager wifiManager = (WifiManager)getSystemService(WIFI_SERVICE);
        if(wifiManager!=null) {
            wifiManager.setWifiEnabled(true);
            addConsoleLog("Wifi is enabled=" + wifiManager.isWifiEnabled());
            int netId = wifiManager.addNetwork(wifiConfig);
            wifiManager.disconnect();
            wifiManager.enableNetwork(netId, true);
            wifiManager.reconnect();
            mPublishHandler.postDelayed(() -> { getConnectionInfo(); }, 5000);
        }
        findViewById(R.id.connectForm).setVisibility(View.GONE);
    }

    public void showConnectForm(View view) {
        findViewById(R.id.connectForm).setVisibility(View.VISIBLE);
    }

    static class DeviceState {
        String appVersion = BuildConfig.VERSION_NAME;
        String currentTime = "";
    }

    protected void addConsoleLog(String msg) {
        Log.d(TAG,msg);
        if(mDebugTextView.length() > 10000) {
            mDebugTextView.setText(msg);
        } else {
            mDebugTextView.setText(String.format("%s\n%s", msg, mDebugTextView.getText()));
        }
    }

    Handler uiHandler = new Handler();

    protected void addConsoleLogUI(final String msg) {
        uiHandler.post(() -> addConsoleLog(msg));
    }

}

