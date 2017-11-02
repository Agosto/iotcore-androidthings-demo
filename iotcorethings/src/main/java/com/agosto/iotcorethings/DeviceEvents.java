package com.agosto.iotcorethings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

public class DeviceEvents {

    public static final String DEVICE_PROVISIONED = "com.agosto.iotcorethings.DEVICE_PROVISIONED";
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

    /*public void registerReceiver(BroadcastReceiver receiver, String event) {
        mLocalBroadcastManager.registerReceiver(receiver,new IntentFilter(event));
    }

    public void registerProvisionedReceiver(BroadcastReceiver receiver) {
        registerReceiver(receiver,DEVICE_PROVISIONED);
    }

    public void registerIdentifyRequestReceiver(BroadcastReceiver receiver) {
        registerReceiver(receiver,IDENTIFY_REQUEST);
    }

    public void unregisterReceiver(BroadcastReceiver receiver) {
        mLocalBroadcastManager.unregisterReceiver(receiver);
    }*/

}
