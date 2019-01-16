package xyz.kripthor.bleexfil;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;

/**
 * Created by kripthor on 21-11-2017.
 */

public class GattCallBack extends BluetoothGattCallback {

    public boolean isConnected;
    public boolean hasColorService;
    public BluetoothGattService colorService;
    public BluetoothGattCharacteristic colorChar;
    public int state = BluetoothProfile.STATE_CONNECTING;

    public GattCallBack() {
        isConnected = false;
        hasColorService = false;
    }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            this.state = newState;
            Log.i("BLE onConnectionStateCh", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("BLE gattCallback", "STATE_CONNECTED");
                    isConnected = true;
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("BLE gattCallback", "STATE_DISCONNECTED");
                    isConnected = false;
                    hasColorService = false;
                    break;
                default:
                    Log.e("BLE gattCallback", "STATE_OTHER");
                    isConnected = false;
                    hasColorService = false;
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            Log.i("BLE onServicesDiscov", services.toString());
            for (BluetoothGattService s:services) {
                Log.i("BLE service", s.getUuid().toString());
                for (BluetoothGattCharacteristic bc:s.getCharacteristics()) {
                    Log.i("BLE chars", bc.getUuid().toString());
                }
            }
            //BluetoothGattService mLightService = gatt.getService(UUID.fromString("00007777-0000-1000-8000-00805f9b34fb"));
            BluetoothGattService mLightService = gatt.getService(UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb"));

            if (mLightService != null) {
                colorService = mLightService;
                //  colorChar = mLightService.getCharacteristic(UUID.fromString("00008877-0000-1000-8000-00805f9b34fb"));
                 colorChar = mLightService.getCharacteristic(UUID.fromString("0000ffe9-0000-1000-8000-00805f9b34fb"));

                hasColorService = (colorChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
                gatt.readCharacteristic(colorChar);
                colorChar.setWriteType(WRITE_TYPE_NO_RESPONSE);
                for (BluetoothGattDescriptor bgd : colorChar.getDescriptors()) {
                    Log.i("BLE descs", bgd.getUuid().toString());
                }

            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            StringBuilder sb = new StringBuilder();
            if (characteristic.getValue() != null) {
                for (byte b : characteristic.getValue()) {
                    sb.append(String.format("%02X ", b));
                }
            }
            Log.i("BLE onCharRead", status+ " "+characteristic.toString() + " value: "+sb.toString());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //Log.i("BLE onCharWrite", status+ " "+characteristic.toString());

        }



}
