/*
 * Created on Mar 13, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package com.sun.gi.mobile.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.TooManyListenersException;

import javax.comm.CommPortIdentifier;
import javax.comm.PortInUseException;
import javax.comm.SerialPort;
import javax.comm.SerialPortEvent;
import javax.comm.SerialPortEventListener;
import javax.comm.UnsupportedCommOperationException;

import com.sun.gi.gamespy.JNITransport;
import com.sun.gi.gamespy.TransportListener;

/**
 * @author Athomas Goldberg
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class GSClientProxy implements TransportListener {
	private static String DEFAULT_SERIAL_PORT = "COM4";
	private static String DEFAULT_GT2_SOCKET_ADDRESS = "127.0.0.1:7777";
	private static int DEFAULT_BUFF_SZ = 2048;
	private static boolean DEFAULT_RELIABILITY = true;
	
	private SerialConnection serialConnection;
	private boolean isReliable = DEFAULT_RELIABILITY;
	private String serialPortID;
	private long connectionHandle;
	private long socketHandle;
	boolean master = false;

	public static void main(String[] args) {
		if(args.length == 0) {
			new GSClientProxy(DEFAULT_SERIAL_PORT, DEFAULT_GT2_SOCKET_ADDRESS);
		} else if(args.length == 2){
			new GSClientProxy(args[0], args[1]);
		} else {
			System.err.println("USAGE: GSClientProxy <serial_port_id>" +
					" <socket_address>");
		}
	}

	/**
	 * 
	 */
	public GSClientProxy(String serialPortID, String sockAddr) {
	    JNITransport.addListener(this);
	    this.serialPortID = serialPortID;
	    socketHandle = JNITransport.gt2CreateSocket(sockAddr,
	    		                                    DEFAULT_BUFF_SZ,
	    		                                    DEFAULT_BUFF_SZ);
	    if (socketHandle != 0) { // were first
	        master = true;
	        JNITransport.gt2Listen(socketHandle);
	      }
	      else { // error on create
	        // we must be the second client
	        socketHandle = JNITransport.gt2CreateSocket("",
	        		                                    DEFAULT_BUFF_SZ,
														DEFAULT_BUFF_SZ);
	        byte[] msg = "Connection Attempt!".getBytes();
	        JNITransport.gt2Connect(socketHandle,sockAddr,msg,msg.length,0);
	        System.out.println("Connect result = " + JNITransport.lastResult());
	        serialConnection = new SerialConnection(this);
	      }
	      startThinkThread();
	}

	  private void startThinkThread() {
	    new Thread(new Runnable() {
	      public void run() {
	        while(true){
	          JNITransport.gt2Think(socketHandle);
	          try {
	            Thread.sleep(100);
	          }
	          catch (InterruptedException ex) {
	            ex.printStackTrace();
	          }
	        }
	      }
	    }).start();
	  }
	  

	/* (non-Javadoc)
	 * @see com.sun.gi.gamespy.TransportListener#socketError(long)
	 */
	public void socketError(long socketHandle) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.gamespy.TransportListener#connected(long, long, byte[], int)
	 */
	  public void connected(long connectionHandle, long result, byte[] message,
            int msgLength) {
  		this.connectionHandle = connectionHandle;
}

	/* (non-Javadoc)
	 * @see com.sun.gi.gamespy.TransportListener#closed(long, long)
	 */
	public void closed(long connectionHandle, long reason) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.gamespy.TransportListener#ping(long, int)
	 */
	public void ping(long connectionHandle, int latency) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.gamespy.TransportListener#connectAttempt(long, long, long, short, int, byte[], int)
	 */
	public void connectAttempt(long socketHandle,
			                   long connectionHandle,
							   long ip,
							   short port,
							   int latency,
							   byte[] message,
							   int msgLength) {
		// TODO Auto-generated method stub
		
	}
	
	public void receive(byte[] data, int length) throws IOException {
		serialConnection.write(data,length);
	}
	
	private void send(byte[] data, int length) {
        JNITransport.gt2Send(connectionHandle,data,length,isReliable);
	}
	
	private static class SerialConnection implements SerialPortEventListener {
	    private CommPortIdentifier portId;
	    private Enumeration portList;
	    private GSClientProxy proxy;
	    private InputStream inputStream;
	    private OutputStream outputStream;
	    private SerialPort serialPort;
		
	    public SerialConnection(GSClientProxy proxy) {
	    	this.proxy = proxy;
	        portList = CommPortIdentifier.getPortIdentifiers();
	        while (portList.hasMoreElements()) {
	            portId = (CommPortIdentifier) portList.nextElement();
	            if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
	                if (portId.getName().equals(DEFAULT_SERIAL_PORT)) {
	                    try {
	                        serialPort =
	                        		(SerialPort)portId.open("GSClientProxy",
	                        				                2000);
	                    } catch (PortInUseException e) {}
	                    try {
	                        inputStream = serialPort.getInputStream();
	                        outputStream = serialPort.getOutputStream();
	                    } catch (IOException e) {}
	            	try {
	                        serialPort.addEventListener(this);
	            	} catch (TooManyListenersException e) {}
	                    serialPort.notifyOnDataAvailable(true);
	                    try {
	                        serialPort.setSerialPortParams(9600,
	                            SerialPort.DATABITS_8,
	                            SerialPort.STOPBITS_1,
	                            SerialPort.PARITY_NONE);
	                    } catch (UnsupportedCommOperationException e) {}
	                }
	            }
	        }
		}

	    public void write(byte[] data, int length) throws IOException {
	    	outputStream.write(data,0,length);
	    }
	    
	    public void serialEvent(SerialPortEvent evt) {
	        switch(evt.getEventType()) {
	            case SerialPortEvent.BI:
	            case SerialPortEvent.OE:
	            case SerialPortEvent.FE:
	            case SerialPortEvent.PE:
	            case SerialPortEvent.CD:
	            case SerialPortEvent.CTS:
	            case SerialPortEvent.DSR:
	            case SerialPortEvent.RI:
	            case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
	                break;
	            case SerialPortEvent.DATA_AVAILABLE:
	                byte[] readBuffer = new byte[DEFAULT_BUFF_SZ];
	                try {
	                	int length = 0;
	                    while (inputStream.available() > 0) {
	                        length = inputStream.read(readBuffer);
	                    }
	                    proxy.send(readBuffer,length);
	                } catch (IOException e) {
	                	e.printStackTrace();
	                }
	                break;
	        }
		}
	}
}
