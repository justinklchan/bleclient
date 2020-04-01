package com.example.bleclient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    public static UUID GATT_SERVICE_UUID = UUID.fromString("8cf0282e-d80f-4eb7-a197-e3e0f965848d");
    public static UUID serviceUUID = UUID.fromString("0000F028-0000-1000-8000-00805F9B34FB");
    public static UUID BROADCAST_CHARACTERISTIC_UUID = UUID.fromString("d945590b-5b09-4144-ace7-4063f95bd0bb");
    public static UUID RECEIVER_CHARACTERISTIC_UUID = UUID.fromString("bf112090-9a76-4714-9706-d56444e3fac5");
    BluetoothAdapter blueAdapter;
    BluetoothGatt gatt;
    Context cxt;
    TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.cxt = this;
        setContentView(R.layout.activity_main);
        tv = (TextView)findViewById(R.id.tv);

//        for (String permission : blePermissions) {
//            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
//                Log.e("results", "return false on " + permission);
//            }
//            else {
//                Log.e("results", "return true on " + permission);
//            }
//        }
        ActivityCompat.requestPermissions(this, blePermissions, 1);
    }

    public static String[] blePermissions= {
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    int OP = 0;
    public void read(View v) {
        OP = 0;
        scan();
    }

    public void write(View v) {
        OP = 1;
        scan();
    }

    public void scan() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(serviceUUID))
                .build();
        List<ScanFilter> filters = new LinkedList<ScanFilter>();
        filters.add(filter);

        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        blueAdapter = bluetoothManager.getAdapter();
        Log.e("ble","start scan");
        blueAdapter.getBluetoothLeScanner().startScan(filters, builder.build(), mLeScanCallback);
    }

    public static byte[] uuid2bytes(UUID uuid, byte rssi) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        byte[] uuidbytes=bb.array();
        byte[] dataout=new byte[17];
        for(int i = 0; i < 16; i++) {
            dataout[i]=uuidbytes[i];
        }
        dataout[16]=rssi;
        return dataout;
    }

    public static String byte2string(byte[] b) {
        ByteBuffer bb = ByteBuffer.wrap(b);
        UUID id = new UUID(bb.getLong(),bb.getLong());
        return id.toString();
    }

    ScanCallback mLeScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    super.onScanResult(callbackType, result);
                    BluetoothDevice device = result.getDevice();
                    Map<ParcelUuid, byte[]> map = result.getScanRecord().getServiceData();
                    byte[] data = map.get(new ParcelUuid(serviceUUID));
                    if (data!=null) {
                        String ss = byte2string(data);
                        Log.e("ble", "got device address " + device.getAddress() + "," + ss);
                            Log.e("ble","connect");
                            blueAdapter.getBluetoothLeScanner().stopScan(this);
                            gatt = device.connectGatt(cxt, false, gattCallback);
                            Log.e("ble","gatt "+(gatt==null));
                            boolean status = gatt.connect();
                            Log.e("ble","status "+(status));
                    }
                }
            };

    public BluetoothGattCallback gattCallback =
        new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                int newState) {
                Log.e("ble","onconnectionstatechange");
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.e("ble","connected");
                    boolean disc = gatt.discoverServices();
                }
            }
            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.e("ble","services discovered");
                    List<BluetoothGattService>services=gatt.getServices();
                    for(BluetoothGattService service:services) {
                        Log.e("ble",service.getUuid().toString());
                        if (service.getUuid().equals(GATT_SERVICE_UUID)) {
                            List<BluetoothGattCharacteristic> characs = service.getCharacteristics();
                            for(BluetoothGattCharacteristic charac:characs) {
                                if (charac.getUuid().equals(BROADCAST_CHARACTERISTIC_UUID) && OP == 0) {
                                    Log.e("ble","going to read char");
                                    boolean b = gatt.readCharacteristic(charac);
                                    Log.e("ble","status "+b);
                                }
                                if (charac.getUuid().equals(RECEIVER_CHARACTERISTIC_UUID) && OP == 1) {
                                    final UUID uuid = UUID.randomUUID();
                                    Log.e("ble","going to write char "+uuid.toString());

                                    ((MainActivity)cxt).runOnUiThread(new Runnable() {
                                        public void run() {
                                            tv.setText(tv.getText().toString() + "wrote " + uuid.toString() + "\n");
                                        }
                                    });

                                    byte[] data = uuid2bytes(uuid,(byte)40);
                                    charac.setValue(data);
                                    boolean b = gatt.writeCharacteristic(charac);
                                    gatt.disconnect();
                                }
                            }
                            break;
                        }
                    }
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic charac, int status) {
                super.onCharacteristicRead(gatt, charac, status);
                Log.e("ble","char read "+charac.getUuid().toString());
                if (charac.getUuid().equals(BROADCAST_CHARACTERISTIC_UUID)) {
                    byte[] bb = charac.getValue();
                    if (bb != null) {
                        final String contactUUID = byte2string(bb);
                        ((MainActivity)cxt).runOnUiThread(new Runnable() {
                            public void run() {
                                tv.setText(tv.getText().toString()+"read "+contactUUID+"\n");
                            }
                        });

                        Log.e("ble", "** read this contact ID: " + contactUUID);
                        gatt.disconnect();
                    } else {
                        Log.e("ble", "** bb is null");
                    }
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic charac, int status) {
                super.onCharacteristicWrite(gatt, charac, status);
                Log.e("ble","char write "+charac.getUuid().toString());
                if (charac.getUuid().equals(RECEIVER_CHARACTERISTIC_UUID)) {
                    Log.e("ble", "** setting value "+status);
                }
            }
        };}
