/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

/*
 * JMEHttpTransporter.java
 *
 * Created on January 30, 2006, 9:57 AM
 *
 *
 */

package com.sun.gi.comm.users.client.impl;

import com.sun.gi.utils.jme.ByteBuffer;
import com.sun.gi.utils.jme.LinkedQueue;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

/**
 * This class actually sends and receives the data using http
 * @author as93050
 */
public class JMEHttpTransporter implements Runnable {
    
    private JMEClientManager listener;
    private String httpSession;
    private String gameName;
    private String host;
    private String port;
    private LinkedQueue inputQueue;
    private final static String GAME_NAME = "GAME_NAME";
    private long pollInterval = 500;
    private boolean stop = false;
    byte[] tempSendBuffer = new byte[16192];
    private final static String URL_START = "http://";
    private final static String COLON = ":";
    //Jetty i sset up so that any request to /Servlet will go to our servlet
    private final static String SERVLET_NAME = "/Servlet/JMEServlet";
    private String url;  
    
    public JMEHttpTransporter() {
        listener = JMEClientManager.getClientManager();
    }
    public void setGameName(String gameName) {
        this.gameName = gameName;
    }
    
    /**
     * if there is no data to send to the server there is no need to do a POST
     * Rather we do an http GET instead
     */
    private void pollForData() {
        HttpConnection connection = null;
        InputStream is = null;
        try {
            connection = (HttpConnection)Connector.open(url);
            setConnectionParams(connection,HttpConnection.GET);
            int rc = connection.getResponseCode();
            if (rc != HttpConnection.HTTP_OK) {
                throw new IOException("HTTP response code: " + rc);
            }            
            is = connection.openInputStream();
            readServerResponse(connection,is);
        } catch (IOException ex) {
            ex.printStackTrace();
            listener.exceptionOccurred(ex);
        } finally {
            try {
                if (is != null)
                    is.close();               
                if (connection != null)
                    connection.close();
            } catch (IOException ex) {
                //not much we can do here
            }
        }
    }
    
    private OutputStream sendDataToServer(byte[] data, HttpConnection connection) throws IOException {
        OutputStream os = null;
        setConnectionParams(connection,HttpConnection.POST);
        os = connection.openOutputStream();
        os.write(data);
        os.flush();
        return os;
    }
    
    private void setConnectionParams(final HttpConnection connection,String requestMethod) throws IOException {
        connection.setRequestMethod(requestMethod);
        connection.setRequestProperty("Content-Language", "en-US");
        //always send the game name, if there is a session send it
        if (httpSession != null) {
            connection.setRequestProperty("Cookie", httpSession + ";" + GAME_NAME + "=" + gameName);
        } else {
            connection.setRequestProperty("Cookie", GAME_NAME + "=" + gameName);
        }
    }
    private void sendAndReceiveData(byte[] data) {
        HttpConnection connection = null;
        InputStream is = null;
        OutputStream os = null;
        try {
            connection = (HttpConnection)Connector.open(url);
            os = sendDataToServer(data,connection);
            is = connection.openInputStream();
            readServerResponse(connection,is);
        } catch (IOException ex) {
            ex.printStackTrace();
            listener.exceptionOccurred(ex);
        } catch (SecurityException ex) {
            ex.printStackTrace();
            listener.exceptionOccurred(ex);
        } finally {
            try {
                if (is != null)
                    is.close();
                if (os != null)
                    os.close();
                if (connection != null)
                    connection.close();
            } catch (IOException ex) {
                //not much we can do here
            }
        }
    }
    
    private void readServerResponse(HttpConnection connection,InputStream is) throws IOException  {
        String cookie = connection.getHeaderField("Set-cookie");
        if (cookie != null) {
            int semicolon = cookie.indexOf(';');
            httpSession = cookie.substring(0, semicolon);
        }
        int len = (int)connection.getLength();
        byte[] data = null;
        if (len > 0) {
            int actual = 0;
            int bytesread = 0 ;
            data = new byte[len];
            while ((bytesread != len) && (actual != -1)) {
                actual = is.read(data, bytesread, len - bytesread);
                bytesread += actual;
            }
        } else {
            int ch;
            int num = 0;
            byte [] tempData = new byte[16192];
            while ((ch = is.read()) != -1) {
                tempData[num++] = (byte)ch;
            }
            data = new byte[num];
            System.arraycopy(tempData,0,data,0,num);
        }        
        if (data.length > 0) {
            ByteBuffer[] packetsReceived = extractPackets(data);
            listener.dataArrived(packetsReceived);
        }
    }
    
