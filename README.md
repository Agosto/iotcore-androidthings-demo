# Google Cloud IoT Core Provisioning Android Things Library and Demo App

Android Things that demonstrating how to provision an device into [Google Cloud IoT Core](https://cloud.google.com/iot-core/).

This project is the device half of the Cloud IoT Core demo. The provisioning mobile app can be found here:

- [Google Play](https://play.google.com/store/apps/details?id=com.agosto.iotcoreprovisioning)
- [Github Project](https://github.com/Agosto/iotcore-provisioning-demo) 

## What You Need

### Raspberry Pi 
https://developer.android.com/things/hardware/raspberrypi-kit.html

- Raspberry Pi 3 Model B (other models might work but have not been tested)
- MicroSD card of 16 GB or higher
- Micro USB power adapter.
- (optional) HDMI display and cable
- (optional) [Rainbow HAT](https://shop.pimoroni.com/products/rainbow-hat-for-android-things)

### NXP Pico i.MX7D
https://developer.android.com/things/hardware/imx7d-kit.html

- You need one of the NXP Pico i.MX7D kits found here: (https://www.technexion.com/solutions/iot-development-platform/android-things/)
- All you require is a board and an Antenna for wifi.
- (optional) Rainbowhat, display, and camera.   

## Device Setup

### For Development:

1. Flash Your Raspberry Pi with the latest Android Things Image:
https://developer.android.com/things/hardware/raspberrypi.html

2. Connect device to your wifi network or ethernet

3. Connect with ADB

4. Build/Run!

### For Demo Only:

1.  Download Iot Core Provisioning Demo Image (*coming soon*)

2.  Follow the same step as above to flash your device but use the download image instead.

3.  Add device to wifi network (same as above) or connect via ethernet

4.  Reboot until you see a blue light flash (then it's ready)

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
  "registryId": "a-gateways"
}
```

- `OPTIONS` acts as normal but also lights the LED (if connected) rainbow colors.
- `DELETE` deletes key pairs and device settings and reboots device

4. Once device has a `projectId` and `registryId` (either after a POST or present on startup), then device will:
- Subscribe to the IoT Core device config topic
- Publish data 1/min to the telemetry topic
- reconnect when token expires


### Rainbow HAT
Optionally you can attached a Rainbow Hat to your Raspberry Pi 3 and receive visual feedback.

*Led Strip:* The led strip driver is flaky.  It often throws exceptions when the device first starts. So I add led indicators as well.  They just aren't as fun!

#### LED Indicators
Led Strip Red/Led Red - Device is not ready to operate.  Should only flash for a brief sec on startup.  If persists longer, something is mis-configured (see setup).

Led Strip Blue/Led Blue - Device is ready for provisioning or operation. The LED will flash blue for 5 seconds.

Led Strip Green/Led Green - Device is publishing telemetry data to IOT Core

Led Strip Yellow/Led Blue - Device is receiving a config update from IOT Core.

Led Strip Rainbow/Led RGB - Device is receiving an HTTP `OPTIONS` request

Under normal operation, you should see the following Indicators.

**Unprovisioned Device**
- Blue (5 seconds)

**Provisioned Device**
- Yellow (once on startup and every time a new config is published)
- Green (every 1 min)
