package com.sun.gi.logic.test.rawsocket;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *  This is a test harness server for the RawSocketManager, and not
 *  intended to be part of a production release. 
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class SocketServerTest {

	List<ServerSocketChannel> channels;
	Selector selector;
	
	public SocketServerTest() {
		
		channels = new ArrayList<ServerSocketChannel>();
		restart();
		while (true) {
			try {
				
				acceptConnections();
			}
			catch (IOException ioe) {
				ioe.printStackTrace();
				restart();
			}
		}
		
	}
	
	private void acceptUDP() {
		try {
			DatagramChannel channel = DatagramChannel.open();
			DatagramSocket socket = channel.socket();
			InetSocketAddress address = new InetSocketAddress(6000);
			socket.bind(address);
			ByteBuffer buffer = ByteBuffer.allocate(65507);
			while (true) {
				System.out.println("Waiting for Datagram");
				SocketAddress clientAddress = channel.receive(buffer);
				System.out.println("Received Datagram");
				buffer.flip();
				for (int i = buffer.position(); i < buffer.limit(); i++) {
					System.out.print(buffer.get());
				}
				System.out.println();
				buffer.flip();
				channel.send(buffer, clientAddress);
				buffer.clear();
			}
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	private void restart() {
		try {
			if (selector != null && selector.isOpen()) {
				selector.close();
			}
			for (ServerSocketChannel s : channels) {
				s.close();
			}
			setupChannels(10);
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	private void acceptConnections() throws IOException {
		Thread t = new Thread() {
			public void run() {
				acceptUDP();
			}
		};
		t.start();
		while (true) {
			selector.select();
			
			Set readyKeys = selector.selectedKeys();
			Iterator iterator = readyKeys.iterator();
			while (iterator.hasNext()) {
				SelectionKey curKey = (SelectionKey) iterator.next();
				iterator.remove();
				
				if (curKey.isAcceptable()) {
					ServerSocketChannel curChannel = (ServerSocketChannel) curKey.channel();
					SocketChannel client = curChannel.accept();
					System.out.println("Accepting connection from " + client);
					client.configureBlocking(false);
					SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
					
				}
				else if (curKey.isReadable()) {
					SocketChannel curChannel = (SocketChannel) curKey.channel();
					ByteBuffer in = ByteBuffer.allocate(100);
					int numBytes = curChannel.read(in);
					if (numBytes < 0) {
						curKey.cancel();
					}
					in.flip();
					System.out.print("Received: " + numBytes + " data:");
					for (int i = in.position(); i < in.limit(); i++) {
						System.out.print(in.get());
					}
					System.out.println();
				}
				else if (curKey.isWritable()) {
					System.out.println("writing response");
					ByteBuffer response = ByteBuffer.wrap("Status OK".getBytes());
					SocketChannel channel = (SocketChannel) curKey.channel();
					channel.write(response);
				}
			}
			
			try {
				Thread.sleep(500);		// poor man's simulation of network latency.
			}
			catch (InterruptedException ie) {
				ie.printStackTrace();
			}
			
		}
	}
	
	private void setupChannels(int numPorts) throws IOException {
		channels.clear();
		selector = Selector.open();
		for (int i = 5000; i < (5000 + numPorts); i++) {
			ServerSocketChannel curChannel = ServerSocketChannel.open();
			curChannel.configureBlocking(false);
			curChannel.socket().bind(new InetSocketAddress(i));
			curChannel.register(selector, SelectionKey.OP_ACCEPT);
			
			channels.add(curChannel);
			System.out.println("Listening on port " + i);
		}
	}
	
	public static void main(String[] args) {
		new SocketServerTest();
	}

}
