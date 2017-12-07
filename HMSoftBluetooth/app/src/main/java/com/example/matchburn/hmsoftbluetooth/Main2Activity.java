package com.example.matchburn.hmsoftbluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.w3c.dom.Text;

import java.io.IOError;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
/*
    Main Activity that will send Input to the board

    Created by: Betto Cerrillos and Francisco Ramirez
 */

public class Main2Activity extends AppCompatActivity {

    View view;

    TextView showValue;
    TextView showData;
    int counter = 0;

    //Needed for Bluetooth
    private int count; //Prevent from scanning forever
    private boolean readyTo;
    private boolean foundChar;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private boolean ThreadIsAlive = false;

    private ArrayList<String> deviceList;
    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private static final String TAG = "Main2Activity";

    //Information about board
    public static final String HMSoftAddress = "9C:1D:04:E0:BD";
    public static final String HMSoftServ = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public  static final String HMSoftChar = "0000ffe1-0000-1000-8000-00805f9b34fb";

    //Needed after HMSoft is connected
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic bluetoothGattCharacteristicHM_SOFT;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    //GraphView variables
    private LineGraphSeries<DataPoint> series;
    private double lastX = 0;
    private  GraphView graph;
    private timerThread trackTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        deviceList = new ArrayList<>();
        count = 0;

        readyTo = false; //Track once it's connected
        foundChar = false;

        view = this.getWindow().getDecorView();
        view.setBackgroundResource(R.color.Blue);

        showValue = (TextView) findViewById(R.id.valueCounter);
        showData = (TextView) findViewById(R.id.data);

        //Bluetooth stuff
        getSupportActionBar().setTitle(R.string.title_devices);
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        checkBTPermissions();
        //Disable bluetooth, to restart it
        /*BluetoothAdapter mBluetoothAdapter2 = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter2.isEnabled()) {
            mBluetoothAdapter2.disable();
        }*/
        //Setting up the Graph View
        // we get graph view instance
       graph = (GraphView) findViewById(R.id.graph);
        //Setup how the graph looks
        series = new LineGraphSeries<DataPoint>();
        series.setColor(Color.WHITE);
        graph.addSeries(series);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time (s)");
        graph.getGridLabelRenderer().setVerticalAxisTitle("Current (mA)");
        graph.getGridLabelRenderer().setLabelHorizontalHeight(20);
        graph.getGridLabelRenderer().setLabelVerticalWidth(30);
        graph.getGridLabelRenderer().setLabelsSpace(3);
        graph.getGridLabelRenderer().setNumVerticalLabels(4);

