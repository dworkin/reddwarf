package com.sun.gi.framework.status.impl;

import com.sun.gi.framework.status.ReportManager;
import com.sun.gi.framework.status.StatusReport;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;
import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import com.sun.gi.framework.interconnect.TransportChannelListener;

public class ReportManagerImpl
    implements ReportManager , TransportChannelListener {
  Map reports = new HashMap();
  TransportChannel chan;
  long reportLife;
  public ReportManagerImpl(TransportManager tmgr,long reportLife) throws IOException {
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
      report = (StatusReportImpl) reports.get(rootName);
      if (report.expirationDate < System.currentTimeMillis()){
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
    String[] reportNames=null;
    synchronized(reports) {
        Set entries = reports.keySet();
        reportNames = new String[entries.size()];
        int c=0;
        for(Iterator i = entries.iterator();i.hasNext();){
          reportNames[c++] = (String)i.next();
        }

    }
     return reportNames;
  }



  /**
   * expireReports
   */
  private void expireReports() {
    synchronized(reports){
      long time = System.currentTimeMillis();
      for(Iterator i = reports.values().iterator();i.hasNext();){
        StatusReportImpl sr = (StatusReportImpl)i.next();
        if (sr.expirationDate<time){
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
    ((StatusReportImpl)report).expirationDate =
        System.currentTimeMillis()+reportLife;
    ByteBuffer buff = ByteBuffer.allocate(report.reportSize());
    report.writeReport(buff);
    chan.sendData(buff);
  }

  /**
   * channelClosed
   */
  public void channelClosed() {
    System.out.println("ERROR! Report channel has been closed!");
  }

  /**
   * dataArrived
   *
   * @param buff ByteBuffer
   */
  public void dataArrived(ByteBuffer buff) {
    StatusReportImpl report = new StatusReportImpl(buff);
    reports.put(report.rootName(),report);
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
