package com.agosto.iotcorethingsdemo.hats;

import android.graphics.Color;
import android.os.Handler;

import com.google.android.things.contrib.driver.apa102.Apa102;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.pio.Gpio;

import java.io.IOException;

public class RainbowHatController implements HatManager.HatController {

    private Apa102 mLedstrip;

    public void ledStripOn(int offDelay) {
        ledStripOn(offDelay,0);
    }

    public void ledStripOn(int offDelay, int color) {
        Handler handler = new Handler();
        try {
            if(mLedstrip==null)
                mLedstrip = RainbowHat.openLedStrip();
            mLedstrip.setBrightness(5);
            int[] rainbow = new int[RainbowHat.LEDSTRIP_LENGTH];
            for (int i = 0; i < rainbow.length; i++) {
                rainbow[i] = color == 0 ? Color.HSVToColor(255, new float[]{i * 360.f / rainbow.length, 1.0f, 1.0f}) : color;
            }
            mLedstrip.write(rainbow);
            mLedstrip.write(rainbow);
        } catch (IOException e) {
            e.printStackTrace();
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ledStripOff();
            }
        },offDelay);
    }

    public void ledStripOff() {
        if(mLedstrip==null) {
            return;
        }
        try {
            //Apa102 ledstrip = RainbowHat.openLedStrip();
            mLedstrip.setBrightness(0);
            int[] rainbow = new int[RainbowHat.LEDSTRIP_LENGTH];
            for (int i = 0; i < rainbow.length; i++) {
                rainbow[i] = Color.BLACK;
            }
            mLedstrip.write(rainbow);
            mLedstrip.write(rainbow);

// Close the device when done.

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void redLedOn(int offDelay) {
        try {
            ledOn(RainbowHat.openLedRed(), offDelay);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void blueLedOn(int offDelay) {
        try {
            ledOn(RainbowHat.openLedBlue(), offDelay);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void greenLedOn(int offDelay) {
        try {
            ledOn(RainbowHat.openLedGreen(), offDelay);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void lightShow(int offDelay) {
        ledStripOn(offDelay);
        greenLedOn(offDelay);
        blueLedOn(4000);
        redLedOn(3000);
    }

    private void ledOn(final Gpio led, int offDelay) throws IOException {
        led.setValue(true);
        //led.close();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    led.setValue(false);
                    led.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        },offDelay);
    }

    @Override
    public void close() {
        if(mLedstrip!=null) {
            try {
                mLedstrip.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mLedstrip = null;
        }
    }


}
