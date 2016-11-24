package com.visellico.raineserver;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class RaineServer {

	private static int defaultPort = 25564;	//arbitrary
	
	public static void main(String[] args) {
		Server server = new Server(defaultPort);	//or whatever port
		
		//etc. etc.
		//maybe set other server props
		
		//client
		InetAddress address = null;
		try {
			address = InetAddress.getByName("192.168.1.1");	//no idea where these packets are going lol
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		int port = 25564;	//lol our port
		
		server.start();
		server.send(new byte[] {0, 1, 2}, address, port);
		
	}
	
}
