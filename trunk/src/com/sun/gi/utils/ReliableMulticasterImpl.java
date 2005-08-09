package com.sun.gi.utils;

import java.net.InetAddress;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import java.io.IOException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.util.ImpossibleException;
import com.sun.multicast.util.AssertFailedException;
import com.sun.multicast.reliable.transport.InvalidTransportProfileException;
import com.sun.multicast.util.UnsupportedException;
import java.net.DatagramPacket;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ReliableMulticasterImpl
    implements ReliableMulticaster {
  final static int PACKET_SIZE = 1000;
  String transportName = "LRMP";
  private RMPacketSocket ms;
  private static boolean done = false;
  private RMCListener listener;
  public ReliableMulticasterImpl(InetAddress ia, int port,
                                 RMCListener l) throws IOException,
      RMException {
    /**
     * Creates an RMPacketSocket.
     * @exception IOException if an I/O error occurs
     * @exception RMException if a reliable multicast related exception occurs
     */
    TransportProfile tp = null;
    listener = l;

    if (transportName.equals("LRMP")) {

      /*
       * Obtain a new LRMPTransportProfile with the address and
       * port specified.
       */
      LRMPTransportProfile lrmptp;

      try {
        lrmptp = new LRMPTransportProfile(ia, port);
      }
      catch (InvalidMulticastAddressException e) {
        throw new ImpossibleException(e);
      }

      tp = lrmptp;

      lrmptp.setTTL( (byte) 1);
      lrmptp.setOrdered(true);
    }
    else {
      throw new AssertFailedException();
    }

    try {
      ms = tp.createRMPacketSocket(TransportProfile.SEND_RECEIVE);
    }
    catch (InvalidTransportProfileException e) {
      throw new ImpossibleException(e);
    }
    catch (UnsupportedException e) {
      throw new ImpossibleException(e);
    }

    startListen(ms);
  }

  public void startListen(final RMPacketSocket sock) {
    (new Thread() {
      public void run() {
        while (!done) {
          try {
            DatagramPacket dp = sock.receive();
            if (listener!=null) {
              listener.pktArrived(dp);
            }
          }
          catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      }
    }).start();

  }

  public void send(DatagramPacket pkt) {
    try {
      ms.send(pkt);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }



  static public void main(String[] args) {
   ReliableMulticasterImpl impl = null;
    try {
      RMCListener l = new RMCListener(){
        public void pktArrived(DatagramPacket pkt) {
          String s = new String(pkt.getData(),pkt.getOffset(),pkt.getLength());
          System.out.println(new String("Received: "+s));
        }
      };
      impl = new ReliableMulticasterImpl(InetAddress.getByName("224.0.0.1"),
                                         9999,null);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
    DatagramPacket outpkt = new DatagramPacket(new byte[PACKET_SIZE],PACKET_SIZE);
    while (!done) {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      try {
        String s = in.readLine();
        System.out.println("Sending: " + s);
        outpkt.setData(s.getBytes());
        impl.send(outpkt);
      }
      catch (IOException ex1) {
        ex1.printStackTrace();
      }
    }
  }

  public void setListener(RMCListener l) {
    listener = l;
  }
}
