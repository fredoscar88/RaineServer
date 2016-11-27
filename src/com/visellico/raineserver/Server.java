package com.visellico.raineserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.visellico.rainecloud.serialization.RCDatabase;
import com.visellico.rainecloud.serialization.RCField;
import com.visellico.rainecloud.serialization.RCObject;
import com.visellico.rainecloud.serialization.RCString;
import com.visellico.rainecloud.serialization.Type;


public class Server {

	//read only
	private int port;
	private Thread listenThread;
	private Thread consoleThread;
	private Thread runThread;
	private boolean listening = false;	//NEVERMIND volatile, cmopiler won't optimize out specific things
	private boolean consoling = false;	//hahahaha
	private boolean running = false;
	//DataGram is the UDP api, Socket is TCP (up to our implementation, can do either)
	private DatagramSocket socket;		//Our computer is an apartment building, our socket is the mailbox. We leave our mail there, and we get our mail there.
	private final int MAX_PACKET_SIZE = 1024;
	private byte[] receivedDataBuffer = new byte[MAX_PACKET_SIZE * 10];	//i.e, we have ten sections all of 1024 which we can stuff packets into, up to 10.
	
	//Indicates the type of packet that is being sent
	private final String PROTOCOL = "protocol";
	private final RCField PROTOCOL_CNXN_CHECK = RCField.Byte(PROTOCOL, (byte) 0x0C);
		
	//Right I had too much fun with this
	//	Read the java doc for information on what is going on here
	private SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM dd, yyyy, 'at' HH:mm:ss z");
	SimpleDateFormat hourMinSec = new SimpleDateFormat("HH:mm:ss");
	
	private List<ServerClient> clients = new ArrayList<>();
	
	/**
	 * Instance of the rain server
	 */
	public Server(int port) {	//no need to supply an ip, the server IS the ip
		this.port = port;
	}
	
	public void start() {
		
		try {
			socket = new DatagramSocket(port);
		} catch (SocketException e) {
			e.printStackTrace();
			return;
		}
		
		System.out.println(sdf.format(System.currentTimeMillis()));
		System.out.println("Started server on port " + port + "...");
		
		listening = true;
		consoling = true;
		
		//Lambda! Like passing in functions! Function Pointers(?)! I AM KEEPING THE OLD WAY WITH ANONYMOUS INNER CLASSES AS A COMMENT
//		listenThread = new Thread(new Runnable() {
//			public void run() {
//				listen();
//			}
//		});
		
		//Lambda: (Parameter for run method of the runnable thing) -> {body of run method} OR JUST ONE LINE listen();
		listenThread = new Thread(() -> listen(), "RainCloudServer-ListenThread");	//lambda (runnable target), name
		listenThread.start();
		
		consoleThread = new Thread(() -> runConsole(), "RainCloudServer-ConsoleThread");	//interactable component to the server- allows console input
		consoleThread.start();
		
		runThread = new Thread(() -> serverCycle(), "RainCloudServer-ServerThread");
		runThread.start();
		
		System.out.println("Server is listening...");
		
	}
	
	private void listen() {
		
		while (listening) {
			//receivedDataBuffer is reused
			DatagramPacket packet = new DatagramPacket(receivedDataBuffer, MAX_PACKET_SIZE);
			
			try {
				//listen for incoming sockets (not on the main thread because this blocks) until we actually hear a packet
				socket.receive(packet);
			} catch (IOException e) {	//Could handle each of the four exceptions .receive throws individually. All of the exceptions extend or are IOException
				e.printStackTrace();
			}
			process(packet);
		}
	}
	
	private void runConsole() {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String timeStamp = null;
		String input = null;
		RCDatabase db;
		RCObject obj;
		byte[] data;
		
		Date date = null;
		while (consoling) {	
			try {
				input = br.readLine();
			} catch (IOException e) {
				System.out.println("A terrible thing has happened in com.visellico.raineserver.Server");
			}
			date = new Date(System.currentTimeMillis());
			timeStamp = hourMinSec.format(date);

			db = new RCDatabase("ServerMessage");
			obj = new RCObject("message");
			
			obj.addString(RCString.Create("time", timeStamp));
			obj.addString(RCString.Create("sender", "Server"));
			obj.addString(RCString.Create("msg", input));
			db.addObject(obj);
			
			data = new byte[db.getSize()];
			db.getBytes(data, 0);
			
			System.out.println("[" + timeStamp + "] " + input);
			broadcast(data);
						
		}
		
	}
	
	private void serverCycle() {
		//Essentially send connection packets every 10 seconds
		
		running = true;
		
		RCDatabase db = new RCDatabase("cnxncheck");
		RCObject obj = new RCObject("obj");
		obj.addField(PROTOCOL_CNXN_CHECK);
		db.addObject(obj);
		byte[] connectionCheck = new byte[db.getSize()];
		
		db.getBytes(connectionCheck, 0);
		
		long lastTime = System.currentTimeMillis();
		long currentTime;
		
		while (running) {
			currentTime = System.currentTimeMillis();
			if (currentTime - lastTime > 1000) {
//				System.out.println("[" + hourMinSec.format(currentTime) + "] Sending sanity check");
				broadcast(connectionCheck);
				lastTime = currentTime;
				
				for (int i = 0; i < clients.size(); i++) {
					if (!clients.get(i).isConnected()) {
						clients.remove(i);
						System.out.println("Client disconnected");
					}
				}
			}
			
		}
	}
	
