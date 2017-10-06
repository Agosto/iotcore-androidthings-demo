package com.agosto.iotcorethings;

import android.text.TextUtils;
import android.util.Log;

import com.google.android.things.devicemanagement.DeviceManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class DeviceConfigServer implements Runnable {

    private static final String TAG = "DeviceConfigServer";
    private DeviceSettings mDeviceSettings;

    /**
     * The port number we listen to
     */
    private final int mPort;


    /**
     * True if the server is running.
     */
    private boolean mIsRunning;

    /**
     * The {@link java.net.ServerSocket} that we listen to.
     */
    private ServerSocket mServerSocket;

    /**
     * WebServer constructor.
     */
    public DeviceConfigServer(int port, DeviceSettings deviceSettings) {
        mPort = port;
        mDeviceSettings = deviceSettings;
    }

    /**
     * This method starts the web server listening to the specified port.
     */
    public void start() {
        if(mIsRunning) {
            Log.d(TAG,"server is already running");
        } else {
            mIsRunning = true;
            new Thread(this).start();
        }
    }

    /**
     * This method stops the web server
     */
    public void stop() {
        if(!mIsRunning) {
            Log.d(TAG,"server is NOT running");
            return;
        }
        try {
            mIsRunning = false;
            if (null != mServerSocket) {
                mServerSocket.close();
                mServerSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing the server socket.", e);
        }
    }

    public int getPort() {
        return mPort;
    }

    @Override
    public void run() {
        try {
            mServerSocket = new ServerSocket(mPort);
            while (mIsRunning) {
                Socket socket = mServerSocket.accept();
                handle(socket);
                socket.close();
            }
        } catch (SocketException e) {
            // The server was stopped; ignore.
        } catch (IOException e) {
            Log.e(TAG, "Web server error.", e);
        }
    }

    /**
     * Respond to a request from a client.
     *
     * @param socket The client socket.
     * @throws IOException
     */
    private void handle(Socket socket) throws IOException {
        BufferedReader reader = null;
        PrintStream output = null;
        try {
            // Read HTTP headers and parse out the route.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            int contentLength = 0;
            String method = "GET";
            final String contentHeader = "Content-Length: ";
            while (!TextUtils.isEmpty(line = reader.readLine())) {
                Log.d(TAG,line);
                if (line.startsWith("GET")) {
                    break;
                } else if (line.startsWith("DELETE")) {
                    method = "DELETE";
                } else if (line.startsWith("POST")) {
                    method = "POST";
                } else if (line.startsWith(contentHeader)) {
                    contentLength = Integer.parseInt(line.substring(contentHeader.length()));
                }
            }

            if (method.equals("POST")) {
                StringBuilder body = new StringBuilder();
                int c = 0;
                for (int i = 0; i < contentLength; i++) {
                    c = reader.read();
                    body.append((char) c);
                    //Log.d(TAG, "POST: " + ((char) c) + " " + c);
                }
                Log.d(TAG,body.toString());
                DeviceUpdate deviceUpdate = new Gson().fromJson(body.toString(),DeviceUpdate.class);
                Log.d(TAG,deviceUpdate.projectId);
                mDeviceSettings.setProjectSettings(deviceUpdate.projectId,deviceUpdate.registryId);
            }

            if(method.equals("DELETE")) {
                Log.d(TAG,"Resetting Device");
                mDeviceSettings.reset();
                DeviceKeys.deleteKeys();
                DeviceManager.reboot();
            }

            this.deviceRepResponse(socket);
        } finally {
            if (null != reader) {
                reader.close();
            }
        }
    }

    protected void deviceRepResponse(Socket socket) throws IOException {
        PrintStream output = null;
        try {
            output = new PrintStream(socket.getOutputStream());
            DeviceSettings outDeviceSettings = mDeviceSettings;
            if(mDeviceSettings.isConfigured()) {
                outDeviceSettings = new DeviceSettings(null);
                outDeviceSettings.deviceId = mDeviceSettings.deviceId;
                outDeviceSettings.projectId = mDeviceSettings.projectId;
                outDeviceSettings.registryId = mDeviceSettings.registryId;
                outDeviceSettings.encodedPublicKey = "already registered";
            }

            byte[] bytes = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(outDeviceSettings).getBytes(); //loadContent(route);
            if (null == bytes) {
                writeServerError(output);
                return;
            }

            // Send out the content.
            output.println("HTTP/1.0 200 OK");
            //output.println("Content-Type: text/plain");
            output.println("Content-Type: application/json");
            output.println("Content-Length: " + bytes.length);
            output.println();
            output.write(bytes);
            output.flush();
        } finally {
            if (null != output) {
                output.close();
            }
        }
    }

    /**
     * Writes a server error response (HTTP/1.0 500) to the given output stream.
     *
     * @param output The output stream.
     */
    private void writeServerError(PrintStream output) {
        output.println("HTTP/1.0 500 Internal Server Error");
        output.flush();
    }

    static class DeviceUpdate {
        String projectId = "";
        String registryId = "";
    }

    public boolean isRunning() {
        return mIsRunning;
    }
}


