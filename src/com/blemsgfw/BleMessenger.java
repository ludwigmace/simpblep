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

    // keep a map of our messages for a connection session - this may not work out; or we may need to keep a map per peer
    private Map<Integer, BleMessage> bleMessageMap;
    
    private Map<String, BlePeer> peerMap;
    
	public BleMessage idMessage;
    
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

	
		// when we connect, send the id message to the connecting party
	}
	
	private UUID uuidFromBase(String smallUUID) {
		String strUUID =  uuidServiceBase.substring(0, 4) + new String(new char[4-smallUUID.length()]).replace("\0", "0") + smallUUID + uuidServiceBase.substring(8, uuidServiceBase.length());
		UUID idUUID = UUID.fromString(strUUID);
		
		return idUUID;
	}
	
	public void AddMessage() {
	    
	}
	
	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
		// based on remoteAddress, UUID of remote characteristic, put the incomingBytes into a Message
		// probably need to have a switchboard function
		
		// remoteAddress will allow me to look up the connection, so "sender" won't need to be in the packet
		// 
		
		int parentMessagePacketTotal = 0;
		
		Log.v(TAG, "incoming hex bytes:" + bytesToHex(incomingBytes));
		
		// if our msg is under a few bytes it can't be valid; return
    	if (incomingBytes.length < 5) {
    		Log.v(TAG, "message bytes less than 5");
    		return;
    	}
    	
    	//get the connection
    	BlePeer thisConnection = peerMap.get(remoteAddress);
	    	
		// stick our incoming bytes into a bytebuffer to do some operations
    	ByteBuffer bb  = ByteBuffer.wrap(incomingBytes);
    
    	// get the Message to which these packets belong as well as the current counter
    	int parentMessage = incomingBytes[0] & 0xFF;
    	int packetCounter = (incomingBytes[1] << 8) | incomingBytes[2] & 0xFF;

    	// find the message for this connection that we're building
    	BleMessage b = thisConnection.getBleMessage(parentMessage);
    	
    	// your packet payload will be the size of the incoming bytes less our 3 needed for the header (ref'd above)
    	byte[] packetPayload = new byte[incomingBytes.length - 3];
    	
    	// throw these bytes into our payload array
    	bb.get(packetPayload, 2, incomingBytes.length - 3);
    	
    	// if our current packet counter is ZERO, then we can expect our payload to be:
    	// the number of packets we're expecting
    	if (packetCounter == 0) {
    		// right now this is only going to be a couple of bytes
    		parentMessagePacketTotal = (incomingBytes[3] << 8) | incomingBytes[4] & 0xFF;
    		b.BuildMessageFromPackets(packetCounter, packetPayload, parentMessagePacketTotal);
    	} else {
    		// otherwise throw this packet payload into the message
    		b.BuildMessageFromPackets(packetCounter, packetPayload);	
    	}
    	
    	// check if this particular message is done; ie, is it still pending packets?
    	if (b.PendingPacketStatus() == false) {
    		
    		// this message receipt is now complete
    		// so now we need to handle that completed state
    		
    		// if this particular message was an identifying message, then:
    		// - send our identity over
    		// - return friendly name to calling program

    		byte[] payload = b.MessagePayload;
    		String recipientFingerprint = bytesToHex(b.RecipientFingerprint);
    		String senderFingerprint = bytesToHex(b.SenderFingerprint);
    		String msgType = b.MessageType;
    		
    		bleStatusCallback.handleReceivedMessage(recipientFingerprint, senderFingerprint, payload, msgType);
    		
    		// check message integrity here?
    		// what about encryption?
    		
    		// how do i parse the payload if the message contains handshake/identity?
    	}
    	
		
		
	}
	
	public void BeFound() {
	
		
		// have this pull from the service definition
		myGattServer.addChar(MyAdvertiser.GATT_READ, uuidFromBase("100"), controlHandler);
		myGattServer.addChar(MyAdvertiser.GATT_READWRITE, uuidFromBase("101"), controlHandler);
		myGattServer.addChar(MyAdvertiser.GATT_NOTIFY, uuidFromBase("102"), controlHandler);
		
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
    	
    	public void handleReadRequest(UUID uuid) { }
    	
    	public void handleNotifyRequest(UUID uuid) { }
    	
    	public void ConnectionState(String device, int status, int newStatus) {
    		
    		// create/reset our message map for our connection
    		bleMessageMap =  new HashMap<Integer, BleMessage>();
    		
    		CurrentParentMessage = 0;
    		
    		// add our id message to this message map
    		bleMessageMap.put(0, idMessage);
    		Log.v(TAG, "id message added to connection's message map");
    		
    	}

		public void incomingBytes(UUID charUUID, byte[] inData) { }

		@Override
		public void handleNotifyRequest(String device, UUID uuid) {
			
		}
    	
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
        	//sendIndicateNotify(uuid);
    		
    	}
    	
    	public void ConnectionState(String dude, int status, int newStatus) {
    		
    	}

		public void incomingBytes(UUID charUUID, byte[] inData) { }

		@Override
		public void handleNotifyRequest(String device, UUID uuid) {
			
		}
    	
    };
    
    MyGattServerHandler controlHandler = new MyGattServerHandler() {
    	
    	public void handleReadRequest(UUID uuid) { }
    	
    	public void handleNotifyRequest(String device, UUID uuid) {
    		
    		// we're connected, so initiate send to "device", to whom we're already connected
    		Log.v(TAG, "from handleNotifyRequest, initiate sending messages");
    		sendIndicateNotify(device, uuid);
    		
    		/* look this connectee up via their id information
			even though you can only be connected to one central at a time, you could lose your
			 connection and get connected to be a different central, in which case you'd need to
			 figure out who the hell you're talking to at any given Notify request because
			 you don't want to send them the wrong message
			*/
    		
    		/*
    		if (peerMap.containsKey(device)) {
    			BlePeer p = peerMap.get(device);
    		}
    		*/
			// send them packets until they're disconnected or the message has been sent
    		
    		// <frank>HOWEVER!
    		// if you don't have a device for this central loaded in your peerMap,
    		// this means they haven't written to you yet, so do you even have anything
    		// to send to them?  so on first connect, nothing will happen here
			
			//firstServiceChar.setValue("HI");
			//btGattServer.notifyCharacteristicChanged(btClient, firstServiceChar, false);
    		

    		
    	}
    	
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
