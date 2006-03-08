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

package com.sun.gi;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.interconnect.impl.LRMPTransportManager;
import com.sun.gi.framework.status.ReportManager;
import com.sun.gi.framework.status.StatusReport;
import com.sun.gi.framework.status.impl.ReportManagerImpl;

public class DiscoveryServer {

    class GameRecord {
        String name;

        int id;

        String description;

        List<UserManagerRecord> userManagerRecords;

        GameRecord(int id, String name, String descr) {
            this.name = name;
            this.id = id;
            userManagerRecords = new ArrayList<UserManagerRecord>();
            description = descr;
        }

        public void addUserManager(UserManagerRecord rec) {
            userManagerRecords.add(rec);
        }

    }

    class UserManagerRecord {
        String clientClassName;

        Map<String, String> params;

        UserManagerRecord(String clientClassName) {
            this.clientClassName = clientClassName;
            params = new HashMap<String, String>();

        }

        public void setParameter(String key, String value) {
            params.put(key, value);
        }

    }

    Map<Integer, GameRecord> gameMap;

    ReportManager reportManager;
    TransportManager transportManager;

    static long fileUpdatePeriod = 50000; // regenreate once a second
    final static String prefix = "_SGS_discover_";

    FileChannel chan;
    FileLock lock;

    String discoveryFileName;

    static {
        String updatePeriodStr =
            System.getProperty("sgs.discovery.updateperiod");
        if (updatePeriodStr != null) {
            fileUpdatePeriod = Integer.parseInt(updatePeriodStr);
        }
    }

    public DiscoveryServer() {
        gameMap = new HashMap<Integer, GameRecord>();
        transportManager = new LRMPTransportManager();
        try {
            // does not initate reports
            reportManager = new ReportManagerImpl(transportManager, 0);
            while (true) {
                String[] reportNames = reportManager.listReports();
                startDiscoveryFile("discovery.xml");
                for (int i = 0; i < reportNames.length; i++) {
                    if (reportNames[i].startsWith(prefix)) {
                        StatusReport report =
                            reportManager.getReport(reportNames[i]);
                        if (report != null) {
                            report.dump(System.out);
                            addReportToDiscoveryFile(report);
                        }
                    }
                }
                endDiscoveryFile();
                long time = System.currentTimeMillis();
                long wakeUpTime = time + fileUpdatePeriod;
                System.out.println("Wakeup Time:" + wakeUpTime);
                while (wakeUpTime > time) {
                    try {
                        Thread.sleep(wakeUpTime - time);
                    } catch (InterruptedException ex1) {
                        ex1.printStackTrace();
                    }
                    time = System.currentTimeMillis();
                }
                System.out.println("WOke up at: " + System.currentTimeMillis());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * endDiscoveryFile
     */
    private void endDiscoveryFile() {

        ByteBuffer buff = ByteBuffer.allocate(100 * 1024);
        buff.put("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes());
        buff.put("<DISCOVERY>\n".getBytes());

        for (GameRecord grec : gameMap.values()) {
            buff.put(("\t<GAME id=\"" + grec.id + "\" name=\"" + grec.name + "\" >\n").getBytes());
            for (UserManagerRecord urec : grec.userManagerRecords) {
                buff.put(("\t\t<USERMANAGER clientclass=\""
                        + urec.clientClassName + "\" >\n").getBytes());

                for (Entry<String, String> entry : urec.params.entrySet()) {
                    buff.put(("\t\t\t<PARAMETER tag=\"" + entry.getKey()
                            + "\" value=\"" + entry.getValue() + "\" />\n").getBytes());
                }
                buff.put("\t\t</USERMANAGER>\n".getBytes());

            }
            buff.put("\t</GAME>\n".getBytes());
        }

        buff.put("</DISCOVERY>\n".getBytes());
        buff.flip();

        while (true) {
            try {
                FileOutputStream fos = new FileOutputStream(discoveryFileName,
                        false);
                FileChannel fchan = fos.getChannel();
                fchan.write(buff);
                fchan.close();
                fos.close();
                return;
            } catch (Exception ex) {
                // temporarily since we cant stop the lock exception,
                // just
                // handle it
                // ex.printStackTrace();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex1) {
                    ex1.printStackTrace();
                }
            }
        }

    }

    /**
     * addReportToDiscoveryFile
     * 
     * @param statusReport StatusReport
     */
    private void addReportToDiscoveryFile(StatusReport statusReport) {
        int gameCount = Integer.parseInt(statusReport.getParameter("game",
                "count"));
        for (int g = 0; g < gameCount; g++) {
            String gamePrefix = "game." + g;
            int gameID = Integer.parseInt(statusReport.getParameter(gamePrefix,
                    "id"));
            String gameName = statusReport.getParameter(gamePrefix, "name");
            String gameDescr = statusReport.getParameter(gamePrefix,
                    "description");
            Integer ti = new Integer(gameID);
            GameRecord grec = gameMap.get(ti);
            if (grec == null) { // first time mentioned
                grec = new GameRecord(gameID, gameName, gameDescr);
                gameMap.put(ti, grec);
            } else {
                if (!grec.name.equals(gameName)) {
                    System.out.println("WARNING: name mismatch on ID " + gameID
                            + ":");
                    System.out.println("<< " + grec.name);
                    System.out.println(">> " + gameName);
                }
                if (!grec.description.equals(gameDescr)) {
                    System.out.println("WARNING: description mismatch on ID "
                            + gameID + ":");
                    System.out.println("<< " + grec.description);
                    System.out.println(">> " + gameDescr);
                }
            }
            // now do user managers
            int userManagerCount = Integer.parseInt(statusReport.getParameter(
                    gamePrefix + ".umgr", "count"));
            for (int u = 0; u < userManagerCount; u++) {
                String umgrPrefix = gamePrefix + ".umgr." + u;
                String clientClassName = statusReport.getParameter(umgrPrefix,
                        "clientClassName");
                UserManagerRecord urec = new UserManagerRecord(clientClassName);
                grec.addUserManager(urec);
                // do umgr params
                int paramCount = Integer.parseInt(statusReport.getParameter(
                        umgrPrefix + ".params", "count"));
                for (int p = 0; p < paramCount; p++) {
                    String key = statusReport.getParameter(umgrPrefix
                            + ".params.keys", Integer.toString(p));
                    String value = statusReport.getParameter(umgrPrefix
                            + ".params.values", Integer.toString(p));
                    urec.setParameter(key, value);
                }
            }
        }
    }

    private void startDiscoveryFile(String string) {
        gameMap.clear();
        discoveryFileName = string;
    }

    static public void main(String[] args) {
        new DiscoveryServer();
    }
}
