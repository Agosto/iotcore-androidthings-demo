package com.agosto.iotcorethings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import static android.content.Context.BLUETOOTH_SERVICE;
import static android.content.Context.WIFI_SERVICE;


public class IotCoreProvisioning {
    private static IotCoreProvisioning ourInstance = null;

    private DeviceConfigServer mWebServer;
    private EddystoneAdvertiser mEddystoneAdvertiser;
    private DeviceSettings mDeviceSettings;
    private DeviceKeys mDeviceKeys;

    public static IotCoreProvisioning getInstance(Context context) {
        if(ourInstance==null) {
            ourInstance = new IotCoreProvisioning(context);
        }
        return ourInstance;
    }

    private IotCoreProvisioning(Context context) {
        if (ourInstance != null){
            throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
        }

        // device settings and keys
        mDeviceSettings = DeviceSettings.fromContext(context);
        mDeviceKeys = new DeviceKeys();
        mDeviceSettings.encodedPublicKey = "-----BEGIN CERTIFICATE-----\n"+mDeviceKeys.encodedCertificate()+"-----END CERTIFICATE-----\n";

        // config server
        WifiManager wm = (WifiManager) context.getSystemService(WIFI_SERVICE);
        if(wm != null) {
            mDeviceSettings.ipAddress = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            mWebServer = new DeviceConfigServer(8080, mDeviceSettings, new DeviceEvents(context));
        }

        // beacons
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
        if(bluetoothManager!=null) {
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            mEddystoneAdvertiser = new EddystoneAdvertiser(bluetoothAdapter);
        }
    }

    public boolean isConfigured() {
        return mDeviceSettings.isConfigured();
    }

    public DeviceSettings getDeviceSettings() {
        return mDeviceSettings;
    }

    public DeviceKeys getDeviceKeys() {
        return mDeviceKeys;
    }

    public void enableConfigServer(boolean enable) {
        if(mWebServer!=null) {
            String url;
            if (enable) {
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
    }

    public void resume() {
        enableConfigServer(!isConfigured());
    }

    public void pause() {
        if(mWebServer != null) {
            mWebServer.stop();
        }
        if(mEddystoneAdvertiser !=null) {
            mEddystoneAdvertiser.stopAdvertising();
        }
    }
}
