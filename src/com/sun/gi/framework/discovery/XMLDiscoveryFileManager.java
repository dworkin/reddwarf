/**
 * 
 * <p>
 * Title: XMLDiscoveryFileManager.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.framework.discovery;

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

/**
 * 
 * <p>
 * Title: XMLDiscoveryFileManager.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class XMLDiscoveryFileManager implements Runnable {
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
    
    private boolean shouldShutDown = false;

    static {
        String updatePeriodStr = System.getProperty("sgs.discovery.updateperiod");
        if (updatePeriodStr != null) {
            fileUpdatePeriod = Integer.parseInt(updatePeriodStr);
        }
    }

    public XMLDiscoveryFileManager(ReportManager reportManager) {
        gameMap = new HashMap<Integer, GameRecord>();
        this.reportManager = reportManager;

        Thread t = new Thread(this);
        t.start();
    }
    
    public void run() {
        while (!shouldShutDown) {
            String[] reportNames = reportManager.listReports();
            startDiscoveryFile("discovery.xml");
            for (int i = 0; i < reportNames.length; i++) {
                if (reportNames[i].startsWith(prefix)) {
                    StatusReport report = reportManager.getReport(reportNames[i]);
                    if (report != null) {
                        report.dump(System.err);
                        addReportToDiscoveryFile(report);
                    }
                }
            }
            endDiscoveryFile();
            long time = System.currentTimeMillis();
            long wakeUpTime = time + fileUpdatePeriod;
            System.err.println("Wakeup Time:" + wakeUpTime);
            while (wakeUpTime > time) {
                try {
                    Thread.sleep(wakeUpTime - time);
                } catch (InterruptedException ex1) {
                    ex1.printStackTrace();
                }
                time = System.currentTimeMillis();
            }
            System.err.println("Woke up at: " + System.currentTimeMillis());
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
            String gameIDStr = statusReport.getParameter(gamePrefix, "id");

            // this game was installed, but has since been removed
            if (gameIDStr == null) {
                continue;
            }
            int gameID = Integer.parseInt(gameIDStr);
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
                    System.err.println("WARNING: name mismatch on ID " + gameID
                            + ":");
                    System.err.println("<< " + grec.name);
                    System.err.println(">> " + gameName);
                }
                if (!grec.description.equals(gameDescr)) {
                    System.err.println("WARNING: description mismatch on ID "
                            + gameID + ":");
                    System.err.println("<< " + grec.description);
                    System.err.println(">> " + gameDescr);
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

    public void shutDown() {
        shouldShutDown = true;
    }
}
