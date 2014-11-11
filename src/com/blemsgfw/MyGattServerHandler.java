package com.blemsgfw;

import java.util.UUID;

public interface MyGattServerHandler {
	
	public void ConnectionState(String device, int status, int newState);
	
	public void handleAdvertiseChange(boolean advertising);
	
	public void handleNotifyRequest(String device, UUID uuid);
	
	public void incomingMissive(String remoteAddress, UUID remoteCharUUID, byte[] incomingBytes);
	
}
