/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

import java.util.*;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

class Node {
    public String host;
    public String address;
    public String head;
    public int lines;
    public int ttl;

    Node(String host, String address, String head, int lines, int ttl) {
	this.host = host;
	this.address = address;
	this.head = head;
	this.lines = lines;
	this.ttl = ttl;
    }
}

public class ShowTree {

    private String line;
    private ShowTree showTree;
    private Vector nodeVector = new Vector();
    private Node sender = null;
    private boolean shortDisplay;
    private String startDate = null;
    private String dummyHeadName = "ZZZZ-DUMMYHEAD";

    public static void main(String args[]) {
	ShowTree showTree = new ShowTree();

	showTree.doit(args);
    }

    private void doit(String[] args) {
	//
	// Scan each file for the last occurrence of "Binding to head"
	// or "Reaffiliating now to head"
	//
	for (int i = 0; i < args.length; i++) {
		if (args[i].equals("-shortDisplay")) {
		    shortDisplay = true;
		    continue;
		}

		String path = args[i];
		int lines = 0;
		String host;	

		int slash = path.lastIndexOf("/");

		if (slash <= 0)
		    host = path;
		else
		    host = path.substring(slash + 1);

		
		if ((host.length() >= 5 && 
		    host.substring(0, 5).equals("USERS")) || 
		    host.equals("SAVE")) {
		    continue;   // Skip USERS* and directory named SAVE
		}

		//
		// Looking for "DataSender.<host>"
		//
		if (host.indexOf("DataSender.") >= 0) {
		    int dot = host.indexOf(".");
		    host = host.substring(dot + 1);  // start of host name
		    String senderAddress = null;

		    if ((dot = host.indexOf(".")) >= 0)
			host = host.substring(0, dot);  // get rid of trailers
		    
		    try {
		        FileReader in = new FileReader(path);
		        BufferedReader r = new BufferedReader(in);

		        r.ready();

			String previousLine = null;

		        while ((line = r.readLine()) != null) {
			    if (startDate == null && 
				line.indexOf("Sender Count") == 0) {

				startDate = previousLine;
			    }

			    // String tmp = "Sender is ";
			    //
			    // if (line.indexOf(tmp) == 0)
			    //	senderAddress = line.substring(tmp.length());

			    lines++;		// count lines
			    previousLine = line;
			}
		    } catch (Exception e) { }

		    try {
			InetAddress s = InetAddress.getByName(host);
		        senderAddress = s.getHostAddress();
			System.out.println("Sender is " + s);
		    } catch (UnknownHostException e) {
			e.printStackTrace();
		    }

		    sender = new Node(host, senderAddress, null, lines, 0);
		    nodeVector.addElement(sender);
		    continue;
		}

		int dot = host.indexOf(".");

		if (dot >= 0)
		    host = host.substring(0, dot);
		else
		    host = host;

		Node n = new Node(host, null, null, 0, 0);
		nodeVector.addElement(n);

		try {
		    FileReader in = new FileReader(path);
		    BufferedReader r = new BufferedReader(in);

		    r.ready();

		    while ((line = r.readLine()) != null) {
		        gethead(n, "Binding to head ");
		        gethead(n, "Reaffiliating now to head ");

			lines++;
		    }
		} catch (FileNotFoundException e) {
			;
		} catch (Exception e) {
			System.out.println(e + " " + path);
			continue;
                }

		n.lines = lines;
	}

	if (sender == null)
	    System.out.println("There is no file for the sender!");
	else {
	    //
	    // Find the head node for each node
	    // if there isn't one, make a node and attach it to the sender
	    //
	    Node dummyHead = null;

	    for (int i = 0; i < nodeVector.size(); i++) {
	        Node ni = (Node)nodeVector.elementAt(i);

		if (ni.host == null || ni.head == null ||
		    ni.host.equals(sender.host)) {
		    continue;
		}

		if (ni.host == dummyHeadName) 
		    continue;

	        boolean found = false;

		for (int j = 0; j < nodeVector.size(); j++) {
		    if (i == j)
			continue;

	            Node nj = (Node)nodeVector.elementAt(j);

		    if (ni.head.equals(nj.host)) {
			found = true;
			break;
		    }
		}

		if (!found) {
		    System.out.println("Missing log file for head " + ni.head + 
			"!  linking to " + dummyHeadName + ".");

		    if (dummyHead == null) {
		        dummyHead = new Node(dummyHeadName, null, 
			    sender.host, 0, 0);
		        nodeVector.addElement(dummyHead);
		    }
		
		    Node n = new Node(ni.head, null, dummyHeadName, 0, 0);
		    nodeVector.addElement(n);
		}
	    }

	    System.out.println("");
	    showCounts();
	    tree(sender, true);
	}
    }

