package com.blemsgfw;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
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
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class BleMessenger {
	private static String TAG = "blemessenger";
	
	// handles to the device's bluetooth
	private BluetoothManager btMgr;
	private BluetoothAdapter btAdptr;
	private Context ctx;
	

	//  this is defined by the framework, but certainly the developer or user can change it
    private static String uuidServiceBase = "73A20000-2C47-11E4-8C21-0800200C9A66";
    private static MyCentral myGattClient = null; 
    private static MyAdvertiser myGattServer = null;
    
    private BleStatusCallback bleStatusCallback;
        
    private BleMessage blmsgOut;
    
    private String myIdentifier;
    private String myFriendlyName;
    
    private int CurrentParentMessage;

    // keep a map of our messages for a connection session - this may not work out; or we may need to keep a map per peer
    private Map<Integer, BleMessage> bleMessageMap;
    
    private Map<String, BlePeer> peerMap;
    private Map<String, String> fpNetMap;
    
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
	
		fpNetMap = new HashMap<String, String>();
		
		// create your server for listening and your client for looking; Android can be both at the same time
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.L) {
			myGattServer = new MyAdvertiser(uuidServiceBase, ctx, btAdptr, btMgr, defaultHandler);
		}
		
		myGattClient = new MyCentral(btAdptr, ctx, clientHandler);
		
		serviceDef.add(new BleCharacteristic("identifier_read", uuidFromBase("100"), MyAdvertiser.GATT_READ));		
		serviceDef.add(new BleCharacteristic("identifier_writes", uuidFromBase("101"), MyAdvertiser.GATT_WRITE));
		serviceDef.add(new BleCharacteristic("data_notify", uuidFromBase("102"), MyAdvertiser.GATT_NOTIFY));
		//serviceDef.add(new BleCharacteristic("data_indicate", uuidFromBase("103"), MyAdvertiser.GATT_INDICATE));
		//serviceDef.add(new BleCharacteristic("data_write", uuidFromBase("104"), MyAdvertiser.GATT_WRITE));

		myGattClient.setRequiredServiceDef(serviceDef);
		
		bleMessageMap = new HashMap<Integer, BleMessage>();
		
	
		// when we connect, send the id message to the connecting party
	}
	
	/**
	 * Send all the messages to the passed in Peer
	 * @param Peer
	 */
	public void sendMessagesToPeer(BlePeer Peer) {
		writeOut(Peer);
	}
	
	// perhaps have another signature that allows sending a single message
	private void writeOut(BlePeer peer) {
		
		// given a peer, get an arbitrary message to send
		BleMessage b = peer.getBleMessageOut();
		
		// pull the remote address for this peer
		String remoteAddress = fpNetMap.get(peer.GetFingerprint());
		
		// send the next packet
    	if (b.PendingPacketStatus()) {
    		byte[] nextPacket = b.GetPacket().MessageBytes;
    		
    		Log.v(TAG, "send write request to " + remoteAddress);
    		
    		if (nextPacket != null) {
	    		myGattClient.submitCharacteristicWriteRequest(remoteAddress, uuidFromBase("101"), nextPacket);
	    		
	    		// call this until the message is sent
	    		writeOut(peer);
    		}
    	} else {
    		Log.v(TAG, "all pending packets sent");
    	}
		
	}

	
	private UUID uuidFromBase(String smallUUID) {
		String strUUID =  uuidServiceBase.substring(0, 4) + new String(new char[4-smallUUID.length()]).replace("\0", "0") + smallUUID + uuidServiceBase.substring(8, uuidServiceBase.length());
		UUID idUUID = UUID.fromString(strUUID);
		
		return idUUID;
	}
	
	public void showFound() {
		// actually return a list of some sort
		myGattClient.scanLeDevice(true);
	}
		
	public void BeFound() {
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.L) {
		
			// have this pull from the service definition
			myGattServer.addChar(MyAdvertiser.GATT_READ, uuidFromBase("100"), defaultHandler);
			myGattServer.addChar(MyAdvertiser.GATT_WRITE, uuidFromBase("101"), defaultHandler);
			myGattServer.addChar(MyAdvertiser.GATT_NOTIFY, uuidFromBase("102"), defaultHandler);
			
			myGattServer.updateCharValue(uuidFromBase("100"), new String(myIdentifier + "|" + myFriendlyName).getBytes());
			myGattServer.updateCharValue(uuidFromBase("101"), new String("i'm listening").getBytes());
			
			// advertising doesn't take much energy, so go ahead and do it
			myGattServer.advertiseNow();
		}
		
	}
	
	public void HideYourself() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.L) {
			myGattServer.advertiseOff();
		}
	}
	
	// maybe we shouldn't use this to send our identity stuff . . .
    private void sendOutgoing(String remote, UUID uuid) {
    	
    	// if we've got messages to send
    	if (bleMessageMap.size() > 0) {
    	
    		// get the current message to send
	    	blmsgOut = bleMessageMap.get(CurrentParentMessage);
	    	
	    	// get the next packet to send
	    	byte[] nextPacket = blmsgOut.GetPacket().MessageBytes;
	    	
	    	// update the value of this characteristic, which will send to subscribers
	    	myGattServer.updateCharValue(uuid, nextPacket);
	    	
	    	if (nextPacket != null) {
	    		sendOutgoing(remote, uuid);
	    	}
	    	
	    	if (!blmsgOut.PendingPacketStatus()) {
	    		Log.v(TAG, "message sent, remove it from the map");
	    		// if this message is sent, remove from our Map queue and increment our counter
	    		bleMessageMap.remove(CurrentParentMessage);
	    		CurrentParentMessage++;
	    		
	    		// handle anything you want to have handled when this message is sent
	    	} 
	    	
	    	
    	}
    	// TODO: consider when to disconnect
    	
    }

    private void incomingMessage(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
		int parentMessagePacketTotal = 0;
		
		Log.v(TAG, "incoming hex bytes:" + ByteUtilities.bytesToHex(incomingBytes));
		
		// if our msg is under a few bytes it can't be valid; return
    	if (incomingBytes.length < 5) {
    		Log.v(TAG, "message bytes less than 5");
    		return;
    	}
    	
    	// get the Message to which these packets belong as well as the current counter
    	int parentMessage = incomingBytes[0] & 0xFF;
    	int packetCounter = (incomingBytes[1] << 8) | incomingBytes[2] & 0xFF;

    	// get the peer which matches the connected remote address 
    	BlePeer p = peerMap.get(remoteAddress);
    	
    	// find the message we're building, identified by the first byte (cast to an integer 0-255)
    	// if this message wasn't already created, then the getBleMessageIn method will create it
    	BleMessage b = p.getBleMessageIn(parentMessage);
    	
    	// your packet payload will be the size of the incoming bytes less our 3 needed for the header (ref'd above)
    	byte[] packetPayload = Arrays.copyOfRange(incomingBytes, 3, incomingBytes.length);
    	
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
    		
    		fpNetMap.put(ByteUtilities.bytesToHex(b.SenderFingerprint), remoteAddress);
    		
    		bleStatusCallback.handleReceivedMessage(ByteUtilities.bytesToHex(b.RecipientFingerprint), ByteUtilities.bytesToHex(b.SenderFingerprint), b.MessagePayload, b.MessageType);
    		
    		// check message integrity here?
    		// what about encryption?
    		
    		// how do i parse the payload if the message contains handshake/identity?
    	}
    	
		
    }
    
    
    MyGattServerHandler defaultHandler = new MyGattServerHandler() {
    	
    	
    	public void ConnectionState(String device, int status, int newStatus) {
    		
    		if (newStatus == 2) {
    		
	    		// create/reset our message map for our connection
	    		bleMessageMap =  new HashMap<Integer, BleMessage>();
	    		
	    		// this global "CurrentParentMessage" thing only works when sending in peripheral mode
	    		CurrentParentMessage = 0;
	    		
	    		// the message itself needs to know what it's sequence is when sent to recipient
	    		idMessage.SetMessageNumber(CurrentParentMessage);
	    		// add our id message to this message map
	    		bleMessageMap.put(0, idMessage);
	    		
	    		Log.v(TAG, "id message added to connection's message map");
	    		
	    		 // create a new peer to hold messages and such for this network device
	    		BlePeer p = new BlePeer(device);
	    		peerMap.put(device, p);
    		
    		}
    		
    	}

    	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
    		// based on remoteAddress, UUID of remote characteristic, put the incomingBytes into a Message
    		// probably need to have a switchboard function
    		
    		incomingMessage(remoteAddress, remoteCharUUID, incomingBytes);
    			
    	}

		@Override
		public void handleNotifyRequest(String device, UUID uuid) {
    		// we're connected, so initiate send to "device", to whom we're already connected
    		Log.v(TAG, "from handleNotifyRequest, initiate sending messages");
    		sendOutgoing(device, uuid);
		}

		@Override
		public void handleAdvertiseChange(boolean advertising) {
			if (advertising) {
				bleStatusCallback.advertisingStarted();
			} else {
				bleStatusCallback.advertisingStopped();
			}
		}
    	
    };


    MyGattClientHandler clientHandler = new MyGattClientHandler() {
    	
    	@Override
    	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes) {
    		incomingMessage(remoteAddress, remoteCharUUID, incomingBytes);
    		
    	}
    	
		@Override
		public void intakeFoundDevices(ArrayList<BluetoothDevice> devices) {
			
			// loop over all the found devices
			// add them 
			for (BluetoothDevice b: devices) {
				Log.v(TAG, "(BleMessenger)MyGattClientHandler found device:" + b.getAddress());
				
				
				// if the peer isn't already in our list, put them in!
				String peerAddress = b.getAddress();
				
				if (!peerMap.containsKey(peerAddress)) {
					BlePeer blePeer = new BlePeer(peerAddress);
					peerMap.put(peerAddress, blePeer);
				}
				
				// this could possibly be moved into an exterior loop
				myGattClient.connectAddress(peerAddress);
			}
			
		}
		
		@Override
		public void parlayWithRemote(String remoteAddress) {
			
			// so at this point we should still be connected with our remote device
			// and we wouldn't have gotten here if the remote device didn't meet our service spec
			// so now let's trade identification information and transfer any data

			BleMessage b = new BleMessage();
			
			// add this new message to our message map
			bleMessageMap.put(0, b);

			// pass our remote address and desired uuid to our gattclient
			// who will look up the gatt object and uuid and issue the read request
			myGattClient.submitSubscription(remoteAddress, uuidFromBase("102"));
			
			// still need to write identity info

			
		}
		
		
		@Override
		public void reportDisconnect() {
			// what to do when disconnected?
			
		}
    	
    };
    
}
