package com.fieldbook.tracker.vuzix;

/**
 * Created by jessica on 3/29/18.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothServer {

    public static String ACTION_STATUS = "com.fieldbook.tracker.BluetoothServer.action.STATUS_CHANGE";
    public static String ACTION_VOICE_COMMAND = "com.fieldbook.tracker.BluetoothServer.action.VOICE_COMMAND";
    public static String ACTION_DATA_CHANGE = "com.fieldbook.tracker.BluetoothServer.action.DATA_CHANGE";
    public static String ACTION_TRAITS = "com.fieldbook.tracker.BluetoothServer.action.TRAITS";
    public static String ACTION_DEVICE_CHANGE = "com.fieldbook.tracker.BluetoothServer.action.DEVICE_CHANGE";
    public static String EXTRA_VOICE_COMMAND = "com.fieldbook.tracker.BluetoothServer.extras.VOICE_COMMAND";

    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothDevice mDevice = null;
    private BluetoothSocket mSocket;

    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;

    private static final String SERVICE_UUID = "a7f2fdc3-177b-44f7-9930-f64400e04231";
    private static final String TAG = "BluetoothServer";

    private Intent mIntentSender;
    private Intent mIntentVoiceCmd;
    private Context mContext;

    public BluetoothServer(Context context) {
        mContext = context;

        mIntentSender = new Intent();
        mIntentSender.setAction(ACTION_STATUS);
        mIntentVoiceCmd = new Intent();
        mIntentVoiceCmd.setAction(ACTION_VOICE_COMMAND);
    }

    public int init(BluetoothDevice device, BluetoothAdapter adapter) {

        mDevice = device;
        mBluetoothAdapter = adapter;

        if (mDevice == null) {
            Log.e(TAG, "Cannot find pairedDevice");
            return -1;
        }

        mAcceptThread = new AcceptThread(mDevice);
        mAcceptThread.start();

        return 0;
    }

    public void write(byte[] bytes) {
        if (mConnectedThread == null) {
            return;
        }
        mConnectedThread.write(bytes);
    }

    public void cancelConnection() {

        try {

            if (mAcceptThread != null) {
                mAcceptThread.cancel();
                mAcceptThread = null;
            }

            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }

            if (mSocket != null) {
                mSocket.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void manageMyConnectedSocket(BluetoothSocket socket) {

        mSocket = socket;

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }

    private class AcceptThread extends Thread{
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread(BluetoothDevice device) {
            BluetoothServerSocket tmp = null;

            try {
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("BluetoothServer", UUID.fromString(SERVICE_UUID));

                Log.d(TAG, "Bluetooth Server listen success.");
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }

            mmServerSocket = tmp;
        }

        public void run() {

            BluetoothSocket socket = null;

            while (true) {

                try {
                    socket = mmServerSocket.accept();
                } catch(IOException e) {

                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {

                    manageMyConnectedSocket(socket);

                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Close server socket failed", e);
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
                Log.d(TAG, "Bluetooth Server input stream established.");

            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
                Log.d(TAG, "Bluetooth Server output stream established.");

            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

        }

        public void run() {

            // mmBuffer store for the stream
            byte[] mmBuffer = new byte[1024];
            // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {

                try {
                    // Read from the InputStream.
                    int numBytes = mmInStream.read(mmBuffer);
                    String s = new String(mmBuffer);
                    Log.i(TAG, "Receive message from DataReceiver: " + s);
                    mIntentVoiceCmd.putExtra(EXTRA_VOICE_COMMAND, s);
                    mContext.sendBroadcast(mIntentVoiceCmd);

                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    mIntentSender.putExtra("message", "Socket disconnected");
                    mContext.sendBroadcast(mIntentSender);
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}
