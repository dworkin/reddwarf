package com.sun.gi;

/**
 *
 * <p>Title: SGS </p>
 * <p>Description: The Sun Game Server.  This is the master app class that gets
 * instantiated.  It coordinbates the creation and inter-wiring of all the
 * levels of the game sever that are resident in a  single slice.</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: Sun Microsystems</p>
 * @author Jeff Kesselman
 * @version 1.0
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
		if (verboseString != null){
			verbose = verboseString.equalsIgnoreCase("true");
		}
		try {			
			kernel = new SimKernelImpl();
			String installProperty = System
					.getProperty("sgs.framework.installurl");
			if (installProperty != null) {
				installFile = installProperty;
			}
			if (verbose){
				System.out.println("Loading configuration from: "+installFile);
			}
			InstallationLoader installation = new InstallationURL(new URL(
					installFile));
			// start framework services
			transportManager = new LRMPTransportManager();
			reportManager = new ReportManagerImpl(transportManager, REPORTTTL);
			long heartbeat = 1000; // 1 sec heartbeat default
			String hbprop = System.getProperty("sgs.framework.timer.heartbeat");
			if (hbprop!= null){
				heartbeat = Long.parseLong(hbprop);
			}
			timerManager = new TimerManagerImpl(heartbeat); 
			kernel.setTimerManager(timerManager);
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
	private Router startupGame(TransportManager transportManager,
			DeploymentRec game, StatusReport installationReport) {
		Router router = null;
		int gameID = game.getID();
		try {
			router = new RouterImpl(transportManager);
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
	
		Simulation sim=null;
		try {
			ostore = new TSOObjectStore(new InMemoryDataSpace(gameID));
			if (System.getProperty("sgs.ostore.startclean").equalsIgnoreCase("true")){
				if (verbose){
					System.out.println("Clearing Object Store");
				}
				ostore.clear();
			}
			sim = new SimulationImpl(kernel,ostore,router, game);
		} catch (InstantiationException e) {
			
			e.printStackTrace();
			return null;
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
		for(int i=0;i<args.length;i++){
			if (args[i].charAt(0)=='-'){
				switch(args[i].charAt(1)){
					case 'v':
					case 'V':
						System.setProperty("sgs.framework.verbose","true");
						break;
					case 'i':
					case 'I':
						System.setProperty("sgs.framework.installurl", args[++i]);
					break;	
					case 'c':
					case 'C':
						System.setProperty("sgs.ostore.startclean","true");
					break;
				}				
			}			
		}
		new SGS();

	}
}
