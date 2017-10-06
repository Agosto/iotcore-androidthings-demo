package com.agosto.iotcorethings;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * basic pojo for device config data send from iot core
 */

public class IotCoreDeviceConfig {

    @SerializedName("config_server_on")
    @Expose
    public boolean configServerOn = true;
}
