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

import java.net.URL;

import com.sun.gi.framework.discovery.XMLDiscoveryFileManager;
import com.sun.gi.framework.install.Deployer;
import com.sun.gi.framework.install.impl.DeployerImpl;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.interconnect.impl.NullTransportManager;
import com.sun.gi.framework.management.ManagerAgent;
import com.sun.gi.framework.rawsocket.impl.RawSocketManagerImpl;
import com.sun.gi.framework.timer.TimerManager;
import com.sun.gi.framework.timer.impl.TimerManagerImpl;
import com.sun.gi.logic.SimKernel;
import com.sun.gi.logic.impl.SimKernelImpl;

/**
 * <p>
 * Title: SGS
 * </p>
 * <p>
 * Description: The Sun Game Server. This is the master app class that
 * gets instantiated. It coordinates the creation and inter-wiring of
 * all the levels of the game sever that are resident in a single slice.
 * </p>
 */
public class SGS {
    TimerManager timerManager;
    TransportManager transportManager;
    SimKernel kernel;
    private boolean verbose = false;
    private Deployer deployer;
    private ManagerAgent agent;
    private XMLDiscoveryFileManager localDiscoveryXMLMgr;

    public SGS() {
	String verboseProp = System.getProperty("sgs.framework.verbose", "false");
	verbose = Boolean.parseBoolean(verboseProp);
	try {
	    kernel = new SimKernelImpl();
	    String installFile =
                    System.getProperty("sgs.framework.installurl", "file:SGS-apps.conf");
	    if (verbose) {
		System.err.println("Loading configuration from: " + installFile);
	    }
	    
	    // start framework services
	    //transportManager = new LRMPTransportManager();
	    transportManager = new NullTransportManager();
	    String hbprop =
                    System.getProperty("sgs.framework.timer.heartbeat", "100");
            long heartbeat = Math.max(Long.parseLong(hbprop), 1);
	    timerManager = new TimerManagerImpl(heartbeat);
	    kernel.setTimerManager(timerManager);

	    kernel.setRawSocketManager(new RawSocketManagerImpl());
            
	    // start game services

	    deployer = new DeployerImpl(kernel, transportManager, 
				new URL(installFile));
 	    
	    int managerPort = Integer.parseInt(
                    System.getProperty("sgs.framework.management.port", "0"));
	    if (managerPort > 0) {
	    	startManagementAgent(managerPort);
	    }
            
	    localDiscoveryXMLMgr = 
                new XMLDiscoveryFileManager(((DeployerImpl)deployer).getReportManager());

	    
	} catch (Exception ex) {
	    ex.printStackTrace();
	    System.exit(1);
	}
		
    }
    
    /**
     * Starts the HTML agent for remote management on the given port. 
     */
    private void startManagementAgent(int port) {
        agent = new ManagerAgent();
        agent.createDeployerMBean(deployer);
        agent.createSGSMBean(this);
        agent.startHTMLAdapter(port);
    }
    
    public void shutdown() {
        localDiscoveryXMLMgr.shutDown();
    	if (deployer != null) {
    	    deployer.stopAll();
    	    agent.shutdown();
    	}
    	System.exit(0);
    }

    static public void main(String[] args) {
	for (int i = 0; i < args.length; i++) {
	    if (args[i].charAt(0) == '-') {
		switch (args[i].charAt(1)) {
		    case 'v':
		    case 'V':
			System.setProperty("sgs.framework.verbose", "true");
			break;
		    case 'i':
		    case 'I':
			System.setProperty("sgs.framework.installurl",
				args[++i]);
			break;
		    case 'c':
		    case 'C':
			System.setProperty("sgs.ostore.startclean", "true");
			break;
		    default:
			System.err.println("Unrecognized switch `" + args[i]
				+ "'");
		}
	    } else {
		System.err.println("Unrecognized argument `" + args[i] + "'");
                System.err.println("Recognized switches are: ");
                System.err.println(" -V (-v)  Verbose operation (note that SGS logs are a better way to get output)");
                System.err.println(" -C (-c)  Start clean (delete the object store.)");
                System.err.println(" -I (-i) <conf file url>  Use .conf file from URL");                            
                System.exit(-1);
	    }
	}
	new SGS();
    }
}
