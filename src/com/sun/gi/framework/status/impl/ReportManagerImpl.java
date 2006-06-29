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

package com.sun.gi.framework.status.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.status.ReportManager;
import com.sun.gi.framework.status.StatusReport;

public class ReportManagerImpl
        implements ReportManager, TransportChannelListener
{
    Map<String, StatusReportImpl> reports =
        new HashMap<String, StatusReportImpl>();

    TransportChannel chan;
    long reportLife;

    public ReportManagerImpl(TransportManager tmgr, long reportLife)
            throws IOException {
        chan = tmgr.openChannel("_SGS_reports");
        this.reportLife = reportLife;
        chan.addListener(this);
    }

    /**
     * getCurrentStatus
     * 
     * @param rootName String
     * @return StatusReport
     */
    public StatusReport getReport(String rootName) {
        StatusReportImpl report = null;
        synchronized (reports) {
            report = reports.get(rootName);
            if (report.expirationDate < System.currentTimeMillis()) {
                reports.remove(rootName);
                report = null;
            }
        }
        return report;
    }

    /**
     * listReports
     * 
     * @return String[]
     */
    public String[] listReports() {
        expireReports();
        String[] reportNames = null;
        synchronized (reports) {
            Set<String> keys = reports.keySet();
            reportNames = new String[keys.size()];
            int c = 0;
            for (String key : keys) {
                reportNames[c++] = key;
            }

        }
        return reportNames;
    }

    /**
     * expireReports
     */
    private void expireReports() {
        synchronized (reports) {
            long time = System.currentTimeMillis();
            for (Iterator<StatusReportImpl> i =
                    reports.values().iterator();
                 i.hasNext(); )
            {
                StatusReportImpl report = i.next();
                if (report.expirationDate < time) {
                    i.remove();
                }
            }
        }
    }

    /**
     * makeNewReport
     * 
     * @param rootName String
     * @return StatusReport
     */
    public StatusReport makeNewReport(String rootName) {
        return new StatusReportImpl(rootName);
    }

    /**
     * sendReport
     * 
     * @param report StatusReport
     */
    public void sendReport(StatusReport report) throws IOException {
        ((StatusReportImpl) report).expirationDate = System.currentTimeMillis()
                + reportLife;
        ByteBuffer buff = ByteBuffer.allocate(report.reportSize());
        report.writeReport(buff);
        chan.sendData(buff);
        buff.flip();
        dataArrived(buff); // log to self
    }

    /**
     * channelClosed
     */
    public void channelClosed() {
        throw new RuntimeException("Report channel has been closed!");
    }

    /**
     * dataArrived
     * 
     * @param buff ByteBuffer
     */
    public void dataArrived(ByteBuffer buff) {
        StatusReportImpl report = new StatusReportImpl(buff);
        reports.put(report.rootName(), report);
    }

    /**
     * getReportTTL
     * 
     * @return long
     */
    public long getReportTTL() {
        return reportLife;
    }
}
