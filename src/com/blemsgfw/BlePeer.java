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

	private Map<Integer, BleMessage> peerMessages;
	

	public BlePeer(String PeerAddress) {
		peerAddress = PeerAddress;
		peerName="";
		peerMessages = new HashMap<Integer, BleMessage>();
	}
	
	public String RecipientAddress() {
		return peerAddress;
	}
	
	public BleMessage getBleMessage(int MessageIdentifier) {

		// if there isn't already a message with this identifier, add one
		if (!peerMessages.containsKey(MessageIdentifier)) {
			peerMessages.put(MessageIdentifier, new BleMessage());
		}
		
		return peerMessages.get(MessageIdentifier);
	}
	
	public void SetName(String PeerName) {
		peerName = PeerName;
	}
	
	public String GetName() {
		return peerName;
	}
	
}