        trackTime = new timerThread();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;

    }

    //Check permissions of device
    private void checkBTPermissions() {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
            int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            if (permissionCheck != 0) {

                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number
            }
        }else{
            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastX = 0;
        graph.removeAllSeries();
        series.resetData(new DataPoint[]{});
        graph.addSeries(series);
        Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(.01);
        viewport.setMaxY(.04);
        viewport.setXAxisBoundsManual(true);
        viewport.setMaxX(10);
        viewport.setMinX(0);


        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled() && !readyTo) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        //Start scanning for devices
        if(!readyTo && BluetoothApp.getApplication().getService()==null)
            scanLeDevice(true);

        if(readyTo || BluetoothApp.getApplication().getService()!=null){
            foundChar= true;
            //checkIfCharacteristic(BluetoothApp.getApplication().getService().getSupportedGattServices());
            //If restarted, bypass scanning
            if(BluetoothApp.getApplication().getService()!=null && mBluetoothLeService==null) {
                bluetoothGattCharacteristicHM_SOFT = BluetoothApp.getApplication().getGattCharacteristic();
                mBluetoothLeService = BluetoothApp.getApplication().getService();
                Log.i(TAG,"mBluetoothLeService has been set, null?: " + (mBluetoothLeService==null));
            }
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            if (mBluetoothLeService != null) {
                final boolean result = mBluetoothLeService.connect(HMSoftAddress);
                Log.d(TAG, "Connect request result=" + result);
            }
        }

        //Keep track of graph timing
        if(ThreadIsAlive) {
            trackTime = new timerThread();
            trackTime.start();
        }

    }
    private class timerThread extends Thread implements Runnable{
        private volatile boolean exit = false;
        @Override
        public void run() {
            // we go on forever
            for (;;) {
                if(exit)
                    break;
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        lastX += .1;
                    }
                });

                // sleep to slow down the add of entries
                //Not always 100% accurate TODO: different way
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // manage error ...
                    Log.i(TAG,"ERROR IN THREAD!");
                }
            }
        }
        public void end(){
            exit = true;
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        trackTime.end();

        //trackTime.interrupt();
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                    //Start scanning again,
                    count++;
                    if(count < 3) {
                        Log.i(TAG,"Did not find HMSoft device, searching again");
                        scanLeDevice(true);
                    }
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    private void checkDeviceName(BluetoothDevice device){
        //Prevent spam of the log
        if(device.getAddress()!=null&& ! deviceList.contains(device.getAddress())) {
            Log.i(TAG, "Found Device: " + device.getName() + "\n" + device.getAddress());
            deviceList.add(device.getAddress());
            if(device.getAddress().equals(HMSoftAddress)){
                Log.i(TAG,"Found HMSoft!");
                count = 3;
                if (mScanning) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    mScanning = false;
                }
                readyTo = true;
                Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
                bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            }
        }
        else
            return;
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            checkDeviceName(device);
                        }
                    });
                }
    };

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(HMSoftAddress);
            //Used so it can later be accessed in another activity
            BluetoothApp.getApplication().setBluetoothLe(mBluetoothLeService);
            Log.i(TAG,"Connected to hmSoft!");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    public void countIN (View v) {
        if(counter >= 9){
            counter = 1;
        }
        else{
            counter++;
        }
        showValue.setText(Double.toString(intToDouble(counter)));
        sendData(counter);

        //Toast.makeText(getApplicationContext(),String.valueOf(counter),Toast.LENGTH_LONG).show();
    }
    public void countDE (View v) {
        if(counter <= 1){
            counter = 9;
        }
        else{
            counter--;
        }
        showValue.setText(Double.toString(intToDouble(counter)));
        sendData(counter);
        //Toast.makeText(getApplicationContext(),String.valueOf(counter),Toast.LENGTH_LONG).show();
    }

    public double intToDouble(int num){
        switch(num){
            case 1:
                return 0.1;
            case 2:
                return 0.2;
            case 3:
                return 0.3;
            case 4:
                return 0.4;
            case 5:
                return 0.5;
            case 6:
                return 0.6;
            case 7:
                return 0.7;
            case 8:
                return 0.8;
            case 9:
                return 0.9;
            default:
                return -1;
        }
    }



    public void startSensingClick(View v){
        sendData(0);
        Log.i(TAG,"Start sensing button clicked");
        if(foundChar)
            startGraph();
    }

    public void startGraph(){

        Log.i(TAG,"Starting other graph...");
        //Make sure that the application is actually connected to the device
        if(BluetoothApp.getApplication().getService()==null)
            return;
        //Make sure timer ends on time
        trackTime.end();
        Intent intent = new Intent(this,SecondaryGraph.class);
        startActivity(intent);
    }

    private void sendData(int dataInt){
        if(!foundChar)
            return;
        Log.i(TAG,"Sent data " + dataInt);
        bluetoothGattCharacteristicHM_SOFT.setValue(Integer.toString(dataInt));
        mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristicHM_SOFT);
        mBluetoothLeService.setCharacteristicNotification(bluetoothGattCharacteristicHM_SOFT, true);
        //Toast.makeText(this,"Sent value " + dataInt, Toast.LENGTH_SHORT).show();
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                Log.i(TAG,"Connected to HMSOFT!");
                Toast.makeText(getApplicationContext(),"Connected to HMSoft!",Toast.LENGTH_SHORT).show();
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                Log.i(TAG,"Found Services!");
                checkIfCharacteristic(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                //Read current
                String returnedVal = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                //remove the Ma to convert to an actual usable value
                double returnedValDouble;
                if(!ThreadIsAlive) {
                    trackTime.start();
                    ThreadIsAlive = true;
                }
                showData.setText(returnedVal);
                if(checkIfValidDouble(returnedVal)){
                    //Log.i(TAG,"" +  lastX);
                    returnedValDouble = Double.parseDouble(getValidDouble(returnedVal));
                    series.appendData(new DataPoint(lastX,returnedValDouble),true,12);

                }
            }
        }
    };

    private boolean checkIfValidDouble(String s){
        return s.substring(0,1).equals("0");
    }

    private String getValidDouble(String s){
        String returnedString = "";
        for(int i = 0; i < s.length(); i++){
            String current = s.substring(i,i+1);
            if(current.equals(" ") || current.equals("μ") || current.equals("m"))
                break;
            returnedString+=current;
        }
        return returnedString;
    }

    //Checks to see if it is the data from the board we need (current)
    private void checkIfCharacteristic(List<BluetoothGattService> gattServices){
        if(gattServices==null || foundChar)
            return;
        Log.i(TAG,"Checking characteristics...");
        String tempUUID;
        UUID UUID_HM_SOFT = UUID.fromString(HMSoftChar);
        //Loop through services
        for(BluetoothGattService gattService : gattServices){
            tempUUID = gattService.getUuid().toString();
            Log.i(TAG,"Service: " + tempUUID);
            if(tempUUID.equals(HMSoftServ)){
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                //Loop through characteristics
                for(BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics){
                    tempUUID = gattCharacteristic.getUuid().toString();
                    Log.i(TAG,"Characteristic: " + tempUUID);
                    if(tempUUID.equals(HMSoftChar)){
                        Log.i(TAG,"Found Characteristics, Reading....");
                        //Toast.makeText(getApplicationContext(),"Found data!",Toast.LENGTH_SHORT).show();
                        foundChar = true;
                        Log.i(TAG,"Obtained characteristic");
                        bluetoothGattCharacteristicHM_SOFT = gattService.getCharacteristic(UUID_HM_SOFT);
                        //Add to application file
                        BluetoothApp.getApplication().setBluetoothGattCharacteristic(bluetoothGattCharacteristicHM_SOFT);
                        activateCharacteristic(gattCharacteristic);
                    }
                }
            }
        }
    }

    //Start reading the data from the board
    private void activateCharacteristic(BluetoothGattCharacteristic gattChar){
        final BluetoothGattCharacteristic characteristic =
                gattChar;
        final int charaProp = characteristic.getProperties();
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
            // If there is an active notification on a characteristic, clear
            // it first so it doesn't update the data field on the user interface.
            if (mNotifyCharacteristic != null) {
                mBluetoothLeService.setCharacteristicNotification(
                        mNotifyCharacteristic, false);
                mNotifyCharacteristic = null;
            }
            mBluetoothLeService.readCharacteristic(characteristic);
        }
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            mNotifyCharacteristic = characteristic;
            mBluetoothLeService.setCharacteristicNotification(
                    characteristic, true);
        }
        foundChar = true;
    }
}
