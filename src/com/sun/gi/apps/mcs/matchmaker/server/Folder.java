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

package com.sun.gi.apps.mcs.matchmaker.server;

import java.util.ArrayList;
import java.util.List;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;

/**
 * <p>
 * Title: Folder
 * </p>
 * 
 * <p>
 * Description: Represents a Lobby Folder in the Match Maker
 * application. A Folder can contain any number of subfolders and
 * Lobbies.
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public class Folder implements GLO {

    private static final long serialVersionUID = 1L;

    private List<GLOReference<Folder>> folderList;
    private List<GLOReference<Lobby>> lobbyList;

    private String name;
    private String description;
    private SGSUUID folderID;

    public Folder(String name, String description) {
        this.name = name;
        this.description = description;
        folderList = new ArrayList<GLOReference<Folder>>();
        lobbyList = new ArrayList<GLOReference<Lobby>>();
        folderID = new StatisticalUUID();
    }

    /**
     * Adds a subfolder to the Folder list.
     * 
     * @param folder the folder to add.
     */
    public void addFolder(GLOReference<Folder> folder) {
        folderList.add(folder);
    }

    /**
     * Adds a Lobby to the lobby list as a GLOReference.
     * 
     * @param lobby the lobby to add.
     */
    public void addLobby(GLOReference<Lobby> lobby) {
        lobbyList.add(lobby);
    }

    /**
     * Called recursively in an attempt to find the folder in the
     * hierarchy matching the given folderID. Returns null if no
     * matching folder is found.
     * 
     * @param task the SimTask used to peek at the folders.
     * @param aFolderID the UUID to match on.
     * 
     * @return the Folder with the matching folderID (with peek access),
     *         or null if not found.
     */
    public Folder findFolder(SimTask task, SGSUUID aFolderID) {
        for (GLOReference<Folder> folderRef : folderList) {
            Folder curFolder = folderRef.peek(task);
            if (curFolder.getFolderID().equals(aFolderID)) {
                return curFolder;
            }
            Folder subFolder = curFolder.findFolder(task, aFolderID);
            if (subFolder != null) {
                return subFolder;
            }
        }

        return null;
    }

    /**
     * Attempts to find a Lobby in the lobby list with a lobby name
     * matching lobbyName. "Peek" access is used to do the lookups. If a
     * matching Lobby is found, "get" access is used.
     * 
     * @param task the SimTask
     * @param lobbyName the name to lookup
     * 
     * @return any matching Lobby with "get" access.
     */
    public Lobby findLobby(SimTask task, String lobbyName) {
        for (GLOReference<Lobby> ref : lobbyList) {
            Lobby curLobby = ref.peek(task);
            if (curLobby.getName().equals(lobbyName)) {
                return ref.get(task);
            }
        }
        return null;
    }

    /**
     * Returns a list of GLOReferences of type Lobby that this Folder is
     * hosting.
     * 
     * @return a list of GLOReferences of Lobbies.
     */
    public List<GLOReference<Lobby>> getLobbies() {
        return lobbyList;
    }

    /**
     * Returns a list of GLOReferences of type Folder which represents
     * the subfolders.
     * 
     * @return alist of GLOReferences of Folders.
     */
    public List<GLOReference<Folder>> getFolders() {
        return folderList;
    }

    /**
     * Returns this Folder's name.
     * 
     * @return the folder name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns this Folder's description.
     * 
     * @return the description of this folder.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns this Folder's UUID.
     * 
     * @return the Folder's UUID.
     */
    public SGSUUID getFolderID() {
        return folderID;
    }
}
