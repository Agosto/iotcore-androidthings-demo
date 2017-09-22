# Google Cloud IoT Core Demo Android Things App
Android Things that demonstrating how to provision an device into [Google Cloud IoT Core](https://cloud.google.com/iot-core/).

This project is the device half of the Cloud IoT Core demo. The provisioning mobile app can be found here [TODO: LINK TO ANDROID APP](TODO)

## What You Need

- Raspberry Pi 3 Model B (other models might work but have not been tested)
- MicroSD card of 16 GB or higher
- Micro USB power adapter.
- (optional) HDMI display and cable
- (optional) [Blinkt!](https://shop.pimoroni.com/products/blinkt) RGB LED Strip

## Device Setup

https://developer.android.com/things/hardware/raspberrypi.html

### Operation Overview

1. On start attempts to load key pairs and device settings from the filesystem.   If none exist, new ones are generated.
2. Device advertises an eddystone url beacon of the ip address of it's webserver.
3. Webserver can received GET, POST, DELETE, OPTION http request.

-  `GET` returns device settings:
```json
{
  "deviceId": "device-12345678",
  "projectId": "cloud-iot-demo",
  "registryId": "a-gateways",
  "encodedPublicKey": "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n"
}
```

- `POST` accept a json payload to set the `deviceId` and `registryId`

```json
{
  "projectId": "cloud-iot-demo",
  "registryId": "a-gateways",
}
```

- `OPTIONS` acts as normal but also lights the LED (if connected) purple.
- `DELETE` deletes key pairs and device settings and reboots device

4. Once device has a `projectId` and `registryId` (either after a POST or present on startup), then device will:
- Subscribe to the IoT Core device config topic
- Publish data 1/min to the telemetry topic
- reconnect when token expires
