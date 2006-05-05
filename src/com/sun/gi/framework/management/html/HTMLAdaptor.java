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
package com.sun.gi.framework.management.html;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Collection;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.sun.gi.comm.users.server.UserManager;
import com.sun.gi.framework.install.SimulationContext;

/**
 * 
 * <p>Title: HTMLAdaptor</p>
 * 
 * <p>Description: This class responses to console management requests via HTTP 
 * requests and responses.  It it designed to be run from its own thread.
 * Once the response is issued, the socket is closed, so the Adaptor is designed
 * as a "one time use" class.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class HTMLAdaptor implements Runnable {
	
	private final static String BANNER_URL = "http://download.java.net/games/ProjectDarkstarHeader.jpg";

	private final static String TITLE = "<title>Project Darkstar Management Console</title>";
	private final static String ACTION_STRING = "action=";
	private final static String ID_STRING = "ID=";
	
	private Socket clientSocket;
	private MBeanServer server;
	private boolean shouldShutDown = false;
	
	public HTMLAdaptor(Socket clientSocket, MBeanServer server) {
		this.clientSocket = clientSocket;
		this.server = server;
	}
	
	public void run() {
		processRequest();
		sendResponse();
	}
	
	private void processRequest() {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			String line = in.readLine();			// just need the first line for the query string.
			if (line == null || line.indexOf('?') == -1) {
				return;
			}
			int queryIndex = line.indexOf('?') + 1;
			parseQueryString(line.substring(queryIndex, line.indexOf(' ', queryIndex)));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void parseQueryString(String queryString) {
		if (queryString.indexOf("shutdown") != -1) { // shutdown the entire server
			shouldShutDown = true;
			return;
		}
		StringTokenizer tokenizer = new StringTokenizer(queryString, "&");
		String action = tokenizer.nextToken().substring(ACTION_STRING.length());
		int appID = Integer.parseInt(tokenizer.nextToken().substring(ID_STRING.length()));
		
		try {
			Object[] params = new Object[] {appID};
			String[] signature = new String[] {"int"};
			if (action.equals("Stop")) {
				server.invoke(new ObjectName(server.getDefaultDomain() + 
								":name=Deployer"), "stopContext", params, signature);
			}
			else if (action.equals("Start")) {
				server.invoke(new ObjectName(server.getDefaultDomain() + 
								":name=Deployer"), "startContext", params, signature);	
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void shutdown() {
		try {
			server.invoke(new ObjectName(server.getDefaultDomain() + 
									":name=SGS"), "shutdown", null, null);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void sendResponse() {
		try {
			PrintWriter out = new PrintWriter(clientSocket.getOutputStream());
			String content = "<html>" + TITLE + "<body>" + (shouldShutDown ? 
									writeShutDown() : listContexts()) + "</body></html>";
			out.print("HTTP/1.0 200 OK\r\nContent-length: " + content.length() + "\r\n");
			out.print("Content-type: text/html\r\n\r\n");
			out.println(content);
			
			out.flush();
			out.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		if (shouldShutDown) {
			shutdown();
		}
	}
	
	private String writeBanner() {
		return "<center>" + "<img src=\"" + BANNER_URL + "\" alt=\"Project Darkstar " +
				"Banner\"><p><h2>Management Console</h2></center>";
	}
	
	private String writeShutDown() {
		return writeBanner() + "<p><p><center>[The server has been shutdown]</center>";
	}
	
	private String listContexts() {
		StringBuffer buffer = new StringBuffer(writeBanner());
		try {
		
			Collection<SimulationContext> contextList = 
								(Collection<SimulationContext>) server.invoke(new ObjectName(server.getDefaultDomain() + 
									":name=Deployer"), "listContexts", null, null);
			buffer.append("<p><p><table width=100% border=1>");
			buffer.append("<tr><th align=left valign=top>ID</th><th align=left>Name</th>" +
						"<th align=left>UserManagers</th>" +
						"<th align=left>Status</th><th align=left>Operation</th></tr>");
			for (SimulationContext curContext : contextList) {
				buffer.append("<tr><td>" + curContext.getID() + "</td><td>" + curContext.getName() + 
						"</td>");
				
				if (curContext.getUserManagers().size() > 0) {
					buffer.append("<td><ul>");
					for (UserManager curUserManager : curContext.getUserManagers()) {
						buffer.append("<li>" + curUserManager.getClass().getName() + "</li>");
						Map<String, String> params = curUserManager.getClientParams();
						if (params.size() > 0) {
							buffer.append("<ul>");
							for (Entry<String, String> curEntry : params.entrySet()) {
								buffer.append("<li>" + curEntry.getKey() + " = " + 
											curEntry.getValue() + "</li>");
							}
							buffer.append("</ul>");
						}
					}
					buffer.append("</ul></td>");
				}
				else {
					buffer.append("<td>None</td>");
				}
				
				buffer.append("<td>" + curContext.getStatus() + "</td><td>" + 
						(curContext.getStatus().equals(SimulationContext.StatusType.STARTED) ? 
								writeActionURL("Stop", curContext.getID() + "") : 
								writeActionURL("Start", curContext.getID() + "")) + 
								"</td></tr>");
			}
			buffer.append("</table>");
			buffer.append("<p><p><a href=\"?shutdown\">[Shutdown Server]</a>");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return buffer.toString();
	}
	
	private String writeActionURL(String action, String value) {
		return "<A HREF=\"?" + ACTION_STRING + action + "&" + ID_STRING + value 
				+ "\">" + action + "</A>";
	}

}
