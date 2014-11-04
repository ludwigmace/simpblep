package com.blemsgfw;


import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import android.util.Log;

import com.google.common.primitives.Bytes;

public class BleMessage {

	private static final String TAG = "BLEMSG";
	private ArrayList<BlePacket> messagePackets;
	private ArrayList<BleRecipient> messageRecipients;
	
	public final byte[] MessageDoneMarker = new byte[] {0x00};
	
	private byte[] BleMsgDigest;

	
	private int BlePacketCount;
	private boolean pendingPacketStatus;
	private int counter;
	private int messagePacketSize;
	private String remoteAddress;
	private UUID remoteCharacteristic;
	private int messageIdentifier;
	
	public String MessageType;
	public byte[] RecipientFingerprint;
	public byte[] SenderFingerprint;
	public byte[] MessageHash;
	public byte[] MessagePayload;
	
	
	public void AddRecipient(BleRecipient Recipient) {
		messageRecipients.add(Recipient);
	}

	public void SetRemoteInfo(String RemoteAddress, UUID RemoteCharacteristic) {
		remoteAddress = RemoteAddress;
		remoteCharacteristic = RemoteCharacteristic;
	}
	
	public void SetRemoteInfo(String RemoteAddress, UUID RemoteCharacteristic, int MessageIdentifier) {
		remoteAddress = RemoteAddress;
		remoteCharacteristic = RemoteCharacteristic;
		messageIdentifier = MessageIdentifier;
	}
	
	
	public BleMessage() {
		messagePackets = new ArrayList<BlePacket>();
		messageRecipients = new ArrayList<BleRecipient>();
		counter = 0;
		pendingPacketStatus = false;
	}
	
	public ArrayList<BlePacket> GetAllPackets() {
		return messagePackets;
	}
	
	public BlePacket GetPacket() {
			
		// as long as you've got packets to send, send them; if no more packets to send, send 0x00
		if (counter <= messagePackets.size()-1) {
			return GetPacket(counter++);
		} else {
			pendingPacketStatus = false;
			return new BlePacket(0, MessageDoneMarker);
		}
		
		
	}
	
	public boolean PendingPacketStatus() {
		return pendingPacketStatus;
	}
	
	public BlePacket GetPacket(int PacketNumber) {
		return messagePackets.get(PacketNumber);
	}
	
	private void addPacket(int packetSequence, byte[] packetBytes) {
		messagePackets.add(new BlePacket(packetSequence, packetBytes));
	}
	
	public void setMessage(byte[] MessageBytes) {
		setMessage(MessageBytes, 20);
	}
	
	public void setMessage(byte[] MessageBytes, int MessagePacketSize) {

		messagePacketSize = MessagePacketSize; 
		
		// clear the list of packets; we're building a new message using packets!
        messagePackets.clear();
        
        // how many packets?  divide msg length by packet size, w/ trick to round up
        // so the weird thing is we've got to leave a byte in each msg, so effectively our
        // msg blocks are decreased by an extra byte, hence the -3 and -2 below
        int msgCount  = (MessageBytes.length + messagePacketSize - 3) / (messagePacketSize - 2);
        
        Log.v(TAG, "packet count:" + String.valueOf(msgCount));
        // first byte is counter; 0 provides meta info about msg
        // right now it's just how many packets to expect
        
        // get a digest for the message, to define it
        MessageDigest md = null;
        
        try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {

			e.printStackTrace();
		}
        
        // i want my digest to be the packet size less the 2 bytes needed for counter and size
        byte[] myDigest = Arrays.copyOfRange(md.digest(MessageBytes), 0, messagePacketSize - 2);
        
        Log.v(TAG, "first payload is of size: " + String.valueOf(myDigest.length));
        
        // first byte is control; second byte is packetcount; add on the digest
        byte[] firstPacket = Bytes.concat(new byte[]{(byte)0x01, (byte)(msgCount & 0xFF)}, myDigest);

        // add the packet to this message
        addPacket(0, firstPacket);
        
        int msgSequence = 1;
					
		while (msgSequence <= msgCount) {
			
			int currentReadIndex = ((msgSequence - 1) * (messagePacketSize - 2));
		
			// leave room for the message counters
			//Log.v(TAG, "rawMsg:" + String.valueOf(rawMsg.length) + ", currentReadIndex:" + String.valueOf(currentReadIndex));
			byte[] val = Arrays.copyOfRange(MessageBytes, currentReadIndex, currentReadIndex + messagePacketSize - 2);
			
			byte[] msgHeader = {(byte) 0x02, (byte)(msgSequence & 0xFF)}; 
	        val = Bytes.concat(msgHeader, val);

	        addPacket(msgSequence, val);
	        
	        msgSequence++;
			
		}

		// final packet will be an EOT
		byte[] eot = {(byte) 0x04, (byte) 0x00};
		addPacket(msgSequence, eot);

		pendingPacketStatus = true;
		
	}
	
