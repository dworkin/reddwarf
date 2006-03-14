/**
 *
 * <p>Title: BigPacketTest.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.framework.interconnect.test;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.interconnect.impl.LRMPTransportManager;

/**
 *
 * <p>Title: BigPacketTest.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class BigPacketTest {
	  static TransportManager mgr1 = new LRMPTransportManager();
	  static TransportManager mgr2 = new LRMPTransportManager();
	  
	  static public void main(String[] args){
		  TransportChannel chan1=null;
		  TransportChannel chan2=null;
		try {
			chan1 = mgr1.openChannel("foo");
			chan2 = mgr2.openChannel("foo");
		} catch (IOException e) {
			
			e.printStackTrace();
		}
		  
		  final TransportChannel chan1final = chan1;
		  chan2.addListener(new TransportChannelListener(){
			  
			public void dataArrived(ByteBuffer buff) {				
				// check validity
				System.out.println(buff.remaining()/4+" ints receieved");
				System.out.flush();
				int size = buff.remaining()/4;
				for(int i=0;i<size;i++){
					int nxtInt = buff.getInt();
					if (nxtInt!=i){
						System.out.println("Transmission error: int "+i+
								" equals "+nxtInt);
						System.out.flush();
					}
				}
				System.out.println("ints checked.");
				System.out.flush();
				synchronized(chan1final){
					chan1final.notifyAll();
				}
			}

			public void channelClosed() {
				System.out.println("Channel closed");
				
			}});
		  sendBytes(chan1,0);
		  sendBytes(chan1,1140);
		  sendBytes(chan1,1140*2);
		  for(int i=0;i<5;i++){ // test 5 times totally random
			  int sz = ((int)((Math.random()*5)*1140))+
			  	((int)(Math.random()*1140));
			  sendBytes(chan1,sz);
		  }
	  }

	/**
	 * @param chan1
	 * @param sz
	 */
	static private void sendBytes(TransportChannel chan1, int sz) {
		ByteBuffer buff = ByteBuffer.allocate(sz);
		System.out.println("Sending "+sz/4+" ints.");
		System.out.flush();
		for(int i=0;i<sz/4;i++){
			buff.putInt(i);
		}
		try {
			chan1.sendData(buff);
			synchronized(chan1){
				try {
					chan1.wait();
				} catch (InterruptedException e) {
					
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			
			e.printStackTrace();
		}
		
	}
}
