package com.blemsgfw;

import java.util.UUID;

public interface MyGattServerHandler {
	
	public void ConnectionState(String device, int status, int newState);
	
	public void incomingBytes(UUID charUUID, byte[] inData);
	
	public void handleReadRequest(UUID uuid);
		
	public void handleNotifyRequest(String device, UUID uuid);
	
}
