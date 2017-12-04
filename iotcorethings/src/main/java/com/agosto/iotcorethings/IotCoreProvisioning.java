package com.agosto.iotcorethings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;

import static android.content.Context.BLUETOOTH_SERVICE;
import static android.content.Context.WIFI_SERVICE;


public class IotCoreProvisioning {
    private static IotCoreProvisioning ourInstance = null;
    private static final String TAG = "IotCoreProvisioning";

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

        mDeviceSettings.ipAddress = getIpAddress();
        // config server
        /*WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);
        if(wm != null) {
            mDeviceSettings.ipAddress = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            mWebServer = new DeviceConfigServer(8080, mDeviceSettings, new DeviceEvents(context));
        }*/
        if(!mDeviceSettings.ipAddress.isEmpty()) {
            mWebServer = new DeviceConfigServer(8080, mDeviceSettings, new DeviceEvents(context));
        }

        // beacons
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
        if(bluetoothManager!=null) {
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            mEddystoneAdvertiser = new EddystoneAdvertiser(bluetoothAdapter);
        }
    }

    private String getIpAddress() {
        Enumeration<NetworkInterface> nwis;
        try {
            nwis = NetworkInterface.getNetworkInterfaces();
            while (nwis.hasMoreElements()) {
                NetworkInterface ni = nwis.nextElement();
                Log.d(TAG,String.format("testing %s",ni));
                if(ni.getDisplayName().equals("eth0") || ni.getDisplayName().equals("wlan0")) {
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        if(ia.getNetworkPrefixLength()==24) {
                            Log.d(TAG,String.format("found ip %s",ia.getAddress().getHostAddress()));
                            return ia.getAddress().getHostAddress();
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
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
