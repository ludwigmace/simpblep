package com.blemsgfw;

public class BleRecipient {

	private String recipName;
	private byte[] encryptKey;

	public BleRecipient(String RecipientName, byte[] RecipientKey) {
		recipName = RecipientName;
		encryptKey = RecipientKey;
	}
	
	public BleRecipient(String RecipientName) {
		recipName = RecipientName;
	}
	
	public String RecipientName() {
		return recipName;
	}
	
	public byte[] RecipientKey() {
		return encryptKey;
	}

	
}
