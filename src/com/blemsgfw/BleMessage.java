package com.blemsgfw;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
	private static final int MessagePacketSize = 20; 
	
	// holds all the packets that make up this message
	private ArrayList<BlePacket> messagePackets;

	// hash of the payload of the message contents, which identifies the msg payload
	private byte[] BleMsgDigest;
	
	// number of packets that make up this message
	private int BlePacketCount;
	
	// are there any pending packets left that need to be sent?
	private boolean pendingPacketStatus;
	
	// indicates which packet needs to be sent
	private int currentPacketCounter;
	
	// included in packet and serves as an identification so that the receiver can build msg from packets
	private int messageNumber;

	// Identity or something else
	public String MessageType;
	
	// sha1 of public key for recipient
	public byte[] RecipientFingerprint;
	
	// sha1 of public key for sender
	public byte[] SenderFingerprint;
	
	// truncated sha1 of message; carried in Packet 0 of every message
	public byte[] MessageHash;
	
	// body of message in bytes
	public byte[] MessagePayload;

	// initializes our list of BlePackets, current counter, and sent status
	public BleMessage() {
		messagePackets = new ArrayList<BlePacket>();
		currentPacketCounter = 0;
		pendingPacketStatus = false;
	}
	
	// allows calling program to set which number identifies this message
	public void SetMessageNumber(int MessageNumber) {
		messageNumber = MessageNumber;
	}
	
	// simply returns all the BlePackets that make up this message
	public ArrayList<BlePacket> GetAllPackets() {
		return messagePackets;
	}
	
	// from our array of packets, return a particular packet
	public BlePacket GetPacket(int PacketNumber) {
		return messagePackets.get(PacketNumber);
	}
	
	// call GetPacket(int) by calculating the int based off the currentPacketCounter
	// increment this counter after pulling this packet
	public BlePacket GetPacket() {
			
		// as long as you've got packets to send, send them; if no more packets to send, send 0x00
		if (currentPacketCounter <= messagePackets.size()-1) {
			return GetPacket(currentPacketCounter++);
		} else {
			pendingPacketStatus = false;
			return null;
		}
		
	}
	
	// are there still packets left to send?
	public boolean PendingPacketStatus() {
		return pendingPacketStatus;
	}
	
	// create a BlePacket with a given sequence and payload, and add to our packets list
	private void addPacket(int packetSequence, byte[] packetBytes) {
		messagePackets.add(new BlePacket(packetSequence, packetBytes));
	}
		
	/**
	 * Takes the message payload from the calling method and builds the list
	 * of BlePackets
	 * 
	 * @param Payload Body of the message you want to send in bytes
	 * @param MessagePacketSize The size of the message packets
	 */
	public void setMessage(byte[] Payload) {

		// for an id message:
		// first byte, 0x01, indicates an identity message
		// next 20 bytes are recipient fingerprint
		// next 20 bytes are sender fingerprint
		// final arbitrary bytes are the payload
		
		//byte[] newMsg = Bytes.concat(new byte[]{(byte)(0x01)}, new byte[20], rsaKey.PuFingerprint());
		
		byte[] MsgType;
		
		if (MessageType == "identity") {
			MsgType = new byte[]{(byte)(0x01)};
		} else {
			MsgType = new byte[]{(byte)(0x02)};
		}
		
		// Message Type, RFP, SFP, and payload
		byte[] MessageBytes = Bytes.concat(MsgType, RecipientFingerprint, SenderFingerprint, Payload);
		
		// clear the list of packets; we're building a new message using packets!
        messagePackets.clear();
        
        // how many packets?  divide msg length by packet size, w/ trick to round up
        // so the weird thing is we've got to leave a byte in each msg, so effectively our
        // msg blocks are decreased by an extra byte, hence the -4 and -3 below
        int msgCount  = (MessageBytes.length + MessagePacketSize - 4) / (MessagePacketSize - 3);
        
        // first byte is counter; 0 provides meta info about msg
        // right now it's just how many packets to expect
        
        // get a digest for the message, to define it
        MessageDigest md = null;
        
        try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {

			e.printStackTrace();
		}
        
        // i want my digest to be the packet size less the 5 bytes needed for header info
        byte[] myDigest = Arrays.copyOfRange(md.digest(MessageBytes), 0, MessagePacketSize - 5);
        
        Log.v(TAG, "first payload is of size: " + String.valueOf(myDigest.length));
        
        // first byte is which message this is for the receiver to understand
        // second/third bytes are current packet
        // fourth/fifth bytes are message size
        // 6+ is the digest truncated to 15 bytes
        
        // build the message size tag, which is the number of BlePackets represented in two bytes
        byte[] msgSize = new byte[2];
        msgSize[0] = (byte)(msgCount >> 8);
        msgSize[1] = (byte)(msgCount & 0xFF);
        
        /* the first BlePacket will be this BleMessage's identifying number,
         * then 2 bytes indicating the current packet, 
         * then 2 bytes of the number of BlePackets,
         * finally the message digest
        */ 
        byte[] firstPacket = Bytes.concat(new byte[]{(byte)(messageNumber & 0xFF)}, new byte[]{(byte)0x00, (byte)0x00}, msgSize, myDigest);

        // create a BlePacket of index 0 using the just created payload
        addPacket(0, firstPacket);
        
        // now start building the rest
        int msgSequence = 1;
					
        // loop over the payload and build packets, starting at index 1
		while (msgSequence <= msgCount) {
			
			/* based on the current sequence number and the message packet size
			 *  get the read index in the MessageBytes array */
			int currentReadIndex = ((msgSequence - 1) * (MessagePacketSize - 3));
		
			// leave room for the message counters (the -3 at the end)
			byte[] val = Arrays.copyOfRange(MessageBytes, currentReadIndex, currentReadIndex + MessagePacketSize - 3);

			// the current packet counter is the message sequence, in two bytes
	        byte[] currentPacketCounter = new byte[2];
	        currentPacketCounter[0] = (byte)(msgSequence >> 8);
	        currentPacketCounter[1] = (byte)(msgSequence & 0xFF);
 
	        // build the payload for the packet using the identifying parent BleMessage number, the current BlePacket counter, and the BlePacket bayload
	        val = Bytes.concat(new byte[]{(byte)(messageNumber & 0xFF)}, currentPacketCounter, val);

	        // add this packet to our list of packets
	        addPacket(msgSequence, val);

	        // increment our counter for the next round
	        msgSequence++;
			
		}

		// once we've built all the packets up, indicate we have packets pending send
		pendingPacketStatus = true;
		
	}
	
	/**
	 * Given a byte array for a BlePacket and the counter for that packet, add to our
	 * list of BlePackets that make up this message.  Once the provided PacketCounter is
	 * gte our indicated BlePacketCount, call unbundleMessage() to slam all these packets together
	 * into a message
	 * 	
	 * @param packetCounter
	 * @param packetPayload
	 */
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
	
	/**
	 * Before calling BuildMessageFromPackets(int packetCounter, byte[] packetPayload), call this
	 * while passing in messageSize.  This initializes our messagePackets ArrayList<BlePacket>,
	 * sets BlePacketCount = messageSize, sets our flag for pendingPacketStatus=true, and then 
	 * calls BuildMessageFromPackets(int packetCounter, byte[] packetPayload) to add the first BlePacket
	 * 
	 * @param packetCounter
	 * @param packetPayload
	 * @param messageSize
	 */
	public void BuildMessageFromPackets(int packetCounter, byte[] packetPayload, int messageSize) {
		messagePackets = new ArrayList<BlePacket>();
		BlePacketCount = messageSize;
		pendingPacketStatus = true;
		BuildMessageFromPackets(packetCounter, packetPayload);
	}

	// Fills RecipientFingerprint, SenderFingerprint, MessageType, and MessagePayload
	private void unbundleMessage() {
		/*
		 * - message type
		 * - recipient fingerprint
		 * - sender fingerprint
		 * - hash/mic
		 * - payload
		 */
		
		// pull all the packets, less counters, into a byte array
		byte[] allBytes = dePacketize();
		
		// we need this to be 41+ bytes
		if (allBytes.length > 41) {
		
			byte[] msgType = Arrays.copyOfRange(allBytes, 0, 1); // byte 0
			RecipientFingerprint = Arrays.copyOfRange(allBytes, 1, 21); // bytes 1-20
			SenderFingerprint = Arrays.copyOfRange(allBytes, 21, 41); // bytes 21-40
			MessagePayload = Arrays.copyOfRange(allBytes, 41, allBytes.length+1); //bytes 41 through end

			if (Arrays.equals(msgType, new byte[] {0x01})) {
				MessageType = "identity";
			} else {
				MessageType = "direct";
			}
		
		}
		
		
	}

	// loop over all the BlePackets in the message - packet0 is the hash; write the rest to MessageBytes
	private byte[] dePacketize() {
		
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		
		// i'm still not necessarily writing these out in order!
        for (BlePacket b : messagePackets) {
        	//Log.v(TAG, "packet" + String.valueOf(i) + ", msgseq:" + String.valueOf(b.MessageSequence) + ":" + bytesToHex(b.MessageBytes));
        	if (b.MessageSequence == 0) {
        		MessageHash = b.MessageBytes;
        	} else {
        		try {
					os.write(b.MessageBytes);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}

        }
		
        return os.toByteArray(); 
		
	}
	
}
