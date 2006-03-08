package com.sun.gi.framework.status.impl;

import java.nio.*;
import java.util.*;
import java.util.Map.*;

import com.sun.gi.framework.status.*;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.PrintStream;

public class StatusReportImpl
    implements StatusReport {
  ReportBlock root;
  long expirationDate;

  class ReportBlock {
    String name;
    Map parameters = new HashMap();
    List children = new ArrayList();

    public ReportBlock(String name) {
      this.name = name;
    }

    public ReportBlock() {

    }


    public void setParameter(String name, String value) {
      parameters.put(name, value);
    }

    public void addChild(ReportBlock blk) {
      children.add(blk);
    }

    public ReportBlock getChild(String name) {
      for (Iterator i = children.iterator(); i.hasNext(); ) {
        ReportBlock block = (ReportBlock) i.next();
        if (block.name.equalsIgnoreCase(name)) {
          return block;
        }
      }
      return null;
    }

    public void write(ByteBuffer buff) {
      buff.putInt(name.length());
      buff.put(name.getBytes());
      buff.putInt(parameters.size());
      for (Iterator i = parameters.entrySet().iterator(); i.hasNext(); ) {
        Entry entry = (Entry) i.next();
        String key = (String) entry.getKey();
        String value = (String) entry.getValue();
        buff.putInt(key.length());
        buff.put(key.getBytes());
        buff.putInt(value.length());
        buff.put(value.getBytes());
      }
      // do children
      buff.putInt(children.size());
      for (Iterator i = children.iterator(); i.hasNext(); ) {
        ( (ReportBlock) i.next()).write(buff);
      }
    }

    public void read(ByteBuffer buff) {
      int sz = buff.getInt();
      byte[] barray = new byte[sz];
      buff.get(barray);
      name = new String(barray);
      int pcount = buff.getInt();
      for (int i = 0; i < pcount; i++) {
        sz = buff.getInt();
        byte[] keybytes = new byte[sz];
        buff.get(keybytes);
        sz = buff.getInt();
        byte[] valuebytes = new byte[sz];
        buff.get(valuebytes);
        parameters.put(new String(keybytes), new String(valuebytes));
      }
      int childcount = buff.getInt();
      for (int i = 0; i < childcount; i++) {
        ReportBlock child = new ReportBlock();
        child.read(buff);
        children.add(child);
      }
    }

    public int getSize(){
      int sz = 0;
      sz += name.length()+4;
      sz += 4;
      for (Iterator i = parameters.entrySet().iterator(); i.hasNext(); ) {
        Entry entry = (Entry) i.next();
        String key = (String) entry.getKey();
        String value = (String) entry.getValue();
        sz += key.length()+4;
        sz += value.length()+4;
      }
      sz += 4;
      for (Iterator i = children.iterator(); i.hasNext(); ) {
        sz += ((ReportBlock) i.next()).getSize();
      }
      return sz;
    }

    /**
     * getParameter
     *
     * @param currentTok String
     * @return String
     */
    private String getParameter(String currentTok) {
      return (String) parameters.get(currentTok);
    }

    /**
     * collectAllParameters
     *
     * @param strlist List
     */
    private void collectAllParameters(List strlist, String prefix) {
      for (Iterator i = parameters.keySet().iterator(); i.hasNext(); ) {
        strlist.add(prefix + "." + (String) i.next());
      }
      for (Iterator i = children.iterator(); i.hasNext(); ) {
        ( (ReportBlock) i.next()).collectAllParameters(strlist,
            prefix + "." + name);
      }
    }

    public void dump(PrintStream outstrm, String prefix){
      prefix+="."+name;
      for(Iterator i=parameters.entrySet().iterator();i.hasNext();){
        Entry entry = (Entry)i.next();
        String key = (String)entry.getKey();
        String value = (String)entry.getValue();
        outstrm.println(prefix+"."+key+" "+value);
      }
      outstrm.flush();
      for (Iterator i = children.iterator(); i.hasNext(); ) {
        ( (ReportBlock) i.next()).dump(outstrm,prefix);
      }

    }

  }

  private StatusReportImpl(){

  }

  StatusReportImpl(String rootName) {
    this();
    root = new ReportBlock(rootName);
  }

  /**
   * StatusReportImpl
   * This is pacakge private.  It is intended to only be called by
   * ReportManagerImpl
   *
   * @param byteBuffer ByteBuffer
   */
  StatusReportImpl(ByteBuffer byteBuffer) {
    this();
    this.read(byteBuffer);
  }


  public void read(ByteBuffer buff) {
    root = new ReportBlock("");
    expirationDate = buff.getLong();
    root.read(buff);
  }

  /**
   * addParameter
   *
   * @param name String
   * @param value String
   */
  public void setParameter(String blockPath, String paramName, String value) {
    ReportBlock currentBlock = root;
    StringTokenizer tok = new StringTokenizer(blockPath, ".");
    while (tok.hasMoreTokens()) {
      String currentTok = tok.nextToken();
      ReportBlock oldBlock = currentBlock;
      currentBlock = currentBlock.getChild(currentTok);
      if (currentBlock == null) { // add block
        currentBlock = new ReportBlock(currentTok);
        oldBlock.addChild(currentBlock);
      }
    }
    currentBlock.setParameter(paramName, value);
  }

  private ReportBlock getBlock(String blockPath) {
    ReportBlock currentBlock = root;
    StringTokenizer tok = new StringTokenizer(blockPath, ".");
    while (tok.hasMoreTokens()) {
      String currentTok = tok.nextToken();
      currentBlock = currentBlock.getChild(currentTok);
      if (currentBlock == null) { // add block
        return null;
      }
    }
    return currentBlock;

  }

  /**
   * getParameter
   *
   * @param blockPath String
   * @param paramName String
   * @return String
   */
  public String getParameter(String blockPath, String paramName) {
    ReportBlock currentBlock = getBlock(blockPath);
    if (currentBlock == null) {
      return null;
    }
    return currentBlock.getParameter(paramName);
  }

  /**
   * listAllParameters
   *
   * @return String[]
   */
  public String[] listAllParameters() {
    List strlist = new ArrayList();
    root.collectAllParameters(strlist, "");
    String[] strs = new String[strlist.size()];
    strlist.toArray(strs);
    return strs;
  }

  /**
   * listBlockParameters
   *
   * @param block String
   * @return String[]
   */
  public String[] listBlockParameters(String blockPath) {
    ReportBlock currentBlock = getBlock(blockPath);
    if (currentBlock == null) {
     return null;
   }
   Set paramNames = currentBlock.parameters.keySet();
   String[] params = new String[paramNames.size()];
   paramNames.toArray(params);
   return params;
  }

  /**
   * listSubBlocks
   *
   * @param parent String
   * @return String[]
   */
  public String[] listSubBlocks(String blockPath) {
    ReportBlock currentBlock = getBlock(blockPath);
   if (currentBlock == null) {
    return null;
   }
   String[] blocknames = new String[currentBlock.children.size()];
   int c=0;
   for(Iterator i = currentBlock.children.iterator();i.hasNext();){
     blocknames[c++] = ((ReportBlock)i.next()).name;
   }
   return blocknames;
  }

  /**
   * reportSize
   *
   * @return int
   */
  public int reportSize() {
    if (root == null) {
      return 0;
    } else {
      return root.getSize()+8;
    }
  }

  /**
   * writeReport
   *
   * @param buff ByteBuffer
   */
  public void writeReport(ByteBuffer buff) {
    buff.putLong(expirationDate);
    if (root == null) {
      return;
    } else {
      root.write(buff);
    }
  }

  public String rootName(){
    if (root == null) {
      return null;
    } else {
      return root.name;
    }
  }

  public void dump(PrintStream outstrm){
    root.dump(outstrm,"");
  }
}
