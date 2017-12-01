package com.example.matchburn.hmsoftbluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import static com.example.matchburn.hmsoftbluetooth.Main2Activity.HMSoftAddress;

/**
 * Created by Matchburn321 on 11/28/2017.
 *
 * Displays the graph once the sensors are ready
 */

public class SecondaryGraph extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private final String TAG = ".SecondaryGraph";
    View view;

    TextView showValue;
    private BluetoothLeService mBluetoothLeService;

    private BluetoothAdapter mBluetoothAdapter;

    //GraphView variables
    private LineGraphSeries<DataPoint> series;
    private int lastX = 0;
    private  GraphView graph;
    private timerThread trackTime;

    @Override
    protected void onCreate(Bundle savedInstance){
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_secondary_graph);

        view = this.getWindow().getDecorView();
        view.setBackgroundResource(R.color.Blue);

        showValue = (TextView) findViewById(R.id.data_bar);
        getSupportActionBar().setTitle(R.string.title_devices);

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

        //the Bluetooth Service should exist by now, so we get it from the application
        mBluetoothLeService = BluetoothApp.getApplication().getService();
        showValue.setText("I am done");
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        //For the graph in this page
        graph = (GraphView) findViewById(R.id.graph);
        // data
        series = new LineGraphSeries<DataPoint>();
        series.setColor(Color.WHITE);
        graph.addSeries(series);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time (s)");
        graph.getGridLabelRenderer().setVerticalAxisTitle("Current (mA)");
        //graph.getGridLabelRenderer().setLabelHorizontalHeight(20);
        //graph.getGridLabelRenderer().setLabelVerticalWidth(30);
        //graph.getGridLabelRenderer().setLabelsSpace(3);
        graph.getGridLabelRenderer().setNumVerticalLabels(4);

        //Timer for the graph
        trackTime = new timerThread();

    }

    @Override
    public void onResume(){
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        //For the graph
        lastX = 0;
        graph.removeAllSeries();
        series.resetData(new DataPoint[]{});
        graph.addSeries(series);
        Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(-.04);
        viewport.setMaxY(-.01);
        viewport.setXAxisBoundsManual(true);
        viewport.setMaxX(10);
        viewport.setMinX(0);

        //Keep track of graph timing
        trackTime = new timerThread();
        trackTime.start();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;

    }

    @Override
    public void onPause(){
        super.onPause();
        trackTime.end();
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

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            //mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
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

    //Used to keep timing on the graph
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
                Log.i(TAG,"Connected to HMSOFT!");
                Toast.makeText(getApplicationContext(),"Connected to HMSoft! Looking for data...",Toast.LENGTH_SHORT).show();
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                //mConnected = false;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                Log.i(TAG,"Found Services!");
                //checkIfCharacteristic(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String returnedVal = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                //remove the Ma to convert to an actual usable value
                double returnedValDouble;
                //showData.setText(returnedVal);
                if(checkIfValidDouble(returnedVal)){
                    Log.i(TAG,"" +  lastX);
                    returnedValDouble = Double.parseDouble(getValidDouble(returnedVal));
                    series.appendData(new DataPoint(lastX,returnedValDouble),true,12);

                }
            }
        }
    };

    //Checks that the data is valid (to prevent errors)
    private boolean checkIfValidDouble(String s){
        return s.substring(0,1).equals("0");
    }

    //Filters out the mA and micro symbols out of the string to get a valid double that displays
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

    public void backToMain(View v){
        trackTime.end();
        Intent intent = new Intent(this,Main2Activity.class);
        startActivity(intent);
    }

}
