package com.blemsgfw;

public class BlePacket {
	public int MessageSequence;
	public byte[] MessageBytes;

	BlePacket(int s, byte[] b) {
		MessageSequence = s;
		MessageBytes = b;
	}

	
}
