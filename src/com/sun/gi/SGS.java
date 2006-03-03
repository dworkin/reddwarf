package com.sun.gi;
/*
        Copyright © 2006 Sun Microsystems, Inc., 
        4150 Network Circle, Santa Clara, California 95054, U.S.A. 
        All rights reserved.

        Sun Microsystems, Inc. has intellectual property rights relating to 
        technology embodied in the product that is described in this 
        document. In particular, and without limitation, these intellectual 
        property rights may include one or more of the U.S. patents listed 
        at http://www.sun.com/patents and one or more additional patents or
        pending patent applications in the U.S. and in other countries.

        U.S. Government Rights - Commercial software. Government users are 
        subject to the Sun Microsystems, Inc. standard license agreement 
        and applicable provisions of the FAR and its supplements.

        Use is subject to license terms.

        This distribution may include materials developed by third parties.

        Sun, Sun Microsystems, the Sun logo and Java are trademarks or 
        registered trademarks of Sun Microsystems, Inc. in the U.S. and 
        other countries.

        This product is covered and controlled by U.S. Export Control laws 
        and may be subject to the export or import laws in other countries.
        Nuclear, missile, chemical biological weapons or nuclear maritime 
        end uses or end users, whether direct or indirect, are strictly 
        prohibited. Export or reexport to countries subject to U.S. embargo 
        or to entities identified on U.S. export exclusion lists, including,
        but not limited to, the denied persons and specially designated 
        nationals lists is strictly prohibited.

        Copyright © 2006 Sun Microsystems, Inc., 
        4150 Network Circle, Santa Clara, California 95054, 
        Etats-Unis. Tous droits réservés.

        Sun Microsystems, Inc. détient les droits de propriété intellectuels 
        relatifs à la technologie incorporée dans le produit qui est 
        décrit dans ce document. En particulier, et ce sans limitation, 
        ces droits de propriété intellectuelle peuvent inclure un ou plus 
        des brevets américains listés à l'adresse 
        http://www.sun.com/patents et un ou les brevets supplémentaires ou 
        les applications de brevet en attente aux Etats - Unis et dans les 
        autres pays.

        L'utilisation est soumise aux termes de la Licence.

        Cette distribution peut comprendre des composants développés par des
        tierces parties.

        Sun, Sun Microsystems, le logo Sun et Java sont des marques de 
        fabrique ou des marques déposées de Sun Microsystems, Inc. aux 
        Etats-Unis et dans d'autres pays.

        Ce produit est soumis à la législation américaine en matière de 
        contrôle des exportations et peut être soumis à la règlementation 
        en vigueur dans d'autres pays dans le domaine des exportations et 
        importations. Les utilisations, ou utilisateurs finaux, pour des 
        armes nucléaires,des missiles, des armes biologiques et chimiques 
        ou du nucléaire maritime, directement ou indirectement, sont 
        strictement interdites. Les exportations ou réexportations vers 
        les pays sous embargo américain, ou vers des entités figurant sur 
        les listes d'exclusion d'exportation américaines, y compris, mais 
        de manière non exhaustive, la liste de personnes qui font objet 
        d'un ordre de ne pas participer, d'une façon directe ou indirecte,
        aux exportations des produits ou des services qui sont régis par 
        la législation américaine en matière de contrôle des exportations 
        et la liste de ressortissants spécifiquement désignés, sont 
        rigoureusement interdites.


 */
/**
 *
 * <p>Title: SGS </p>
 * <p>Description: The Sun Game Server.  This is the master app class that gets
 * instantiated.  It coordinbates the creation and inter-wiring of all the
 * levels of the game sever that are resident in a  single slice.</p>
 */
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.impl.RouterImpl;
import com.sun.gi.comm.users.server.UserManager;
import com.sun.gi.comm.users.validation.UserValidatorFactory;
import com.sun.gi.comm.users.validation.impl.UserValidatorFactoryImpl;
import com.sun.gi.framework.install.DeploymentRec;
import com.sun.gi.framework.install.InstallationLoader;
import com.sun.gi.framework.install.UserMgrRec;
import com.sun.gi.framework.install.ValidatorRec;
import com.sun.gi.framework.install.impl.InstallationURL;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.interconnect.impl.LRMPTransportManager;
import com.sun.gi.framework.rawsocket.impl.RawSocketManagerImpl;
import com.sun.gi.framework.status.ReportManager;
import com.sun.gi.framework.status.ReportUpdater;
import com.sun.gi.framework.status.StatusReport;
import com.sun.gi.framework.status.impl.ReportManagerImpl;
import com.sun.gi.framework.timer.TimerManager;
import com.sun.gi.framework.timer.impl.TimerManagerImpl;
import com.sun.gi.logic.SimKernel;
import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.impl.SimKernelImpl;
import com.sun.gi.logic.impl.SimulationImpl;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.tso.TSOObjectStore;
import com.sun.gi.objectstore.tso.dataspace.InMemoryDataSpace;
import com.sun.gi.objectstore.tso.dataspace.PersistantInMemoryDataSpace;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;

public class SGS {
	ReportManager reportManager;

	TimerManager timerManager;

	TransportManager transportManager;

	ReportUpdater reportUpdater;

	SGSUUID sliceID = new StatisticalUUID();

	SimKernel kernel;

	ObjectStore ostore;

	private boolean verbose = false;

