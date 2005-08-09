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
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.comm.users.*;
import com.sun.gi.comm.validation.impl.*;
import com.sun.gi.framework.install.*;

import com.sun.gi.comm.discovery.Advertisement;
import java.util.Map.Entry;
import com.sun.gi.framework.status.ReportManager;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.interconnect.impl.LRMPTransportManager;
import com.sun.gi.framework.status.impl.ReportManagerImpl;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;
import com.sun.gi.framework.status.StatusReport;
import com.sun.gi.framework.status.ReportUpdater;
import com.sun.gi.comm.routing.nohb.NOHBRouter;

public class SGS {
  ReportManager reportManager;
  TransportManager transportManager;
  ReportUpdater reportUpdater;
  SGSUUID sliceID = new StatisticalUUID();
  private String installFile = "Install.txt";
  private static final long REPORTTTL = 1000;
  public SGS() {
    try {
      String installProperty = System.getProperty("sgs.framework.installfile");
      if (installProperty!=null) {
        installFile = installProperty;
      }
      InstallationLoader installation =
          new InstallationFile(new File(System.getProperty("user.dir") +
                                        File.separator + installFile));
      // start framework services
      transportManager = new LRMPTransportManager();
      reportManager = new ReportManagerImpl(transportManager, REPORTTTL);
      // start game services
      StatusReport installationReport =
          reportManager.makeNewReport("_SGS_discover_" + sliceID);
      installationReport.setParameter("game", "count", "0");
      for (Iterator i = installation.listGames().iterator(); i.hasNext(); ) {
        InstallRec game = (InstallRec) i.next();
        startupGame(transportManager, game, installationReport);
      }
      reportUpdater = new ReportUpdater(reportManager);
      installationReport.dump(System.out);
      reportUpdater.addReport(installationReport);
    }
    catch (Exception ex) {
      ex.printStackTrace();
      System.exit(1);
    }

  }

  /**
   * startupGame
   *
   * @param game InstallRec
   */
  private Router startupGame(TransportManager transportManager,
                             InstallRec game, StatusReport installationReport) {
    Router router = null;
    int gameID = game.getID();
    try {
      router = new NOHBRouter(transportManager, gameID);
    } catch (Exception e){
      e.printStackTrace();
      return null;
    }
    int gameCount =
        Integer.parseInt(installationReport.getParameter("game", "count"));
    String statusBlockName = "game." + gameCount;
    installationReport.setParameter(
        statusBlockName, "id", Integer.toString(gameID));
    installationReport.setParameter(
        statusBlockName, "description", game.getDescription());
    installationReport.setParameter(
        statusBlockName, "name", game.getName());
    int umgrCount = 0;
    
    // create simulation container for game

    
    // create user managers
    for (Iterator i = game.listUserManagers(); i.hasNext(); ) {
      UserMgrRec umgrRec = (UserMgrRec) i.next();
      String serverClassName = umgrRec.getServerClassName();
      String umgrBlock = statusBlockName + ".umgr." + umgrCount;
      umgrCount++;
      installationReport.setParameter(
          statusBlockName + ".umgr", "count", Integer.toString(umgrCount));
      try {
        Class serverClass = Class.forName(serverClassName);
        Constructor constructor = serverClass.getConstructor(new Class[] {
            Router.class, Map.class});
        UserManager umgr = (UserManager) constructor.newInstance(new Object[] {
            router,umgrRec.getParameterMap()});
        installationReport.setParameter(umgrBlock, "clientClassName",
                                        umgr.getClientClassname());
        Set clientParams = umgr.getClientParams().entrySet();
        installationReport.setParameter(
            umgrBlock + ".params", "count",
            Integer.toString(clientParams.size()));
        int c = 0;
        for (Iterator i2 = clientParams.iterator(); i2.hasNext(); ) {
          Entry entry = (Entry) i2.next();
          installationReport.setParameter(
              umgrBlock + ".params.keys", Integer.toString(c),
              (String) entry.getKey());
          installationReport.setParameter(
              umgrBlock + ".params.values", Integer.toString(c),
              (String) entry.getValue());
          c++;
        }
        if (umgrRec.hasLoginModules()) {
          LoginModuleValidatorFactory validatorFactory = new
              LoginModuleValidatorFactory();
          for (Iterator i2 = umgrRec.listLoginModules(); i2.hasNext(); ) {
            LoginModuleRec lmoduleRec = (LoginModuleRec) i2.next();
            String loginModuleClassname = lmoduleRec.getModuleClassName();
            Class loginModuleClass = Class.forName(loginModuleClassname);
            validatorFactory.addLoginModule(loginModuleClass);
          }
          umgr.setUserValidatorFactory(validatorFactory);
        }
        // add client to list

        
        // need to start boot method in container if it has one here.

        
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
      gameCount++;
      installationReport.setParameter("game", "count", Integer.toString(gameCount));
    }

    return router;
  }

  static public void main(String[] args) {
    new SGS();

  }
}