    private ByteBuffer[] extractPackets(byte[] data) {
        int numberOfPackets = data[0];
        int position = 1;
        byte[] packet;
        ByteBuffer[] packets = new ByteBuffer[numberOfPackets];
        for (int i = 0;i < numberOfPackets;i++) {
            //we need to extract the packet size so that we can read the 
            //individual packets. The size is sent as a short(2 bytes)            
            byte packetSize0 = data[position++];
            byte packetSize1 = data[position++];
            int packetSize = makeShort(packetSize0,packetSize1);
            packet = new byte[packetSize];
            System.arraycopy(data,position,packet,0,packetSize);
            packets[i] = ByteBuffer.wrap(packet);
            position += packetSize;
        }
        return packets;
    }
    public void setInputQueue(LinkedQueue queue) {
        inputQueue = queue;
    }
    
    public void run() {
        url = URL_START + host + COLON + port + SERVLET_NAME;
        while (!stop) {
            try {
                Thread.sleep(pollInterval);
                sendData();
                synchronized(this) {         
                    notify();
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                listener.exceptionOccurred(ex);
            } catch (Exception ex) {
                ex.printStackTrace();
                listener.exceptionOccurred(ex);
            }
            
        }
    }
    
    private void sendData() {
        if (inputQueue.size() > 0) {
            byte[] sendBuffer = convertToByteArray();
            sendAndReceiveData(sendBuffer);
        } else {
            pollForData();
        }
    }
    
    private byte[] convertToByteArray() {
        int sendPos = 0;
        int numberofPackets = 0;
        int packetSizeIndex = 0;
        int packetSize = 0;
        int queueSize = inputQueue.size();
        for (int i = 0;i < queueSize;i++) {
            ByteBuffer[] buffers = (ByteBuffer[])inputQueue.dequeue();
            packetSizeIndex = sendPos;
            //we need to send the packet size which is 2 bytes
            sendPos += 2;
            packetSize = 0;
            for (int j = 0;j < buffers.length;j++) {               
                int dataSize = buffers[j].position();                
                System.arraycopy(buffers[j].array(),0,tempSendBuffer,sendPos,dataSize);
                sendPos += dataSize;
                packetSize += dataSize;
            }
            //set the packet size for this packet - a 2 byte short
            tempSendBuffer[packetSizeIndex++] = short1((short)packetSize);
            tempSendBuffer[packetSizeIndex] = short0((short)packetSize);
            numberofPackets++;
        }
        byte[] sendBuffer = new byte[sendPos+1];
        sendBuffer[0] = (byte)numberofPackets;
        System.arraycopy(tempSendBuffer,0,sendBuffer,1,sendBuffer.length - 1);
        return sendBuffer;
    }
    private byte short1(short x) { return (byte)(x >>  8); }
    private byte short0(short x) { return (byte)(x >>  0); }
    
    public void setStop(boolean flag) {
        stop = flag;
    }
    
    public void setPort(String port) {
        this.port = port;
    }
  
    public void setHost(String host) {
        this.host = host;
    }
    
    public void setPollInterval(long interval) {
        pollInterval = interval;
    }
    
    public void sendLogoutRequest() {
        byte[] sendBuffer = new byte[1];
        sendBuffer[0] = (byte)0;
        OutputStream os = null;
        HttpConnection connection = null;
        try {
            connection = (HttpConnection)Connector.open(url);
            os = sendDataToServer(sendBuffer,connection);
            httpSession = null;
        } catch (IOException ex) {
            ex.printStackTrace();
            listener.exceptionOccurred(ex);
        } catch (SecurityException ex) {
            ex.printStackTrace();
            listener.exceptionOccurred(ex);
        } finally {
            try {
                if (os != null)
                    os.close();
                if (connection != null)
                    connection.close();
            } catch (IOException ex) {
                //not much we can do here
            }
        }
    }
    
    private int makeShort(byte b1, byte b0) {
	return (int)((((b1 & 0xff) <<  8) |
		      ((b0 & 0xff) <<  0)));
    }
}



