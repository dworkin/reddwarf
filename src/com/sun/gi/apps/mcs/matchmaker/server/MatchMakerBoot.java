/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

package com.sun.gi.apps.mcs.matchmaker.server;

import java.io.IOException;
import java.net.URL;
import java.security.Principal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import javax.security.auth.Subject;

import com.sun.gi.apps.mcs.matchmaker.common.CommandProtocol;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserListener;
import com.sun.gi.utils.SGSUUID;

/**
 * <p>
 * Title: MatchMakerBoot
 * </p>
 * 
 * <p>
 * Description: The boot class for the MCS Match Maker application. When
 * users join, they are wrapped in a Player object and registered in the
 * GLO namespace under their UserID.
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public class MatchMakerBoot
        implements SimBoot<MatchMakerBoot>, SimUserListener
{
    private static final long serialVersionUID = 1L;

    private GLOReference<Folder> folderRoot;

    /**
     * {@inheritDoc}
     */
    public void boot(GLOReference<? extends MatchMakerBoot> bootGLO,
            boolean firstBoot)
    {
        SimTask task = SimTask.getCurrent();
        if (firstBoot) {
            System.out.println("MatchMakerBoot: firstBoot");
            if (task.findGLO("UsernameMap") == null) {
                task.createGLO(new GLOMap<String, UserID>(), "UsernameMap");
            }

            if (task.findGLO("LobbyMap") == null) {
                task.createGLO(new GLOMap<SGSUUID, GLOReference<Lobby>>(),
                        "LobbyMap");
            }

            if (task.findGLO("GameRoomMap") == null) {
                task.createGLO(new GLOMap<SGSUUID, GLOReference<GameRoom>>(),
                        "GameRoomMap");
            }

            folderRoot = task.createGLO(createRootFolder(task));

        }
        task.addUserListener(bootGLO);
        initChannels(task);
    }

    private void initChannels(SimTask task) {
        GLOReference<GLOMap<SGSUUID, GLOReference>> lobbyRef =
            task.findGLO("LobbyMap");
        openChannels(task, lobbyRef, true);

        GLOReference<GLOMap<SGSUUID, GLOReference>> gameRoomRef =
            task.findGLO("GameRoomMap");
        openChannels(task, gameRoomRef, false);
    }

    /**
     * Iterates through the given map of channel room references and
     * refreshes their channel ID.  If refreshMapping is true, it will 
     * also re-map the channel room to the new CID.
     * 
     * @param task
     * @param gloMapRef
     * @param refreshMapping		
     */
    private void openChannels(SimTask task,
            GLOReference<GLOMap<SGSUUID, GLOReference>> gloMapRef, 
            boolean refreshMapping) {
    	
        GLOMap<SGSUUID, GLOReference> gloMap = gloMapRef.get(task);
        Iterator iterator = gloMap.keySet().iterator();
        HashMap<SGSUUID, GLOReference> map =
            new HashMap<SGSUUID, GLOReference>();
        while (iterator.hasNext()) {
            GLOReference ref = gloMap.get(iterator.next());
            ChannelRoom curRoom = (ChannelRoom) ref.get(task);
            ChannelID cid = task.openChannel(curRoom.getChannelName());
            curRoom.setChannelID(cid);
            if (refreshMapping) {
            	map.put(cid, ref);
            }
        }
        if (!refreshMapping) {
        	return;
        }
        gloMap.clear();
        Iterator<SGSUUID> mapIterator = map.keySet().iterator();
        while (mapIterator.hasNext()) {
            SGSUUID curKey = mapIterator.next();
            gloMap.put(curKey, map.get(curKey));
        }
    }

    /*
     * Called when a new user connects to the server. A new Player
     * object is constructed and set as the user's "command proxy". 
     * 
     * @see com.sun.gi.logic.SimUserListener#userJoined
     */
    public void userJoined(UserID uid, Subject subject) {
        SimTask task = SimTask.getCurrent();
        System.out.println("Match Maker User Joined");

        GLOMap<String, UserID> userMap =
            (GLOMap<String, UserID>) task.findGLO("UsernameMap").get(task);
        System.out.println("userJoined: map size " + userMap.size());
        // TODO sten: don't know how to handle duplicate logins yet.
        // if (!userMap.containsKey(uid)) {
        Set<Principal> principles = subject.getPrincipals();
        Principal principal = principles.iterator().next();
        String username = principal.getName();
        userMap.put(username, uid);
        Player p = new Player(uid, username, folderRoot);

        System.out.println("Adding username " + username + " uid " + uid);

        // map the player reference to its UserID for later lookup by
        // other players.
        GLOReference<Player> pRef = task.findGLO(uid.toString());
        if (pRef == null) {
            pRef = task.createGLO(p, uid.toString());
            if (pRef == null) {
                pRef = task.findGLO(uid.toString());
            }
        }
        task.addUserDataListener(uid, pRef);

        task.join(uid,
                task.openChannel(CommandProtocol.LOBBY_MANAGER_CONTROL_CHANNEL));
        // }
    }

    /**
     * {@inheritDoc}
     */
    public void userLeft(UserID uid) {
        SimTask task = SimTask.getCurrent();
        GLOReference pRef = task.findGLO(uid.toString());

        if (pRef == null) {
            return;
        }
        Player player = (Player) pRef.get(task);
        GLOMap<String, UserID> userMap = (GLOMap<String, UserID>) task.findGLO(
                "UsernameMap").get(task);
        if (userMap.containsKey(player.getUserName())) {
            System.out.println("removing " + player.getUserName() + " from map");
            userMap.remove(uid);
        }
        // this currently throws an exception
        // pRef.delete(task);
    }

    /**
     * Creates the root of the Folder tree, creating Lobbies and
     * subfolders along the way.
     * 
     * @param task the SimTask to generate all the GLOReferences.
     */
    private Folder createRootFolder(SimTask task) {
        URL url = null;
        try {
            String appname = task.getAppName();
            String propName = "sgs.game."+
    			appname.replaceAll(" ","_").toLowerCase()+".rootURL";
            String root = System.getProperty(propName);

            url = new URL(root + "/matchmaker_config.xml");
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        ConfigParser parser = new ConfigParser(url);

        return parser.getFolderRoot(task);

    }

    private Folder createTestFolders(SimTask task) {
        Folder root = new Folder("Test Game", "Some Description");

        Folder sub1 = new Folder("I can play!", "Some Description");
        Folder sub2 = new Folder("Hurt Me Plenty", "Some Description");
        Folder sub3 = new Folder("Nightmare", "Some Description");

        root.addFolder(task.createGLO(sub1));
        root.addFolder(task.createGLO(sub2));
        root.addFolder(task.createGLO(sub3));

        return root;
    }

}
