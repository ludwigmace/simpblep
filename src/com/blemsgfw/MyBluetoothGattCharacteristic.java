package com.blemsgfw;

import java.util.UUID;

import android.bluetooth.BluetoothGattCharacteristic;

public class MyBluetoothGattCharacteristic extends BluetoothGattCharacteristic {

	public MyBluetoothGattCharacteristic(UUID uuid, int properties, int permissions) {
		super(uuid, properties, permissions);
	}
	
	public MyBluetoothGattCharacteristic(UUID uuid, int properties, int permissions, MyGattServerHandler cHandler) {
		this(uuid, properties, permissions);
		charHandler = cHandler;
	}
	
	public MyGattServerHandler charHandler;
	
}
