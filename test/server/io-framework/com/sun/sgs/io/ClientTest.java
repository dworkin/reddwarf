package com.sun.sgs.io;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import com.sun.sgs.impl.io.ConnectorFactory;
import com.sun.sgs.impl.io.SocketConnector;
import com.sun.sgs.impl.io.IOConstants.TransportType;
import com.sun.sgs.io.IOHandle;
import com.sun.sgs.io.IOHandler;
import com.sun.sgs.io.IOConnector;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/**
 * This is a simple test class that demonstrates potential usage of the IO
 * Framework.  
 *
 * @author      Sten Anderson
 * @version     1.0
 */
public class ClientTest extends JFrame {

    private IOHandle connection = null;
    
    private SocketTableModel model;
    private int numSockets = 20; 
    private int numDisconnects = 0;
    private boolean done = false;
    private Random random;
    
    public ClientTest() {
        super("Client Test");
        
        random = new Random();
        
        JTable table = new JTable(model = new SocketTableModel());
        JScrollPane pane = new JScrollPane(table);
        
        add(pane, BorderLayout.CENTER);
        
        JButton goButton = new JButton("Go!");
        goButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                start();
            }
        });
        
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        
        addWindowListener(new WindowAdapter() {
           public void windowClosing(WindowEvent e) {
               shutdown();
           }
        });
        
        JPanel bottomPanel = new JPanel();
        bottomPanel.add(goButton);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        setBounds(200, 200, 600, 300);
        setVisible(true);
    }
    
    private void shutdown() {
        done = true;
        model.shutdown();
    }
    
    public void dataChanged() {
        model.fireTableDataChanged();
    }
    

    public void start() {
        IOConnector connector = ConnectorFactory.createConnector(
                                                TransportType.RELIABLE,
                                                Executors.newCachedThreadPool());
        model.connect(connector);
    }

    public static void main(String[] args) {
        new ClientTest();
    }
    
    private class SocketTableModel extends AbstractTableModel {
        
        private List<SocketInfo> list; 
        private List<String> columnHeaders;
        
        SocketTableModel() {
            columnHeaders = new ArrayList<String>();
            columnHeaders.add("ID");
            columnHeaders.add("Status");
            columnHeaders.add("Messages In");
            columnHeaders.add("Bytes In");
            columnHeaders.add("Bytes Out");
            
            list = new ArrayList<SocketInfo>();
            for (int i = 0; i < numSockets; i++) {
                list.add(new SocketInfo(i));
            }
        }
        
        public void connect(IOConnector connector) {
            for (SocketInfo info : list) {
                info.connect(connector);
            }
        }
        
        public void shutdown() {
            for (SocketInfo info : list) {
                info.close();
            }
        }
        
        public String getColumnName(int col) {
            return columnHeaders.get(col);
        }
        
        public int getRowCount() {
            return list.size();
        }
        
        public int getColumnCount() {
            return columnHeaders.size();
        }
        
        public Object getValueAt(int row, int col) {
            String curCol = columnHeaders.get(col);
            SocketInfo info = list.get(row);
            if (curCol.equals("ID")) {
                return info.getID();
            }
            else if (curCol.equals("Status")) {
                return info.getStatus();
            }
            else if (curCol.equals("Messages In")) {
                return info.getMessagesIn();
            }
            else if (curCol.equals("Bytes In")) {
                return info.getBytesIn();
            }
            else if (curCol.equals("Bytes Out")) {
                return info.getBytesOut();
            }
            
            return "";
        }
    }
    
    private class SocketInfo implements IOHandler {
        
        private IOHandle handle;
        private String status;
        private int messagesIn;
        private long bytesIn;
        private long bytesOut;
        private int id;
        
        SocketInfo(int id) {
            this.id = id;
            status = "Not Connected";
        }
        
        public int getID() {
            return id;
        }
        
        public int getMessagesIn() {
            return messagesIn;
        }
        
        public long getBytesIn() {
            return bytesIn;
        }
        
        public long getBytesOut() {
            return bytesOut;
        }
        
        public void close() {
            try {
                handle.close();
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
            
        }
        
        public void connect(IOConnector connector) {
            try {
                int port = 5150;
                handle = connector.connect(InetAddress.getByName("127.0.0.1"), port, this);
            }
            catch (UnknownHostException uhe) {
                uhe.printStackTrace();
            }
            catch (IOException ioe) {
                ioe.printStackTrace();

            }
        }
        
        public String getStatus() {
            return status;
        }
        
        public void messageReceived(byte[] message, IOHandle handle) {
            messagesIn++;
            bytesIn += message.length;
            dataChanged();
        }

        public void disconnected(IOHandle handle) {
            numDisconnects++;
            if (numDisconnects >= numSockets) {
                System.exit(0);
            }
            dataChanged();
        }

        public void exceptionThrown(Throwable exception, IOHandle handle) {
            System.out.println("ClientTest exceptionThrown");
            exception.printStackTrace();
        }

        public void connected(IOHandle handle) {
            status = "Connected";
            dataChanged();
            Thread t = new Thread () {
                public void run() {
                    while (!done) {
                        writeBytes(random.nextInt(2000) + 1);
                        try {
                            Thread.sleep(random.nextInt(500) + 50);
                        }
                        catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                    }
                }
                
            };
            t.start();
        }
        
        private void writeBytes(int num) {
            
            byte[] buffer = new byte[num];
            for (int i = 0; i < num; i++) {
                buffer[i] = (byte) 1;
            }
            try {
                handle.sendMessage(buffer);
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
            bytesOut += num;
            dataChanged();
        }
    }

}
