package com.visellico.raineserver;

import java.net.InetAddress;

public class ServerClient {

	public int userID;
	private InetAddress address;
	private int port;
	private boolean status = false;	//descriptive, this
	
	private final int CLIENT_TIMEOUT = 2000;	//If the client doesnt refresh their connection within 2 seconds we cut' em
	private long lastCheckIn;
	
	private static int userIDCounter = 1;
	
	public String userName;
	
	public ServerClient(InetAddress address, int port) {
		
		userID = userIDCounter++;
		this.address = address;
		this.port = port;
		status = true;
		
	}
	
	public InetAddress getAddress() {
		
		return address;
	}
	
	public int getPort() {
		return port;
	}
	
	//Currently we're hashing based on userID, but we'll eventually move to their name or something
	//So when we add this to a set, this method is called to get the id to put it in the table as, overriding hashCode from object
	public int hashCode() {
		return userID;
	}
	
	public void refreshConnected() {
		lastCheckIn = System.currentTimeMillis();
//		System.out.println("ServerClient: Connection refreshed " + this + " " + lastCheckIn);
	}
	
	public boolean isConnected() {
		//If the client has sent a "still connected" packet in the last 10 seconds
//		System.out.println(lastCheckIn);
		return System.currentTimeMillis() - lastCheckIn < CLIENT_TIMEOUT;
	}
	
	public boolean equals(Object o) {
//		System.out.println(address + " " + ((ServerClient)o ).getAddress());
//		System.out.println(address.equals(((ServerClient) o).getAddress()));
		return (address.equals(((ServerClient) o).getAddress()) && port == ((ServerClient) o).getPort());
		
	}
	
}