	// this signature of the method will be called when this Message is created
	public void BuildMessageFromPackets(int packetCounter, byte[] packetPayload, int messageSize) {
		messagePackets = new ArrayList<BlePacket>();
		BlePacketCount = messageSize;
		pendingPacketStatus = true;
		BuildMessageFromPackets(packetCounter, packetPayload);
	}
	
	public void BuildMessageFromPackets(int packetCounter, byte[] packetPayload) {
		this.addPacket(packetCounter, packetPayload);
		
		// if we've got all the packets for this message, set our pending packet flag to false
		// this will need to be changed to account for missing packets, if we use NOTIFY to get our data, or non-reliable WRITEs
		if (packetCounter >= BlePacketCount) {
			pendingPacketStatus = false;
			// now act on the fact this message has all its packets
			unbundleMessage();
		}
		
	}
	
	private void unbundleMessage() {
		/*
		 * - message type
		 * - recipient fingerprint
		 * - sender fingerprint
		 * - hash/mic
		 * - payload
		 */
		
		byte[] allBytes = getAllBytes();
		
		byte[] msgType = Arrays.copyOfRange(allBytes, 0, 1); // byte 0
		RecipientFingerprint = Arrays.copyOfRange(allBytes, 1, 21); // bytes 1-20
		SenderFingerprint = Arrays.copyOfRange(allBytes, 21, 41); // bytes 21-40
		MessageHash = Arrays.copyOfRange(allBytes, 41, 61); // bytes 41-60
		MessagePayload = Arrays.copyOfRange(allBytes, 61, allBytes.length+1); //bytes 61 through end

		if (msgType.equals(new byte[] {0x01})) {
			MessageType = "identity";
		} else {
			MessageType = "direct";
		}
		
		
	}
	
	
	public byte[] getAllBytes() {
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		
        for (BlePacket b : messagePackets) {
        	os.write(b.MessageBytes, 0, b.MessageBytes.length);
        }
		
        return os.toByteArray(); 
		
	}
	
	public boolean BuildMessage(byte[] incomingBytes) {
		
    	ByteBuffer bb  = ByteBuffer.wrap(incomingBytes);
    	boolean nextMsg = false;
    	
    	if (incomingBytes.length > 1) {
	    	

    		// the first byte tells us what's going on with this message
	    	byte pC = bb.get(0);
	    	 
	    	
	    	// if the first byte is a 0X01, then the 2nd byte is the size of the message and the rest is the digest
	    	if (pC == (byte) 0x01) {
	        	BlePacketCount = bb.get(1) & 0xFF;
	        	
	        	BleMsgDigest = new byte[incomingBytes.length];
	      	
	        	// build the message digest
	        	bb.get(BleMsgDigest, 2, incomingBytes.length - 2);
	        	              
	        	// we've got a new message, so re-init the messagePackets arraylist
	        	messagePackets = new ArrayList<BlePacket>();
	        	
	        	Log.v(TAG, "new msg");
	        	
	        	nextMsg = true;
	        	
	        // EOT
	    	} else if (pC == (byte) 0x04) {
	    		nextMsg = false;
	            
	    	// packet counter
	    	} else {
	    		int packetCounter = bb.get(1) & 0xFF;
	    		
	    		// our message will be the size of the incoming packet less the 2 for the control and counter
	    		byte[] bytesMsg = new byte[incomingBytes.length - 2];
	
	    		// set bytebuffer position to 3rd character (offset 2)
	    		bb.position(2);
	    		
	    		// read the rest of it into the bytesMsg array
	    		bb.get(bytesMsg, 0, incomingBytes.length - 2);
	    		
	    		// add to our list of messages
	    		addPacket(packetCounter, bytesMsg);
	    		
	    		Log.v(TAG, "msg:"+new String(bytesMsg));
	    		
	    		nextMsg = true;
	        	
	    	}
    
    	}
    	return nextMsg;
	}
	
	
}
