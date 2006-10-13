package com.sun.sgs.impl.service.data;

import java.math.BigInteger;
import java.util.Properties;

public final class Util {
    private Util() {
	throw new AssertionError();
    }

    public static String toHexString(long l) {
	return BigInteger.valueOf(l).toString(16);
    }

    public static int getIntProperty(
	Properties properties, String name, int defaultValue)
    {
	String value = properties.getProperty(name);
	return value == null ? defaultValue : Integer.parseInt(value);
    }

    public static long getLongProperty(
	Properties properties, String name, long defaultValue)
    {
	String value = properties.getProperty(name);
	return value == null ? defaultValue : Long.parseLong(value);
    }

}	
