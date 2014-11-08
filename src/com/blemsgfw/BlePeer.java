package com.blemsgfw;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
	
	public String RecipientAddress() {
		return peerAddress;
	}
	
	
	public String GetFingerprint() {
		return bytesToHex(peerPublicKeyFingerprint);
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

		// if there isn't already a message with this identifier, add one
		if (!peerMessagesOut.containsKey(MessageIdentifier)) {
			peerMessagesOut.put(MessageIdentifier, new BleMessage());
		}
		
		return peerMessagesOut.get(MessageIdentifier);
	}
	
	public void SetName(String PeerName) {
		peerName = PeerName;
	}
	
	public String GetName() {
		return peerName;
	}
	
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
	
    private static byte[] hexToBytes(String hex) {
    	byte[] bytes = new byte[hex.length() / 2];
    	
    	for (int i = 0; i < bytes.length; i++) {
    		bytes[i] = (byte) Integer.parseInt(hex.substring(2*i, 2*i+2),16);
    		
    	}
    	
    	return bytes;
    }
    
}
