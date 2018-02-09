package com.agosto.iotcorethings;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.security.PrivateKey;
import java.util.Calendar;
import java.util.Date;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * Utility class for connecting to Google Cloud Iot Core MQTT bridge
 */
public class IotCoreMqtt {
    /** Create a Cloud IoT Core JWT for the given project id, signed with the given private key. */
    private static String createJwtRsa(String projectId, PrivateKey privateKey) throws Exception {
       // DateTime now = new DateTime();
        Calendar rightNow = Calendar.getInstance();
        Date now = rightNow.getTime();
        rightNow.add(Calendar.MINUTE,20);
        Date exp = rightNow.getTime();
        // Create a JWT to authenticate this device. The device will be disconnected after the token
        // expires, and will have to reconnect with a new token. The audience field should always be set
        // to the GCP project id.

        JwtBuilder jwtBuilder =
                Jwts.builder()
                        .setIssuedAt(now)
                        .setExpiration(exp)
                        .setAudience(projectId);

        return jwtBuilder.signWith(SignatureAlgorithm.RS256,privateKey).compact();
    }

    /**
     * Connects to IotCore Mqtt and returns a MqttClient
     * @param projectId projectId of your google cloud project
     * @param registryId IoT core registryId
     * @param deviceId deviceId of this device
     * @param privateKey the device generated privateKey
     * @return a connected MqttClient
     * @throws Exception
     */
    public static MqttClient connect(String projectId, String registryId, String deviceId, PrivateKey privateKey) throws Exception {
        String mqttBridgeHostname = "mqtt.googleapis.com";
        String cloudRegion = "us-central1";
        short mqttBridgePort = 8883;
        // Build the connection string for Google's Cloud IoT Core MQTT server.
        String mqttServerAddress = String.format("ssl://%s:%s", mqttBridgeHostname, mqttBridgePort);

        // Create our MQTT client. The mqttClientId is a unique string that identifies this device. For
        // Google Cloud IoT Core, it must be in the format below.
        String mqttClientId = String.format("projects/%s/locations/%s/registries/%s/devices/%s", projectId, cloudRegion, registryId, deviceId);

        MqttConnectOptions connectOptions = new MqttConnectOptions();
        // Note that the the Google Cloud IoT Core only supports MQTT 3.1.1, and Paho requires that we
        // explictly set this. If you don't set MQTT version, the server will immediately close its
        // connection to your device.
        connectOptions.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

        // With Google Cloud IoT Core, the username field is ignored, however it must be set for the
        // Paho client library to send the password field. The password field is used to transmit a JWT
        // to authorize the device.
        connectOptions.setUserName("unused");

        connectOptions.setPassword(createJwtRsa(projectId, privateKey).toCharArray());

        // Create a client, and connect to the Google MQTT bridge.
        MqttClient client = new MqttClient(mqttServerAddress, mqttClientId, new MemoryPersistence());
        client.connect(connectOptions);
        return client;
    }

    /**
     * The MQTT topic that this device will publish telemetry data to. The MQTT topic name is
     * required to be in the format below. Note that this is not the same as the device registry's
     * Cloud Pub/Sub topic.
     * @param deviceId id of device in registry
     * @return topic string
     */
    public static String telemetryTopic(String deviceId) {
        return String.format("/devices/%s/events", deviceId);
    }

    /**
     * device config topic to receive device config settings
     * @param deviceId id of device in registry
     * @return topic string
     */
    public static String configTopic(String deviceId) {
        return String.format("/devices/%s/config", deviceId);
    }

    /**
     * device state topic to publish state data
     * @param deviceId id of device in registry
     * @return topic string
     */
    public static String stateTopic(String deviceId) {
        return String.format("/devices/%s/state", deviceId);
    }

}
