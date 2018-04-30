package com.agosto.iotcorethingsdemo.hats;

import android.graphics.Color;
import android.os.Handler;
import android.util.Log;

import com.nilhcem.androidthings.driver.blinkt.Blinkt;

import java.io.IOException;

public class BlinktController implements HatManager.HatController {

    private static final String TAG = "BlinktController";
    
    @Override
    public void ledStripOn(int offDelay) {
        ledStripOn(offDelay,0);
    }

    @Override
    public void ledStripOff() {
        setLedStripColor(Color.TRANSPARENT);
    }

    @Override
    public void redLedOn(int offDelay) {
        ledStripOn(offDelay,Color.RED);
    }

    @Override
    public void blueLedOn(int offDelay) {
        ledStripOn(offDelay,Color.BLUE);
    }

    @Override
    public void greenLedOn(int offDelay) {
        ledStripOn(offDelay,Color.GREEN);
    }

    @Override
    public void lightShow(int offDelay) {
        int[] rainbow = new int[Blinkt.LEDSTRIP_LENGTH];
        for (int i = 0; i < rainbow.length; i++) {
            rainbow[i] = Color.HSVToColor(255, new float[]{i * 360.f / rainbow.length, 1.0f, 1.0f});
        }
        setLedStripColor(rainbow);
        ledStripOff(offDelay);
    }

    @Override
    public void close() {

    }

    @Override
    public void ledStripOn(int offDelay, int color) {
        setLedStripColor(color);
        ledStripOff(offDelay);
    }

    private void ledStripOff(int offDelay) {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                setLedStripColor(Color.TRANSPARENT);
            }
        },offDelay);
    }

    private void setLedStripColor(int color) {
        setLedStripColor(new int[]{color, color, color, color, color, color, color, color});
    }

    private void setLedStripColor(int[] colors) {
        try {
            Blinkt blinkt = new Blinkt();
            blinkt.setBrightness(1);
            blinkt.write(colors);
            blinkt.close();
        } catch (IOException e) {
            Log.d(TAG,"No Blinkt connected");
            e.printStackTrace();
        }
    }
}
