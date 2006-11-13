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
package com.sun.gi.framework.install.impl;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.sun.gi.framework.install.Deployer;
import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.framework.install.InstallationLoader;
import com.sun.gi.framework.install.SimulationContext;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.status.ReportManager;
import com.sun.gi.framework.status.ReportUpdater;
import com.sun.gi.framework.status.StatusReport;
import com.sun.gi.framework.status.impl.ReportManagerImpl;
import com.sun.gi.logic.SimKernel;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;

/**
 * 
 * <p>Title: DeployerImpl</p>
 * 
 * <p>Description: A concrete implementation of the Deployer interface.  This 
 * Deployer creates SimulationContexts from the install.txt config file on
 * startup.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class DeployerImpl implements Deployer {
	
    private static final long REPORTTTL = 1000;
    
    private TransportManager transportManager;
    private Map<Integer, SimulationContext> contextMap;
    private SGSUUID sliceID = new StatisticalUUID();
    private StatusReport installationReport;
    
    public DeployerImpl(SimKernel kernel, TransportManager transportManager, 
    					URL url) throws InstantiationException {
    	
    	//this.kernel = kernel;
    	this.transportManager = transportManager;
    	contextMap = new HashMap<Integer, SimulationContext>();
    	
    	InstallationLoader loader = null;
    	try {
    	    loader = new InstallationURL(url);
    	}
    	catch (Exception e) {
    	    e.printStackTrace();
    	}
    	if (loader == null) {
    	    throw new InstantiationException("Missing Installation Loader");
    	}
    	for (DeploymentRec curRec : loader.listGames()) {
    	    SimulationContext curContext = new SimulationContextImpl(kernel, 
    						transportManager, curRec);
    	    contextMap.put(curContext.getID(), curContext);
    	}
    	firstStart();
    }
    
    private ReportManager reportManager=null;
    
    /**
     * This should be called once on server start, and not ever again.
     */
    private void firstStart() {
        reportManager = null;
        try {
            reportManager = new ReportManagerImpl(transportManager, REPORTTTL);
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        installationReport = reportManager.makeNewReport("_SGS_discover_" + sliceID);
        installationReport.setParameter("game", "count", "0");
        
        for (Entry<Integer, SimulationContext> curEntry : contextMap.entrySet()) {
            startContext(curEntry.getKey());
        }
        ReportUpdater reportUpdater = new ReportUpdater(reportManager);
        installationReport.dump(System.err);
        reportUpdater.addReport(installationReport);
    }
    
    public void startContext(int appID) {
    	SimulationContext context = contextMap.get(appID);
    	if (context != null) {
    	    context.start(installationReport);
    	}
    }
    
    public void stopAll() {
    	for (SimulationContext curContext : contextMap.values()) {
    	    stopContext(curContext.getID());
    	}
    }
    
    public void stopContext(int appID) {
    	SimulationContext context = contextMap.get(appID);
    	if (context != null) {
    	    context.stop(installationReport);
    	}
    }
    
    public Collection<SimulationContext> listContexts() {
    	return Collections.unmodifiableCollection(contextMap.values());
    }
    
    /**
     * @return
     */
    public ReportManager getReportManager(){
        return reportManager;
    }

}
