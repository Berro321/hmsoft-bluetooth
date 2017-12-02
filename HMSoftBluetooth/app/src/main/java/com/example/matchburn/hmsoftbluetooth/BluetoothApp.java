package com.example.matchburn.hmsoftbluetooth;

import android.app.Application;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Created by Matchburn321 on 11/28/2017.
 * Contains the bluetoothLeService that communicates with the board
 */

public class BluetoothApp extends Application {
    private static BluetoothApp sInstance;

    public static BluetoothApp getApplication(){
        return sInstance;
    }

    BluetoothLeService mBLE = null;
    BluetoothGattCharacteristic mBGC = null;

    public void onCreate(){
        super.onCreate();

        sInstance = this;
    }

    public void setBluetoothLe(BluetoothLeService in){
        mBLE = in;
    }
    public void setBluetoothGattCharacteristic(BluetoothGattCharacteristic in){mBGC=in;}
    public BluetoothLeService getService(){
        return mBLE;
    }
    public BluetoothGattCharacteristic getGattCharacteristic(){return mBGC;}
}