	private String installFile = "file:Install.txt";

	private static final long REPORTTTL = 1000;

	public SGS() {
		String verboseString = System.getProperty("sgs.framework.verbose");
		if (verboseString != null) {
			verbose = verboseString.equalsIgnoreCase("true");
		}
		try {
			kernel = new SimKernelImpl();
			String installProperty = System
					.getProperty("sgs.framework.installurl");
			if (installProperty != null) {
				installFile = installProperty;
			}
			if (verbose) {
				System.out
						.println("Loading configuration from: " + installFile);
			}
			InstallationLoader installation = new InstallationURL(new URL(
					installFile));
			// start framework services
			transportManager = new LRMPTransportManager();
			reportManager = new ReportManagerImpl(transportManager, REPORTTTL);
			long heartbeat = 1000; // 1 sec heartbeat default
			String hbprop = System.getProperty("sgs.framework.timer.heartbeat");
			if (hbprop != null) {
				heartbeat = Long.parseLong(hbprop);
			}
			timerManager = new TimerManagerImpl(heartbeat);
			kernel.setTimerManager(timerManager);

			kernel.setRawSocketManager(new RawSocketManagerImpl());

			// start game services
			StatusReport installationReport = reportManager
					.makeNewReport("_SGS_discover_" + sliceID);
			installationReport.setParameter("game", "count", "0");
			for (DeploymentRec game : installation.listGames()) {
				startupGame(transportManager, game, installationReport);
			}
			reportUpdater = new ReportUpdater(reportManager);
			installationReport.dump(System.out);
			reportUpdater.addReport(installationReport);
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}

	}

	/**
	 * startupGame
	 * 
	 * @param game
	 *            InstallRec
	 */
	private Router startupGame(TransportManager transportMgr,
			DeploymentRec game, StatusReport installationReport) {
		Router router = null;
		int gameID = game.getID();
		try {
			router = new RouterImpl(transportMgr);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		int gameCount = Integer.parseInt(installationReport.getParameter(
				"game", "count"));
		String statusBlockName = "game." + gameCount;
		installationReport.setParameter(statusBlockName, "id", Integer
				.toString(gameID));
		installationReport.setParameter(statusBlockName, "description", game
				.getDescription());
		installationReport
				.setParameter(statusBlockName, "name", game.getName());
		int umgrCount = 0;

		// create simulation container for game
		if (game.getBootClass() != null) {
			Simulation sim = null;
			try {
				ostore = new TSOObjectStore(new PersistantInMemoryDataSpace(
						gameID));
				String cleanProperty = System
						.getProperty("sgs.ostore.startclean");
				if ((cleanProperty != null)
						&& (cleanProperty.equalsIgnoreCase("true"))) {
					if (verbose) {
						System.out.println("Clearing Object Store");
					}
					ostore.clear();
				}
				sim = new SimulationImpl(kernel, ostore, router, game);
			} catch (InstantiationException e) {

				e.printStackTrace();
				return null;
			}
		}

		// create user managers
		for (UserMgrRec umgrRec : game.getUserManagers()) {
			String serverClassName = umgrRec.getServerClassName();
			String umgrBlock = statusBlockName + ".umgr." + umgrCount;
			umgrCount++;
			installationReport.setParameter(statusBlockName + ".umgr", "count",
					Integer.toString(umgrCount));
			try {
				Class serverClass = Class.forName(serverClassName);
				Constructor constructor = serverClass
						.getConstructor(new Class[] { Router.class, Map.class });
				UserManager umgr = (UserManager) constructor
						.newInstance(new Object[] { router,
								umgrRec.getParameterMap() });
				installationReport.setParameter(umgrBlock, "clientClassName",
						umgr.getClientClassname());
				Set clientParams = umgr.getClientParams().entrySet();
				installationReport.setParameter(umgrBlock + ".params", "count",
						Integer.toString(clientParams.size()));
				int c = 0;
				for (Iterator i2 = clientParams.iterator(); i2.hasNext();) {
					Entry entry = (Entry) i2.next();
					installationReport.setParameter(umgrBlock + ".params.keys",
							Integer.toString(c), (String) entry.getKey());
					installationReport.setParameter(umgrBlock
							+ ".params.values", Integer.toString(c),
							(String) entry.getValue());
					c++;
				}
				if (umgrRec.hasValidatorModules()) {
					UserValidatorFactory validatorFactory = new UserValidatorFactoryImpl();
					for (ValidatorRec lmoduleRec : umgrRec
							.getValidatorModules()) {
						String loginModuleClassname = lmoduleRec
								.getValidatorClassName();
						Class loginModuleClass = Class
								.forName(loginModuleClassname);
						validatorFactory.addLoginModule(loginModuleClass,
								lmoduleRec.getParameterMap());
					}
					umgr.setUserValidatorFactory(validatorFactory);
				}
				// start up container

				// add client to list

				// need to start boot method in container if it has one here.

			} catch (Exception ex) {
				ex.printStackTrace();
			}
			gameCount++;
			installationReport.setParameter("game", "count", Integer
					.toString(gameCount));
		}

		return router;
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
					System.setProperty("sgs.framework.installurl", args[++i]);
					break;
				case 'c':
				case 'C':
					System.setProperty("sgs.ostore.startclean", "true");
					break;
				}
			}
		}
		new SGS();

	}
}
