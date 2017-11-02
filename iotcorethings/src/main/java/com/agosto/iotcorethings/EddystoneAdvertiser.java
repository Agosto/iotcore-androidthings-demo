package com.agosto.iotcorethings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;
import android.webkit.URLUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Most of this is copied over from https://github.com/google/eddystone
 */
public class EddystoneAdvertiser {

    private static final String TAG = "EddystoneAdvertiser";

    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private AdvertiseCallback mAdvertiseCallback;
    private int mTxPowerLevel = AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM;
    private int mAdvertiseMode = AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY;
    private static final ParcelUuid SERVICE_UUID = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");
    private static final byte FRAME_TYPE_UID = 0x00;
    private static final byte FRAME_TYPE_URL = 0x10;

    public EddystoneAdvertiser(BluetoothAdapter bluetoothAdapter) {
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        //Log.d(TAG,"mBluetoothLeAdvertiser " + mBluetoothLeAdvertiser);
        mAdvertiseCallback = createAdvertiseCallback();
    }

    private AdvertiseCallback createAdvertiseCallback() {
        return new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {
                switch (errorCode) {
                    case ADVERTISE_FAILED_DATA_TOO_LARGE:
                        Log.d(TAG,"ADVERTISE_FAILED_DATA_TOO_LARGE");
                        break;
                    case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                        Log.d(TAG,"ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
                        break;
                    case ADVERTISE_FAILED_ALREADY_STARTED:
                        Log.d(TAG,"ADVERTISE_FAILED_ALREADY_STARTED");
                        break;
                    case ADVERTISE_FAILED_INTERNAL_ERROR:
                        Log.d(TAG,"ADVERTISE_FAILED_INTERNAL_ERROR");
                        break;
                    case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                        Log.d(TAG,"ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
                        break;
                    default:
                        Log.d(TAG,"startAdvertising failed with unknown error " + errorCode);
                        break;
                }
            }
        };
    }

    public void startAdvertising(String url) {
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(mAdvertiseMode)
                .setTxPowerLevel(mTxPowerLevel)
                .setConnectable(true)
                .build();


        byte[] serviceData = null;
        try {
            serviceData =  buildUrlServiceData(url);//buildServiceData();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            Log.e(TAG, "failed to build service data");
        }

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .addServiceData(SERVICE_UUID, serviceData)
                .addServiceUuid(SERVICE_UUID)
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(false)
                .build();

        mBluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertiseData, mAdvertiseCallback);
    }

    public void stopAdvertising() {
        Log.i(TAG, "Stopping ADV");
        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /*private byte[] buildServiceData() throws IOException {
        byte txPower = txPowerLevelToByteValue();
        byte[] namespaceBytes = toByteArray("01020304050607080910");
        byte[] instanceBytes = toByteArray("AABBCCDDEEFF");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(new byte[]{FRAME_TYPE_UID, txPower});
        os.write(namespaceBytes);
        os.write(instanceBytes);
        return os.toByteArray();
    }*/

    private byte[] buildUrlServiceData(String uri) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(new byte[]{FRAME_TYPE_URL, txPowerLevelToByteValue()});
        byte[] urlData = encodeUri(uri);
        if (urlData == null) {
            Log.d(TAG,"Could not encode URI " + uri);
        } else {
            os.write(urlData);
        }
        return os.toByteArray();
    }


    static byte[] encodeUri(String uri) {
        if (uri.length() == 0) {
            return new byte[0];
        }
        ByteBuffer bb = ByteBuffer.allocate(uri.length());
        // UUIDs are ordered as byte array, which means most significant first
        bb.order(ByteOrder.BIG_ENDIAN);
        int position = 0;

        // Add the byte code for the scheme or return null if none
        Byte schemeCode = encodeUriScheme(uri);
        if (schemeCode == null) {
            return null;
        }
        String scheme = URI_SCHEMES.get(schemeCode);
        bb.put(schemeCode);
        position += scheme.length();

        if (URLUtil.isNetworkUrl(scheme)) {
            return encodeUrl(uri, position, bb);
        }
        return null;
    }

    private static Byte encodeUriScheme(String uri) {
        String lowerCaseUri = uri.toLowerCase(Locale.ENGLISH);
        for (int i = 0; i < URI_SCHEMES.size(); i++) {
            // get the key and value.
            int key = URI_SCHEMES.keyAt(i);
            String value = URI_SCHEMES.valueAt(i);
            if (lowerCaseUri.startsWith(value)) {
                return (byte) key;
            }
        }
        return null;
    }

    private static byte[] encodeUrl(String url, int position, ByteBuffer bb) {
        while (position < url.length()) {
            byte expansion = findLongestExpansion(url, position);
            if (expansion >= 0) {
                bb.put(expansion);
                position += URL_CODES.get(expansion).length();
            } else {
                bb.put((byte) url.charAt(position++));
            }
        }
        return byteBufferToArray(bb);
    }

    /**
     * Finds the longest expansion from the uri at the current position.
     *
     * @param uriString the Uri
     * @param pos start position
     * @return an index in URI_MAP or 0 if none.
     */
    private static byte findLongestExpansion(String uriString, int pos) {
        byte expansion = -1;
        int expansionLength = 0;
        for (int i = 0; i < URL_CODES.size(); i++) {
            // get the key and value.
            int key = URL_CODES.keyAt(i);
            String value = URL_CODES.valueAt(i);
            if (value.length() > expansionLength && uriString.startsWith(value, pos)) {
                expansion = (byte) key;
                expansionLength = value.length();
            }
        }
        return expansion;
    }

    private static byte[] byteBufferToArray(ByteBuffer bb) {
        byte[] bytes = new byte[bb.position()];
        bb.rewind();
        bb.get(bytes, 0, bytes.length);
        return bytes;
    }


    private static final SparseArray<String> URI_SCHEMES = new SparseArray<String>() {{
        put((byte) 0, "http://www.");
        put((byte) 1, "https://www.");
        put((byte) 2, "http://");
        put((byte) 3, "https://");
        put((byte) 4, "urn:uuid:");    // RFC 2141 and RFC 4122};
    }};

    private byte txPowerLevelToByteValue() {
        switch (mTxPowerLevel) {
            case AdvertiseSettings.ADVERTISE_TX_POWER_HIGH:
                return (byte) -16;
            case AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM:
                return (byte) -26;
            case AdvertiseSettings.ADVERTISE_TX_POWER_LOW:
                return (byte) -35;
            default:
                return (byte) -59;
        }
    }

    /*private byte[] toByteArray(String hexString) {
        // hexString guaranteed valid.
        int len = hexString.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return bytes;
    }(/

    /**
     * Expansion strings for "http" and "https" schemes. These contain strings appearing anywhere in a
     * URL. Restricted to Generic TLDs. <p/> Note: this is a scheme specific encoding.
     */
    private static final SparseArray<String> URL_CODES = new SparseArray<String>() {{
        put((byte) 0, ".com/");
        put((byte) 1, ".org/");
        put((byte) 2, ".edu/");
        put((byte) 3, ".net/");
        put((byte) 4, ".info/");
        put((byte) 5, ".biz/");
        put((byte) 6, ".gov/");
        put((byte) 7, ".com");
        put((byte) 8, ".org");
        put((byte) 9, ".edu");
        put((byte) 10, ".net");
        put((byte) 11, ".info");
        put((byte) 12, ".biz");
        put((byte) 13, ".gov");
    }};


}
