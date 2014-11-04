package com.blemsgfw;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

public class BleMessenger {
	private static String TAG = "blemessenger";
	
	// handles to the device's bluetooth
	private BluetoothManager btMgr;
	private BluetoothAdapter btAdptr;
	private Context ctx;
	
	private boolean messageInbound;
	private boolean messageOutbound;
	
	//  this is defined by the framework, but certainly the developer or user can change it
    private static String uuidServiceBase = "73A20000-2C47-11E4-8C21-0800200C9A66";
    private static MyAdvertiser myGattServer = null;
    
    private BleStatusCallback bleStatusCallback;
        
    private BleMessage blmsgOut;
    private BleMessage blmsgIn;
    
    private String myIdentifier;
    private String myFriendlyName;

    // keep a map of our messages for a connection session - this may not work out; or we may need to keep a map per peer
    private Map<Integer, BleMessage> bleMessageMap;
    
    private Map<String, BlePeer> peerMap;
    
    List<BleCharacteristic> serviceDef;
	
	public BleMessenger(BleMessengerOptions options, BluetoothManager m, BluetoothAdapter a, Context c) {
		myIdentifier = options.Identifier;
		myFriendlyName = options.FriendlyName;
		
		btMgr = m;
		btAdptr = a;
		ctx = c;
		
		serviceDef = new ArrayList<BleCharacteristic>();
		
		// i need a place to put my found peers
		peerMap = new HashMap<String, BlePeer>();
		
		// create your server for listening and your client for looking; Android can be both at the same time
		myGattServer = new MyAdvertiser(uuidServiceBase, ctx, btAdptr, btMgr, defaultHandler);
	
		serviceDef.add(new BleCharacteristic("identifier_read", uuidFromBase("100"), MyAdvertiser.GATT_READ));		
		serviceDef.add(new BleCharacteristic("identifier_writes", uuidFromBase("101"), MyAdvertiser.GATT_READWRITE));
		serviceDef.add(new BleCharacteristic("data_notify", uuidFromBase("102"), MyAdvertiser.GATT_NOTIFY));
		//serviceDef.add(new BleCharacteristic("data_indicate", uuidFromBase("103"), MyAdvertiser.GATT_INDICATE));
		//serviceDef.add(new BleCharacteristic("data_write", uuidFromBase("104"), MyAdvertiser.GATT_WRITE));

		
	}
	
	private UUID uuidFromBase(String smallUUID) {
		String strUUID =  uuidServiceBase.substring(0, 4) + new String(new char[4-smallUUID.length()]).replace("\0", "0") + smallUUID + uuidServiceBase.substring(8, uuidServiceBase.length());
		UUID idUUID = UUID.fromString(strUUID);
		
		return idUUID;
	}
	
	
	public void BeFound() {
		
		
		myGattServer.addChar(MyAdvertiser.GATT_READ, uuidFromBase("100"), controlHandler);
		myGattServer.addChar(MyAdvertiser.GATT_READWRITE, uuidFromBase("101"), controlHandler);
		myGattServer.addChar(MyAdvertiser.GATT_NOTIFY, uuidFromBase("102"), controlHandler);
		
		myGattServer.updateCharValue(uuidFromBase("100"), myIdentifier + "|" + myFriendlyName);
		myGattServer.updateCharValue(uuidFromBase("101"), "i'm listening");
		
		// advertising doesn't take much energy, so go ahead and do it
		myGattServer.advertiseNow();
		
	}
	
	
    private void sendIndicateNotify(UUID uuid) {
    	byte[] nextPacket = blmsgOut.GetPacket().MessageBytes;
    	
    	boolean msgSent = myGattServer.updateCharValue(uuid, nextPacket);
		
    	if (blmsgOut.PendingPacketStatus()) {
    		sendIndicateNotify(uuid);
    	}
    	
    }
    

    MyGattServerHandler defaultHandler = new MyGattServerHandler() {
    	
    	public void handleReadRequest(UUID uuid) { }
    	
    	public void handleNotifyRequest(UUID uuid) { }
    	
    	public void ConnectionState(String dude, int status, int newStatus) {}

		public void incomingBytes(UUID charUUID, byte[] inData) { }
    	
    };

	

	MyGattServerHandler dataHandler = new MyGattServerHandler() {
    	
    	public void handleReadRequest(UUID uuid) {
    		
    		byte[] nextPacket = blmsgOut.GetPacket().MessageBytes;
    		
    		if (blmsgOut.PendingPacketStatus()) {    		
    			Log.v(TAG, "handle read request - call myGattServer.updateCharValue for " + uuid.toString());
    			myGattServer.updateCharValue(uuid, nextPacket);
    		} else {
    			bleStatusCallback.messageSent(uuid);
    		}
    	}
    	
    	public void handleNotifyRequest(UUID uuid) { 
    		
        	byte[] nextPacket = blmsgOut.GetPacket().MessageBytes;
        	boolean msgSent = myGattServer.updateCharValue(uuid, nextPacket);
        	
        	if (msgSent) {        	
        		Log.v(TAG, "client notified with initial message");
        	} else {
        		Log.v(TAG, "client NOT notified with initial message");
        	}
    		
        	// call your self-calling function to keep sending
        	sendIndicateNotify(uuid);
    		
    	}
    	
    	public void ConnectionState(String dude, int status, int newStatus) {}

		public void incomingBytes(UUID charUUID, byte[] inData) { }
    	
    };
    
    MyGattServerHandler controlHandler = new MyGattServerHandler() {
    	
    	public void handleReadRequest(UUID uuid) { }
    	
    	public void handleNotifyRequest(UUID uuid) { }
    	
    	public void ConnectionState(String dude, int status, int newStatus) {}

		public void incomingBytes(UUID charUUID, byte[] inData) { }
    	
    };
    


    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
}
