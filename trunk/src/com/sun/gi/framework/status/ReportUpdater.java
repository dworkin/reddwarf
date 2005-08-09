package com.sun.gi.framework.status;

import java.io.*;
import java.util.*;

public class ReportUpdater {
  final ReportManager reportManager;
  long updateTime;
  long lastUpdate;
  List reports = new ArrayList();
  Thread thread;

  public ReportUpdater(ReportManager mgr) {
    reportManager =mgr;
    updateTime = mgr.getReportTTL();
    thread = new Thread(new Runnable() {
      public void run() {
        lastUpdate = System.currentTimeMillis();
        while(true){
          try {
            Thread.sleep(updateTime);
          }
          catch (InterruptedException ex) {
            ex.printStackTrace();
          }
          long time =System.currentTimeMillis();
          if (time >lastUpdate+(updateTime)){
            lastUpdate = time;
            for(Iterator i = reports.iterator();i.hasNext();){
              try {
                reportManager.sendReport( (StatusReport) i.next());
              }
              catch (IOException ex1) {
                ex1.printStackTrace();
              }
            }
          }
        }
      }
    });
    thread.start();
  }

  /**
   * addReport
   *
   * @param installationReport StatusReport
   */
  public void addReport(StatusReport installationReport) {
    reports.add(installationReport);
  }
}
