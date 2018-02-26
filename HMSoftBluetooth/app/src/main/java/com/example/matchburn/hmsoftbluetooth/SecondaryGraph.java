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
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

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

    //Used for recording data
    private File dirpath;
    private FileOutputStream outputStream;
    private boolean isRecording;

    TextView showValue;
    private BluetoothLeService mBluetoothLeService;

    private BluetoothAdapter mBluetoothAdapter;

    //GraphView variables
    private LineGraphSeries<DataPoint> series;
    private double lastX2 = 0;
    private  GraphView graph;
    private long startTime;
    private boolean startedGraphing;

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
        checkBTAndWritePermissions();

        //the Bluetooth Service should exist by now, so we get it from the application
        mBluetoothLeService = BluetoothApp.getApplication().getService();
        if(BluetoothApp.getApplication().getService() == null)
            Log.i(TAG,"BluetoothLeService is null");
        //No Data
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        //For the graph in this page
        graph = (GraphView) findViewById(R.id.graphView);
        // data
        series = new LineGraphSeries<DataPoint>();
        series.setColor(Color.WHITE);
        graph.addSeries(series);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time (s)");
        graph.getGridLabelRenderer().setVerticalAxisTitle("Current (μA)");
        //graph.getGridLabelRenderer().setLabelHorizontalHeight(20);
        //graph.getGridLabelRenderer().setLabelVerticalWidth(30);
        //graph.getGridLabelRenderer().setLabelsSpace(3);
        graph.getGridLabelRenderer().setNumVerticalLabels(4);

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
        lastX2 = 0;
        graph.removeAllSeries();
        series.resetData(new DataPoint[]{});
        graph.addSeries(series);
        Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(-12);
        viewport.setMaxY(0);
        viewport.setXAxisBoundsManual(true);
        viewport.setMaxX(10);
        viewport.setMinX(0);
        startedGraphing = false;

        //Create file to write on and starts writing
        if(isExternalStorageWritable()) {
            dirpath = createFile();
            try {
                outputStream = new FileOutputStream(dirpath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            isRecording = true;
            //Write a header
            String mess = "Date,Time,Sensing Current,µA\n";
            try {
                //if(outputStream!=null)
                outputStream.write(mess.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;

        //Close writing file
        try {
            outputStream.close();
            outputStream = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause(){
        super.onPause();
        //trackTime.end();
        isRecording = false; //Stop recording when not in foreground
    }

    //Check permissions of device
    private void checkBTAndWritePermissions() {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
            int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            if (permissionCheck != 0) {

                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1); //Any number
            }
        }else{
            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.");
        }
    }

    //Create a file to write on
    private File createFile(){
        String timeDate = "[" + BluetoothApp.getDateString() + " " + BluetoothApp.getTimeString() + "]";
        //Toast.makeText(this,"Clicked!",Toast.LENGTH_LONG).show();
        File Root = Environment.getExternalStorageDirectory();
        File dir = new File(Root.getAbsolutePath() + "/HMSOFTOUT");
        if(!dir.exists())
            dir.mkdir();
        File file = new File(dir,"Sensing" + timeDate + ".txt");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    @Override //Used for checking if the permissions were accepted and obtained
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(SecondaryGraph.this, "Permission denied to read your External storage or Location", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
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
            //BluetoothApp.getApplication().setBluetoothLe(mBluetoothLeService);
            Log.i(TAG,"Connected to hmSoft!");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

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
                showValue.setText(returnedVal);
                if(Main2Activity.checkIfValidDouble(returnedVal)){
                    Log.i(TAG,returnedVal);
                    //Log.i(TAG,"" +  lastX2);
                    returnedValDouble = Double.parseDouble(getValidDouble(returnedVal));
                    if(!startedGraphing){
                        startedGraphing = true;
                        startTime = SystemClock.elapsedRealtime(); //zero time
                    }
                    double currentX = (SystemClock.elapsedRealtime() - startTime) / 1000.0;
                    series.appendData(new DataPoint(currentX,returnedValDouble),true,12);
                    if(outputStream!=null && isRecording){
                        //In format of <X>,<Current Value>\n so it can be read as a csv file
                        String message = BluetoothApp.getDateString() + "," + BluetoothApp.getTimeStringWithColons() + "," + returnedValDouble + "," +
                                Main2Activity.getValueSuffix(returnedVal) + "\n";
                        try {
                            outputStream.write(message.getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    };


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
        Intent intent = new Intent(this,Main2Activity.class);
        startActivity(intent);
    }

}
