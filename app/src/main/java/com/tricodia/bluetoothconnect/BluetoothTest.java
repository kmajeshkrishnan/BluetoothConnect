package com.tricodia.bluetoothconnect;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class BluetoothTest extends AppCompatActivity {
    BluetoothAdapter bluetoothAdapter;
    final int REQUEST_ENABLE_BT = 99;
    ConnectThread currentThead;
    OutputStream oStream;
    private BroadcastReceiver broadcastReceiver;
    Button btn,send;
    EditText editText;
    TextView txt,locText;
    Switch debugSwitch;
    LinearLayout debugLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn = findViewById(R.id.btn);
        txt = findViewById(R.id.text);
        editText = findViewById(R.id.editText);
        send = findViewById(R.id.sendBtn);
        locText = findViewById(R.id.locText);
        debugLayout = findViewById(R.id.debugLayout);
        debugSwitch = findViewById(R.id.debugSwitch);
        debugSwitch.setChecked(false);

        debugSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b){
                    debugLayout.setVisibility(View.VISIBLE);
                }else{
                    debugLayout.setVisibility(View.GONE);
                }
            }
        });
        if(!runtime_permissions())
            enable_buttons();


        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(oStream!=null){
                    String s = editText.getText().toString();
                    if(!s.equals("")) {
                        byte[] b = s.getBytes();
                        editText.setText("");
                        try {
                            oStream.write(b);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    private void startBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device is not compatible", Toast.LENGTH_SHORT).show();
        }
        else {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            String s = "";
            if (pairedDevices.size() > 0) {
                // There are paired devices. Get the name and address of each paired device.
                for (BluetoothDevice device : pairedDevices) {
                    String deviceName = device.getName();
//                    s = s + "\n" + deviceName;
                    if (deviceName.equals("HC-05")) {
                        currentThead = new ConnectThread(device);
                        currentThead.start();
                    }
                }
                Log.i("Devices ", s);
            }

        }
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e("Ouch", "Socket's create() method failed", e);
            }
                mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                manageMyConnectedSocket(mmSocket);
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e("Ouch", "Could not close the client socket", closeException);
                }
            }

        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("Ouch", "Could not close the client socket", e);
            }
        }
    }

    private void manageMyConnectedSocket (BluetoothSocket mmSocket){
        try {
            if(mmSocket!=null){
                txt.setText("Connected!");
            }
            oStream = mmSocket.getOutputStream();
            byte[] b = "Connected!".getBytes();
            oStream.write(b);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void enable_buttons() {

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (btn.getText().equals("Start")) {
                    Intent i =new Intent(getApplicationContext(),LocService.class);
                    startService(i);
                    startBluetooth();
                    btn.setText("Stop");
                }
                else {

                    Intent i =new Intent(getApplicationContext(),LocService.class);
                    stopService(i);
                    locText.setText("");
                    if(currentThead!=null) {
                        currentThead.cancel();
                    }
                    btn.setText("Start");
                    txt.setText("Searching");
                }
            }
        });


    }

    private boolean runtime_permissions() {
        if(Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){

            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},100);

            return true;
        }
        return false;
    }


    @Override
    protected void onResume() {
        super.onResume();
        if(broadcastReceiver == null){
            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

                    String coordinates = ""+intent.getExtras().get("coordinates");

                    if(oStream!=null){
                        if(!coordinates.equals("")) {
                            byte[] b = coordinates.getBytes();
                            try {
                                oStream.write(b);
                            } catch (IOException e) {
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(),"Location Service Error",Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    locText.setText("\n" +coordinates.replace('$','\n'));

                }
            };
        }
        registerReceiver(broadcastReceiver,new IntentFilter("location_update"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(broadcastReceiver != null){
            unregisterReceiver(broadcastReceiver);
        }
    }


}
