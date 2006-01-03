package com.sun.gi.objectstore.test;

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.ObjectStore;
import javax.swing.JFrame;
import java.awt.Container;
import java.awt.BorderLayout;
import javax.swing.JTextField;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.tso.TSOObjectStore;
import com.sun.gi.objectstore.tso.dataspace.InMemoryDataSpace;

import java.io.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

class TextHolder implements Serializable {
  String text;
  long updatecount;
}

public class OSChat extends JFrame {
  ObjectStore os;
  JTextField inputLine;
  JTextArea outputArea;
  long lastUpdateCount = Long.MIN_VALUE;
  public OSChat(ObjectStore objectStore) {
    os = objectStore;
    //os.clear();
    Container c = getContentPane();
    c.setLayout(new BorderLayout());
    inputLine = new JTextField();
    inputLine.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        doInput(inputLine.getText());
        inputLine.setText("");
      }
    });
    c.add(inputLine,BorderLayout.SOUTH);
    outputArea = new JTextArea();
    c.add(new JScrollPane(outputArea),BorderLayout.CENTER);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setSize(400,400);
    setVisible(true);
    startRunning();
  }

  private void startRunning() {
    (new Thread() {
      public void run(){
        Transaction trans = os.newTransaction(null);
        long id = trans.lookup("OSChatData");
        if (id == os.INVALID_ID) {
          TextHolder data = new TextHolder();
          data.text = "";
          data.updatecount = 0;
          id = trans.create(data,"OSChatData");
          trans.commit();
        } else {
          trans.abort();
        }
        while(true){
          try {
            Thread.sleep(500);
          }
          catch (InterruptedException ex) {
            ex.printStackTrace();
          }
          trans = os.newTransaction(null);
          TextHolder holder=null;
		try {
			holder = (TextHolder)trans.peek(id);
		} catch (NonExistantObjectIDException e) {
			
			e.printStackTrace();
		}
          if (lastUpdateCount < holder.updatecount){
            outputArea.setText(holder.text);
            lastUpdateCount = holder.updatecount;
          }
          trans.commit();
        }
      }
    }).start();
  }

  public void doInput(String str){
    Transaction trans = os.newTransaction(null);
    System.out.println("lookign up text object");
    long time = System.currentTimeMillis();
    long id =  trans.lookup("OSChatData");
    long after = System.currentTimeMillis();
    System.out.println("locking text objeect  ("+(after-time)+")");
    time = after;
    TextHolder holder=null;
	try {
		holder = (TextHolder)trans.lock(id);
	} catch (DeadlockException e) {
		
		e.printStackTrace();
	} catch (NonExistantObjectIDException e) {
		
		e.printStackTrace();
	}
    after = System.currentTimeMillis();
    System.out.println("text obeject locked ("+(after-time)+")");
    time = after;
    holder.text += str+"\n";
    holder.updatecount++;
     time = System.currentTimeMillis();
    trans.commit();
    after = System.currentTimeMillis();
     System.out.println("text obecet committed ("+(after-time)+")");
  }

  public static void main(String[] args){
    try {
      ObjectStore ostore = new TSOObjectStore(new InMemoryDataSpace(1));

      new OSChat(ostore);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}
