package com.agosto.iotcorethings;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

public class DeviceEvents {

    public static final String DEVICE_PROVISIONED = "com.agosto.iotcorethings.DEVICE_PROVISIONED";
    public static final String DEVICE_RESET = "com.agosto.iotcorethings.DEVICE_RESET";
    public static final String IDENTIFY_REQUEST = "com.agosto.iotcorethings.IDENTIFY_REQUEST";

    private LocalBroadcastManager mLocalBroadcastManager;

    public DeviceEvents(Context context) {
        this(LocalBroadcastManager.getInstance(context));
    }

    public DeviceEvents(LocalBroadcastManager localBroadcastManager) {
        mLocalBroadcastManager = localBroadcastManager;
    }

    public void broadCastProvisioned() {
        mLocalBroadcastManager.sendBroadcast(new Intent(DEVICE_PROVISIONED));
    }

    public void broadCastIdentifyRequest() {
        mLocalBroadcastManager.sendBroadcast(new Intent(IDENTIFY_REQUEST));
    }

    public void broadCastDeviceReset() {
        mLocalBroadcastManager.sendBroadcast(new Intent(DEVICE_RESET));
    }
}
