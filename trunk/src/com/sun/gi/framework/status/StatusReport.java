package com.sun.gi.framework.status;

import java.nio.ByteBuffer;
import java.io.PrintStream;

public interface StatusReport {
  public void setParameter(String blockPath, String paramName, String value);
  public String[] listSubBlocks(String parent);
  public String[] listBlockParameters(String block);
  public String[] listAllParameters();
  public String getParameter(String blockPath, String paramName);
  public int reportSize();
  public void writeReport(ByteBuffer buff);
  public String rootName();
  public void dump(PrintStream outstrm);
}
