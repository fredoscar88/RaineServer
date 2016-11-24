package com.visellico.raineserver;

import java.net.InetAddress;

public class ServerClient {

	public int userID;
	private InetAddress address;
	private int port;
	private boolean status = false;	//descriptive, this
	
	private static int userIDCounter = 1;
	
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
	
}
