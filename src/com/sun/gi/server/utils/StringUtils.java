package com.sun.gi.server.utils;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

import java.util.*;

public class StringUtils {
    public StringUtils() {
    }

     public static String[] explode(String str,String delim){
         return explode(str,delim,true);
     }

    public static String[] explode(String str,String delim, boolean trim){
        List plist = new ArrayList();
        while (str.length() >0) {
            int delimpos = str.indexOf(delim);
            String leftString = str.substring(0,delimpos);
            if (leftString.length()>0){
                if (trim) {
                    leftString = leftString.trim();
                }
                plist.add(leftString);
            }
            str = str.substring(delimpos+delim.length());
        }
        String[] retarray= new String[plist.size()];
        return (String[])plist.toArray(retarray);
    }

}