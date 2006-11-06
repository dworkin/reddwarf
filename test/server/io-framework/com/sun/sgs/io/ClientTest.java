package com.sun.sgs.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import com.sun.sgs.impl.io.SocketConnector;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.io.IOConnector;

import java.nio.ByteBuffer;

/**
 * This is a simple test class that demonstrates potential usage of the IO
 * Framework.  
 *
 * @author      Sten Anderson
 * @version     1.0
 */
public class ClientTest {

    private IOHandle connection = null;

    public void start() {
        IOConnector manager = new SocketConnector();

        IOHandler listener = new IOHandler() {


            public void messageReceived(ByteBuffer message, IOHandle handle) {
                System.out.println("ClientConnectionTest: message received");
                try {
                    handle.close();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

            public void disconnected(IOHandle handle) {
                System.out.println("ClientConnectionTest: disconnected");
                System.exit(0);
            }

            public void exceptionThrown(Throwable exception, IOHandle handle) {
                System.out.println("ClientTest exceptionThrown");
                exception.printStackTrace();
            }

            public void connected(IOHandle handle) {
                System.out.println("ClientConnectionTest: connected");
                ByteBuffer buffer = ByteBuffer.allocate(1);
                buffer.put((byte) 1);
                
                try {
                    handle.sendMessage(buffer);
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
                
            }

        };
        try {
            int port = 5150;
            connection = manager.connect(InetAddress.getByName("127.0.0.1"), port, listener);
        }
        catch (UnknownHostException uhe) {
            uhe.printStackTrace();
        }
        catch (IOException ioe) {
            ioe.printStackTrace();

        }
    }

    public static void main(String[] args) {
        new ClientTest().start();
    }

}