    //
    // We're looking for lines like these:
    //
    // Reaffiliating now to head sandpiper/129.148.185.112, \
    //   My Address is: speedo/129.148.70.149, TTL is: 4 
    //
    // Binding to head sandpiper/129.148.185.112, starting seq 14, \
    //   My Address is: zoomass/129.148.176.138, TTL is: 4
    //
    private void gethead(Node n, String s) {
	int i;

	if ((i = line.indexOf(s)) < 0)
	    return;

	i += s.length();

	int j;

	if ((j = line.indexOf("/", i)) < 0) {
	    System.out.println("Invalid head binding information in file " +
	        n.host);
	    System.exit(1);
	}

	n.head = line.substring(i, j);		// head name

	if ((i = n.head.indexOf(".")) > 0)
	    n.head = n.head.substring(0, i);	// get rid of domainname

        String tmp = "My Address is: ";

	if ((i = line.indexOf(tmp)) >= 0) {
	    i += tmp.length();
	    tmp = ", TTL is: ";
	    if ((j = line.indexOf(tmp)) > 0) {
	        n.address = line.substring(i, j);
		j += tmp.length();
		n.ttl = Integer.parseInt(line.substring(j));
	    }
	}
    }

    private void showCounts() {
	int heads = 0;
	int membersOnly = 0;
	int nonMembers = 0;

	for (int i = 0; i < nodeVector.size(); i++) {
	    try {
	        Node n = (Node)nodeVector.elementAt(i);

	        if (isHead(n.host))
	    	    heads++;
	        else if (n.head != null)
		    membersOnly++;
		else
		    nonMembers++;

	    } catch (IndexOutOfBoundsException ie) {
		break;
            }
	}

	if (startDate != null)
	    System.out.println("DataSender running since " + startDate);

	System.out.println("Total Group Members:  " + 
	    (heads + 1 + membersOnly));
	System.out.println("Members only:  " + membersOnly);
	System.out.println("Heads including the sender:  " + (heads + 1));
	System.out.println("Maximum tree depth:  " + tree(sender, false));

	if (nonMembers != 0) {
	    System.out.print("Nodes seeking membership:  ");

	    for (int i = 0; i < nodeVector.size(); i++) {
	        try {
	            Node n = (Node)nodeVector.elementAt(i);

	            if (n.head == null && n != sender) {
		        int dot = n.host.indexOf(".");
			

			System.out.print(n.host + " ");
		    }
	        } catch (IndexOutOfBoundsException ie) {
	            break;
                }
	    }
	    System.out.println("");
	}

	System.out.println("");
    }

    private boolean isHead(String host) {
	//
	// The sender does not have its own node in the list "nodes".
	// If a node exists with the host equal to the sender,
	// then the sender is also a receiver and this node
	// does not need to be accounted for as a head because
	// we always assume the sender to be a head already.
	//
	if (host.equals(sender.host))
	    return false;

	for (int i = 0; i < nodeVector.size(); i++) {
	    try {
	        Node n = (Node)nodeVector.elementAt(i);

		if (n.head == null)
		    continue;	// not a member

		if (n.head.equals(host))
		    return true;

	    } catch (IndexOutOfBoundsException ie) {
		break;
            }
	}

	return false;
    }

    private int spaces = 0;
    private int treeDepth = 0;
    private int maxDepth = 0;

    private int tree(Node nextNode, boolean display) {
	if (nodeVector.size() == 0)
	    return 0;

	int i;

	if (display && nextNode.host.equals(dummyHeadName))
		System.out.println("");

	for (i = 0; i < spaces; i++) {
	    if (display)
		System.out.print(" ");
	}

	if (display) {
	    if (nextNode.host != null)
	        System.out.print(nextNode.host);
	    else
	        System.out.print(nextNode.address);

	    //
	    // Only show sender's direct members when displaying the host
	    // as the sender.
	    //
	    if (spaces == 0 || !nextNode.host.equals(sender.host)) {
	        int directMembers = 0;

	        for (i = 0; i < nodeVector.size(); i++) {
	            try {
	                Node n = (Node)nodeVector.elementAt(i);
		        if (n.head != null && n.head.equals(nextNode.host))
		            directMembers++;
	            } catch (IndexOutOfBoundsException ie) {
	                System.out.println("Shouldn't happen!");
                        break;
                    }
	        }

	        if (directMembers != 0)
	            System.out.print(" DirectMembers=" + directMembers);
	    }

	    if (shortDisplay == false) {
	        if (nextNode.ttl != 0)
	            System.out.print(" TTL=" + nextNode.ttl);

	        // System.out.print(" Lines=" + nextNode.lines);
	    } 

	    System.out.println("");
	}

	//
	// The first time in here we want to display the sender.
	// If the sender is also a receiver, it will show up one level
	// down in the tree.  In this case, we only want to display it
	// as a member and not check if it's a head because we'd end
	// up with infinite recursion!
	//
	if ((spaces == 0 || !nextNode.host.equals(sender.host)) &&
	    !nextNode.host.equals(nextNode.head)) {

	    spaces += 4;
	    treeDepth++;

	    if (treeDepth > maxDepth)
	        maxDepth++;

	    for (i = 0; i < nodeVector.size(); i++) {
	        try {
	            Node n = (Node)nodeVector.elementAt(i);

		    if (n.head == null)
		        continue;	// not a member

		    if (n.head.equals(nextNode.host))
		        tree(n, display);
	        } catch (IndexOutOfBoundsException ie) {
	            System.out.println("Shouldn't happen!");
                    break;
                }
	    }

	    spaces -= 4;
	    treeDepth--;
	}

	return maxDepth;
    }

}
