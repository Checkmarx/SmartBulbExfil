package xyz.kripthor.bleexfil;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static java.lang.Thread.sleep;

/**
 * Created by kripthor on 20-11-2017.
 */
public class MainActivity extends Activity {
    private BlueThread mBlueT;
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 100000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        new Thread(new Runnable(){
            @Override
            public void run () {
                try {
                    while(true) {
                        sleep(1000);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mBlueT != null) {
                                    ((EditText) findViewById(R.id.editText2)).setText(mBlueT.statusText);
                                    mBlueT.exfilData = (((EditText) findViewById(R.id.editText)).getText()).toString();
                                    mBlueT.stealth = ((ToggleButton) findViewById(R.id.toggleButton2)).isChecked();
                                    mBlueT.mac = (((EditText) findViewById(R.id.editText3)).getText()).toString();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }).start();

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }



    @Override
    protected void onPause() {
        mBlueT.pause();
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        mBlueT.die();
        super.onDestroy();
    }


    public void toogleClick(View v) {
        Log.d("BLE click", "clickity");
        boolean checked = ((ToggleButton) findViewById(R.id.toggleButton)).isChecked();
        if (checked) {
            if (mBlueT == null) {
                mBlueT = new BlueThread(this, mBluetoothAdapter);
                mBlueT.exfilData = (((EditText) findViewById(R.id.editText)).getText()).toString();
                mBlueT.stealth = ((ToggleButton) findViewById(R.id.toggleButton2)).isChecked();
                mBlueT.start();
            }
        } else {
            if (mBlueT != null) {
                mBlueT.die();
                mBlueT = null;
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


}