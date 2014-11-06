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
    
    private int CurrentParentMessage;
    private BlePeer CurrentPeer;

    // keep a map of our messages for a connection session - this may not work out; or we may need to keep a map per peer
    private Map<Integer, BleMessage> bleMessageMap;
    
    private Map<String, BlePeer> peerMap;
    
    
	public BleMessage idMessage;
    
    List<BleCharacteristic> serviceDef;
	
	public BleMessenger(BleMessengerOptions options, BluetoothManager m, BluetoothAdapter a, Context c, BleStatusCallback BleStatusCallback) {
		myIdentifier = options.Identifier;
		myFriendlyName = options.FriendlyName;
		
		bleStatusCallback = BleStatusCallback;
		btMgr = m;
		btAdptr = a;
		ctx = c;
		
		serviceDef = new ArrayList<BleCharacteristic>();
		
		// i need a place to put my found peers
		peerMap = new HashMap<String, BlePeer>();
		
		// create your server for listening and your client for looking; Android can be both at the same time
		myGattServer = new MyAdvertiser(uuidServiceBase, ctx, btAdptr, btMgr, defaultHandler);
	
		serviceDef.add(new BleCharacteristic("identifier_read", uuidFromBase("100"), MyAdvertiser.GATT_READ));		
		serviceDef.add(new BleCharacteristic("identifier_writes", uuidFromBase("101"), MyAdvertiser.GATT_WRITE));
		serviceDef.add(new BleCharacteristic("data_notify", uuidFromBase("102"), MyAdvertiser.GATT_NOTIFY));
		//serviceDef.add(new BleCharacteristic("data_indicate", uuidFromBase("103"), MyAdvertiser.GATT_INDICATE));
		//serviceDef.add(new BleCharacteristic("data_write", uuidFromBase("104"), MyAdvertiser.GATT_WRITE));

	
		// when we connect, send the id message to the connecting party
	}
	
	private UUID uuidFromBase(String smallUUID) {
		String strUUID =  uuidServiceBase.substring(0, 4) + new String(new char[4-smallUUID.length()]).replace("\0", "0") + smallUUID + uuidServiceBase.substring(8, uuidServiceBase.length());
		UUID idUUID = UUID.fromString(strUUID);
		
		return idUUID;
	}
	
	public void AddMessage() {
	    
	}
	

	
	public void BeFound() {
	
		
		// have this pull from the service definition
		myGattServer.addChar(MyAdvertiser.GATT_READ, uuidFromBase("100"), defaultHandler);
		myGattServer.addChar(MyAdvertiser.GATT_WRITE, uuidFromBase("101"), defaultHandler);
		myGattServer.addChar(MyAdvertiser.GATT_NOTIFY, uuidFromBase("102"), defaultHandler);
		
		myGattServer.updateCharValue(uuidFromBase("100"), new String(myIdentifier + "|" + myFriendlyName).getBytes());
		myGattServer.updateCharValue(uuidFromBase("101"), new String("i'm listening").getBytes());
		
		// advertising doesn't take much energy, so go ahead and do it
		myGattServer.advertiseNow();
		
	}
	
	// maybe we shouldn't use this to send our identity stuff . . .
    private void sendIndicateNotify(String remote, UUID uuid) {
    	
    	// if we've got messages to send
    	if (bleMessageMap.size() > 0) {
    	
    		// get the current message to send
	    	blmsgOut = bleMessageMap.get(CurrentParentMessage);
	    	
	    	// get the next packet to send
	    	byte[] nextPacket = blmsgOut.GetPacket().MessageBytes;
	    	
	    	// update the value of this characteristic, which will send to subscribers
	    	Log.v(TAG, "send next packet");
	    	myGattServer.updateCharValue(uuid, nextPacket);
	    	
	    	if (!blmsgOut.PendingPacketStatus()) {
	    		Log.v(TAG, "message is sent, remove it from the map");
	    		// if this message is sent, remove from our Map queue and increment our counter
	    		bleMessageMap.remove(CurrentParentMessage);
	    		CurrentParentMessage++;
	    	} 
	    	
    		Log.v(TAG, "recurse!");
    		sendIndicateNotify(remote, uuid);
	    	
    	}/* else {
    		// if we've got no more messages, then we need to call a disconnect
    		// however if we 
    		myGattServer.closeConnection();
    	}*/
    	
    }
    
    public void HandleIncomingID() {
		
		// now for this peer let's go ahead and pull all his messages and add them to our map
		BlePeer p;
		
		String puKfingerprint = "";
		p = peerMap.get(puKfingerprint);  // won't work, pulls of bt address
		
		// loop over p.getMessage(i) or something and do a bleMessageMap.put(x, msg) foreach
		// . . . incrementing CurrentParentMessage each time
		
		// hole up, we don't have a peermap before this connection, at least based on device address
		// it'll have to be based on ID sent to us, ie the PuKfp
    }

    MyGattServerHandler defaultHandler = new MyGattServerHandler() {
    	
    	public void handleReadRequest(UUID uuid) {
    		
    		byte[] nextPacket = blmsgOut.GetPacket().MessageBytes;
    		
    		if (blmsgOut.PendingPacketStatus()) {    		
    			Log.v(TAG, "handle read request - call myGattServer.updateCharValue for " + uuid.toString());
    			myGattServer.updateCharValue(uuid, nextPacket);
    		} else {
    			bleStatusCallback.messageSent(uuid);
    		}
    	}
    	
    	public void ConnectionState(String device, int status, int newStatus) {
    		
    		if (newStatus == 2) {
    		
	    		// create/reset our message map for our connection
	    		bleMessageMap =  new HashMap<Integer, BleMessage>();
	    		CurrentParentMessage = 0;
	    		
	    		// the message itself needs to know what it's sequence is when sent to recipient
	    		idMessage.SetMessageNumber(CurrentParentMessage);
	    		// add our id message to this message map
	    		bleMessageMap.put(0, idMessage);
	    		
	    		Log.v(TAG, "id message added to connection's message map");
	    		
	    		CurrentPeer = new BlePeer(device); // make a new peer with their address, although that is unhelpful
    		
    		}
    		
    	}

    	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
    		// based on remoteAddress, UUID of remote characteristic, put the incomingBytes into a Message
    		// probably need to have a switchboard function
    		
    		int parentMessagePacketTotal = 0;
    		
    		Log.v(TAG, "incoming hex bytes:" + bytesToHex(incomingBytes));
    		
    		// if our msg is under a few bytes it can't be valid; return
        	if (incomingBytes.length < 10) {
        		Log.v(TAG, "message bytes less than 10");
        		return;
        	}
        	
    		// stick our incoming bytes into a bytebuffer to do some operations
        	ByteBuffer bb  = ByteBuffer.wrap(incomingBytes);
        
        	// get the Message to which these packets belong as well as the current counter
        	int parentMessage = incomingBytes[0] & 0xFF;
        	int packetCounter = (incomingBytes[1] << 8) | incomingBytes[2] & 0xFF;

        	// find the message we're building, identified by the first byte (cast to an integer 0-255)
        	BleMessage b = CurrentPeer.getBleMessageIn(parentMessage);
        	
        	// your packet payload will be the size of the incoming bytes less our 3 needed for the header (ref'd above)
        	byte[] packetPayload = new byte[incomingBytes.length];
        	
        	// throw these bytes into our payload array
        	bb.get(packetPayload, 2, incomingBytes.length - 3);
        	
        	// if our current packet counter is ZERO, then we can expect our payload to be:
        	// the number of packets we're expecting
        	if (packetCounter == 0) {
        		// right now this is only going to be a couple of bytes
        		parentMessagePacketTotal = (incomingBytes[3] << 8) | incomingBytes[4] & 0xFF;
        		
        		Log.v(TAG, "parent message packet total is:" + String.valueOf(parentMessagePacketTotal));
        		b.BuildMessageFromPackets(packetCounter, packetPayload, parentMessagePacketTotal);
        	} else {
        		// otherwise throw this packet payload into the message
        		b.BuildMessageFromPackets(packetCounter, packetPayload);	
        	}
        	
        	// if this particular message is done; ie, is it still pending packets?
        	if (b.PendingPacketStatus() == false) {
        		
        		bleStatusCallback.handleReceivedMessage(bytesToHex(b.RecipientFingerprint), bytesToHex(b.SenderFingerprint), b.MessagePayload, b.MessageType);
        		
        		// check message integrity here?
        		// what about encryption?
        		
        		// how do i parse the payload if the message contains handshake/identity?
        	}
        	
    		
    		
    	}

		@Override
		public void handleNotifyRequest(String device, UUID uuid) {
    		// we're connected, so initiate send to "device", to whom we're already connected
    		Log.v(TAG, "from handleNotifyRequest, initiate sending messages");
    		sendIndicateNotify(device, uuid);
		}
    	
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
