package com.agosto.iotcorethings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.UUID;


public class DeviceSettings {
    private static final String TAG = "DeviceSettings";

    @SerializedName("deviceId")
    @Expose
    public String deviceId = "";

    @SerializedName("encodedPublicKey")
    @Expose
    public String encodedPublicKey = "";

    @SerializedName("projectId")
    @Expose
    public String projectId = "";

    @SerializedName("registryId")
    @Expose
    public String registryId = "";

    public String ipAddress= "";

    private Context context = null;
    // e0b51921-8504-494f-b5aa-f1f055fd41f0

    public static DeviceSettings fromContext(Context context) {
        DeviceSettings deviceSettings = new DeviceSettings(context);
        deviceSettings.loadFromPreferences();
        deviceSettings.generateUUID();
        return deviceSettings;
    }

    public DeviceSettings(Context context) {
        this.context = context;
    }

    private void generateUUID() {
        if(deviceId.isEmpty()) {
            deviceId = "device-" + UUID.randomUUID().toString().substring(0,8);
            saveToPreferences();
        }
        Log.d(TAG,"This devices deviceId is " + deviceId);
    }

    private void loadFromPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        deviceId = sharedPreferences.getString("deviceId","");
        projectId = sharedPreferences.getString("projectId","");
        registryId = sharedPreferences.getString("registryId","");
    }

    private void saveToPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit()
                .putString("deviceId",deviceId)
                .putString("projectId",projectId)
                .putString("registryId",registryId)
                .apply();
        Log.d(TAG,"Device settings saved to preferences");
    }

    public void reset() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    public void setProjectSettings(String projectId, String registryId) {
        this.registryId = registryId;
        this.projectId = projectId;
        saveToPreferences();
        new DeviceEvents(context).broadCastProvisioned();
    }

    public boolean isConfigured() {
        return !projectId.isEmpty() && !registryId.isEmpty();
    }

}