	private void process(DatagramPacket packet) {
		//Here is where deserialization comes in
		// If RCDB packet
		byte[] data = packet.getData();
		
		InetAddress address = packet.getAddress();
		int port = packet.getPort();
		
		ServerClient c = getConnectedClient(address, port);
		
		if (new String(data, 0, 4).equals("RCDB")) {	//scould just compare the bytes lol
			RCDatabase database = RCDatabase.Deserialize(data);
			//Ugh code is all over the place today
			process(database, packet);
		} else if (data[0] == 0x7f && data[1] == 0x7f) {
			
			switch (data[2]) {
			case PacketType.PACKET_CONNECTION: //connection packet
				System.out.println("Client connected");
//				clients.add(new ServerClient(packet.getAddress(), packet.getPort()));
				c.refreshConnected();
				clients.add(c);
				
				//Confirmation packet
				send(new byte[] {42}, c);
				break;
			case PacketType.PACKET_REFRESH_CONNECTION: //Client refreshing connection
				c.refreshConnected();
//				for (ServerClient c1 : clients) {
//					if (c.equals(c1)) c1.refreshConnected();
//				}
				break;
				default: System.out.println("Unknown packet");
			}
		}
		
	}
	
	private void process(RCDatabase database, DatagramPacket packet) {
		System.out.println("Received database");
		
		InetAddress address = packet.getAddress();
		int port = packet.getPort();
		ServerClient c = getConnectedClient(address, port);
		
		if (database.getName().equals("username")) {
			System.out.println("Server: SENDING NEW CLIENT");
			//Lol that is a lot of work for one string
			c.userName = database.objects.get(0).strings.get(0).getString();
			
			RCDatabase userConnected = new RCDatabase("userConnected");
			RCObject client = new RCObject("client");
			client.addField(RCField.Int("userclass", 1));	//value one for Mage
			client.addString(RCString.Create("username", c.userName));
			userConnected.addObject(client);
			
			broadcast(userConnected);
			
		}
		
		dump(database);
	}
	
	public ServerClient getConnectedClient(InetAddress address, int port) {
		ServerClient c = new ServerClient(address, port);
		
		//If the client is already connected
		for (ServerClient c1 : clients) {
			if (c.equals(c1)) c = c1;
		}
		return c;
	}
	
	/**
	 * Sends data to every connected client
	 * @param data
	 */
	public void broadcast(byte[] data) {
		for (ServerClient c : clients) {
			send(data, c);
		}
	}
	
	/**
	 * Sends a databse to every connected client
	 * @param data
	 */
	public void broadcast(RCDatabase db) {
		byte[] data = new byte[db.getSize()];
		db.getBytes(data, 0);
		broadcast(data);
	}

	public void send(byte[] data, ServerClient client) {
		send(data, client.getAddress(), client.getPort());
	}
	
	public void send(byte[] data, InetAddress address, int port) {
		assert(socket.isConnected());
		DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
		try {
			socket.send(packet);	//put our mail in the mail box to send
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	
	public int getPort() {
		return port;
	}
	
	private void dump(DatagramPacket packet) {
		byte[] data = packet.getData();
		InetAddress address = packet.getAddress();
		int port = packet.getPort();
		
		System.out.println("------------------\nPACKET:");
		System.out.println("\t" + address.getHostAddress() + ":" + port);
		System.out.println("\tContents:\n");
		System.out.println("\t\t");
		
		//Has to be packet.getLength() instead of .getData() because, silly packet, it has a buffer that is size MAX_PACKET_SIZE
		for (int i = 0; i < packet.getLength(); i++) {
			System.out.printf("0x%02X ", data[i]);
			if ((i + 1) % 8 == 0) {
				System.out.println("\n\t\t");
			}
		}
		System.out.println("\n------------------");

		
	}
	
	private void dump(RCDatabase database) {

		System.out.println("------------------\nPACKET:");
		System.out.println("    RCDatabase    ");
		System.out.println("------------------");
		System.out.println("Name: " + database.getName());
		System.out.println("Size: " + database.getSize());
		System.out.println("Object Count: " + database.objects.size());
		System.out.println("");
		for (RCObject object : database.objects) {
			System.out.println("\tObject");
			System.out.println("\tName: " + object.getName());
			System.out.println("\tSize: " + object.getSize());
			System.out.println("\tField Count: " + object.fields.size());
			for (RCField field : object.fields) {
				System.out.println("\t\tField");
				System.out.println("\t\tName: " + field.getName());
				System.out.println("\t\tSize: " + field.getSize());
				String data = "";
				switch (field.type) {
				case Type.BYTE: data += field.getByte();
					break;
				case Type.SHORT: data += field.getShort();
					break;
				case Type.CHAR: data += field.getChar();
					break;
				case Type.INTEGER: data += field.getInt();
					break;
				case Type.LONG: data += field.getLong();
					break;
				case Type.FLOAT: data += field.getFloat();
					break;
				case Type.DOUBLE: data += field.getDouble();
					break;
				case Type.BOOLEAN: data += field.getBoolean();
					break;
				}
				System.out.println("\t\tData: " + data);

			}
			for (RCString string : object.strings) {
				System.out.println("\t\tString");
				System.out.println("\t\tName: " + string.getName());
				System.out.println("\t\tSize: " + string.getSize());
				System.out.println("\t\tVal: " + string.getString());
			}
			System.out.println("");

		}
		System.out.println("------------------");
		
	}
	
}
