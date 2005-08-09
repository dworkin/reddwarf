package com.sun.gi.framework.status;

import java.io.*;

public interface ReportManager {
  public StatusReport makeNewReport(String rootName);
  public void sendReport(StatusReport report) throws IOException;
  public StatusReport getReport(String rootName);
  public String[] listReports();
  public long getReportTTL();

}
