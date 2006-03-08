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

package com.sun.gi.gamespy.test;

import com.sun.gi.gamespy.JNIQueryReport;
import com.sun.gi.gamespy.QuereyReportListener;

public class QuereyTest implements QuereyReportListener {
    long sessionID;

    public QuereyTest() {
        JNIQueryReport.addListener(this);
        JNIQueryReport.registerKey(99, "Agent99");
        // JNIQueryReport.registerKey(1,"Jeff's Test");
        sessionID = JNIQueryReport.init("10.5.34.17", 1100, "altitude",
                "DZzvoR");
        System.out.println("SessionID = " + sessionID);
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    JNIQueryReport.think(sessionID);
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        new QuereyTest();

    }

    /**
     * addError
     * 
     * @param errorCode int
     * @param message String
     */
    public void addError(int errorCode, String message) {
        System.out.println("Add Error (" + errorCode + ") " + message);
    }

    /**
     * countCallback
     * 
     * @param countType int
     * @return int
     */
    public int countCallback(int countType) {
        System.out.println("Count requested of type " + countType);
        return 0;
    }

    /**
     * keyListCallback
     * 
     * @param listType int
     * @return int[]
     */
    public int[] keyListCallback(int listType) {
        System.out.println("Key List requested");
        int[] out = { 1, 3, 4, 5, 6, 99 };
        return out;
    }

    /**
     * serverKeyCallback
     * 
     * @param keyid int
     *
     * @return byte[]
     */
    public byte[] serverKeyCallback(int keyid) {
        System.out.println("Server Key Value Requested for keyid=" + keyid);
        switch (keyid) {
            case 1:
                return "JeffHost\000".getBytes();
            case 3:
                return "1.0\000".getBytes();
            case 4:
                return "1141\000".getBytes();
            case 5:
                return "A Map\000".getBytes();
            case 6:
                return "A Game Type\000".getBytes();
            case 99:
                return "GetSmartServer!\000".getBytes();
        }
        return null;
    }

    /**
     * serverKeyCallback
     * 
     * @param keyid int
     * @return byte[]
     */
    public byte[] playerKeyCallback(int keyid, int team) {
        System.out.println("Player Key Value Requested for keyid=" + keyid
                + " team=" + team);
        switch (keyid) {
            case 99:
                return ("GetSmartClient(" + team + ")").getBytes();
        }
        return null;
    }

    /**
     * teamKeyCallback
     * 
     * @param keyid int
     * @param team int
     * @return byte[]
     */
    public byte[] teamKeyCallback(int keyid, int team) {
        System.out.println("Player Key Value Requested for keyid=" + keyid
                + " team=" + team);
        switch (keyid) {
            case 99:
                return ("GetSmartTeam(" + team + ")").getBytes();
        }
        return null;
    }
}
