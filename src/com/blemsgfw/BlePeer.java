package com.blemsgfw;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.util.Log;

public class BlePeer {

	private String peerAddress;
	private String peerName;
	private byte[] peerPublicKey;
	private byte[] peerPublicKeyFingerprint;
	private String friendlyName;
	private Date lastSeen;

	private Map<Integer, BleMessage> peerMessagesIn;
	private Map<Integer, BleMessage> peerMessagesOut;
	

	public BlePeer(String PeerAddress) {
		peerAddress = PeerAddress;
		peerName="";
		peerMessagesIn = new HashMap<Integer, BleMessage>();
		peerMessagesOut = new HashMap<Integer, BleMessage>();
	}
	
	private boolean checkDigest(byte[] digest, byte[] payload) {
        MessageDigest md = null;
        boolean status = false;
        try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {

			e.printStackTrace();
		}
        
        if (Arrays.equals(md.digest(payload), digest)) {
        	status = true;	
        }

        return status;
	}
	
	public Map<Integer, BleMessage> GetMessageOut() {
		return peerMessagesOut;
	}
	
	public Map<Integer, BleMessage> GetMessageIn() {
		return peerMessagesIn;
	}
	
	public String RecipientAddress() {
		return peerAddress;
	}
	
	public byte[] GetPublicKey() {
		return peerPublicKey;
	}
	
	public boolean SetPublicKey(byte[] publicKey) {
		boolean status = true;
		
		peerPublicKey = publicKey;
		
		status = checkDigest(peerPublicKeyFingerprint, publicKey);
		
		return status;
	}
	
	
	public String GetFingerprint() {
		return bytesToHex(peerPublicKeyFingerprint);
	}

	public byte[] GetFingerprintBytes() {
		return peerPublicKeyFingerprint;
	}
	
	public void SetFingerprint(byte[] fp) {
		peerPublicKeyFingerprint = fp;
	}
	
	public void SetFingerprint(String fp) {
		peerPublicKeyFingerprint = hexToBytes(fp);
	}
	
	public BleMessage getBleMessageIn(int MessageIdentifier) {

		// if there isn't already a message with this identifier, add one
		if (!peerMessagesIn.containsKey(MessageIdentifier)) {
			peerMessagesIn.put(MessageIdentifier, new BleMessage());
		}
		
		return peerMessagesIn.get(MessageIdentifier);
	}
	
	public BleMessage getBleMessageOut(int MessageIdentifier) {
		
		return peerMessagesOut.get(MessageIdentifier);
	}
	
	public BleMessage getBleMessageOut() {
		
		// get the highest priority (0=highest) message to send out
		int min = 0;
		for (Integer i : peerMessagesOut.keySet()) {
			if (i <= min ) {
				min = i;
			}
		}
		
		Log.v(TAG, "getBleMessageOut #" + String.valueOf(min));
		return getBleMessageOut(min);
	}
	
	
	public void addBleMessageOut(BleMessage m) {
		
		// find the highest message number
		int max = 0;
		
		if (peerMessagesOut.size() > 0) {
				for (Integer i : peerMessagesOut.keySet()) {
					if (max <= i ) {
						max = i;
					}
				}
				max++;
		}
		
		Log.v(TAG, "add message to peerMessagesOut #" + String.valueOf(max));
		peerMessagesOut.put(max, m);		
	}
	
	public void SetName(String PeerName) {
		peerName = PeerName;
	}
	
	public String GetName() {
		return peerName;
	}
	
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	private static final String TAG = "BlePeer";
    private static String bytesToHex(byte[] bytes) {
    	
    	String result = "";
    	
    	try {
	        char[] hexChars = new char[bytes.length * 2];
	        for ( int j = 0; j < bytes.length; j++ ) {
	            int v = bytes[j] & 0xFF;
	            hexChars[j * 2] = hexArray[v >>> 4];
	            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	        }
	        result = new String(hexChars);
    	} catch (Exception x) {
    		result = "";
    		Log.v(TAG, "error bytes to hex");
    	}
    	
    	
        return result;
    }
	
    private static byte[] hexToBytes(String hex) {
    	byte[] bytes = new byte[hex.length() / 2];
    	
    	for (int i = 0; i < bytes.length; i++) {
    		bytes[i] = (byte) Integer.parseInt(hex.substring(2*i, 2*i+2),16);
    		
    	}
    	
    	return bytes;
    }
    
}
