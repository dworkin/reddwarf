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
