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
