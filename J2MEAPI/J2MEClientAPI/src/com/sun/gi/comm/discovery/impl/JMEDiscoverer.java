/*
 * JMEDiscoverer.java
 *
 * Created on January 8, 2006, 1:16 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.gi.comm.discovery.impl;

import com.sun.gi.comm.users.client.impl.JMEClientManager;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;


/**
 * Used to discover games based on an XML file that is retrieved from
 * the network
 * @author as93050
 */
public class JMEDiscoverer implements Runnable{
    
    /** Creates a new instance of JMEDiscoverer */
    private String url;
    private JMEClientManager listener;
    private JMEDiscoveredGameImpl[] games;
    
    public JMEDiscoverer(String discoveryUrl) {
        url = discoveryUrl;
        
    }
    
    public void discoverGames() {
        Thread t = new Thread(this);
        t.start();
    }
    /**
     * Return the discovered games
     * @return array of discovered games
     */
    public JMEDiscoveredGameImpl[] games() {
        return games;
    }
    
    /**
     * We need to retrieve the games from the network therefore we need
     * to access the network on a seperate thread
     */
    public void run() {       
        parseXML();
        listener.discoveredGames();        
    }
    
    /**
     * Parse the discovery.xml using the kXml pull parser
     * The code here is hardcoded for the current XML format
     * Long method think about refactoring
     */
    private void parseXML() {
        HttpConnection httpConnection = null;
        try {
            httpConnection = (HttpConnection) Connector.open(url);
            KXmlParser parser = new KXmlParser();
            parser.setInput(new InputStreamReader(httpConnection.openInputStream()));
            Vector foundGames = new Vector();
            Vector userManagers = new Vector();
            boolean done = false;
            JMEDiscoveredGameImpl game = null;
            JMEDiscoveredUserManagerImpl userManager = null;
            while (!done) {
                int tagType = parser.nextTag();
                String tagName;
                if (tagType == XmlPullParser.START_TAG) {
                    tagName = parser.getName();
                    if (tagName.equals("DISCOVERY")) {
                        foundGames.removeAllElements();
                    } else if (tagName.equals("GAME")) {
                        int gameID = Integer.parseInt(parser.getAttributeValue(0));
                        String gameName = parser.getAttributeValue(1);
                        game = new JMEDiscoveredGameImpl(gameID,gameName);
                    } else if (tagName.equals("USERMANAGER")) {
                        String userManagerClass = parser.getAttributeValue(0);
                        userManager = new JMEDiscoveredUserManagerImpl(userManagerClass);
                        userManager.addParameter("GAME_NAME",game.getName());
                    } else if (tagName.equals("PARAMETER")) {
                        String tag = parser.getAttributeValue(0);
                        userManager.addParameter(tag,parser.getAttributeValue(1));
                    }
                } else if (tagType == XmlPullParser.END_TAG) {
                    tagName = parser.getName();
                    if (tagName.equals("GAME")) {
                        handleGameEnd(userManagers, foundGames, game);
                    } else if (tagName.equals("USERMANAGER")) {
                        userManagers.addElement(userManager);
                    } else if (tagName.equals("DISCOVERY")) {
                        done = true;
                    }
                }
            }
            games = new JMEDiscoveredGameImpl[foundGames.size()];
            foundGames.copyInto(games);
        } catch (IOException ex) {
            ex.printStackTrace();
            listener.exceptionOccurred(ex);
        } catch (XmlPullParserException ex) {
            ex.printStackTrace();
            listener.exceptionOccurred(ex);
        } catch (SecurityException ex) {
            ex.printStackTrace();
            listener.exceptionOccurred(ex);
        } finally {
            if (httpConnection != null) {
                try {
                    httpConnection.close();
                } catch (IOException ex) {
                    //not much to do here
                }
            }
        }
    }
    
    private void handleGameEnd(final Vector userManagers, final Vector foundGames, final JMEDiscoveredGameImpl game) {
        JMEDiscoveredUserManagerImpl[] userMgrs = new JMEDiscoveredUserManagerImpl[userManagers.size()];
        userManagers.copyInto(userMgrs);
        game.setUserManagers(userMgrs);
        userManagers.removeAllElements();
        foundGames.addElement(game);
    }
    
    public void setListener(JMEClientManager listener) {
        this.listener = listener;
    }
}
