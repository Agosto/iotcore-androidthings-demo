package com.agosto.iotcorethingsdemo.hats;

import android.util.Log;

public class HatManager {

    private static final String TAG = "HatManager";

    public static final String BLINKT = "blinkt";
    public static final String RAINBOW = "rainbow";

    public interface HatController {

        void ledStripOn(int offDelay);

        void ledStripOn(int offDelay, int color);

        void ledStripOff();

        void redLedOn(int offDelay);

        void blueLedOn(int offDelay);

        void greenLedOn(int offDelay);

        void lightShow(int offDelay);

        void close();
    }

    public static HatController getConnectedHat(String type) {
        if(type.equals(RAINBOW)) {
            return new RainbowHatController();
        }
        if(type.equals(BLINKT)) {
            return new BlinktController();
        }
        return new NoHatController();
    }
}
