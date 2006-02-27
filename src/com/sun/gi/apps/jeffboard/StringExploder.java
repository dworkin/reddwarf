/**
 *
 * <p>Title: StringExploder.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.apps.jeffboard;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * <p>
 * Title: StringExploder.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class StringExploder {
	private StringExploder() {
		// cannot be initialized
	}

	static final Pattern splitPattern = 
		Pattern.compile("(?:\"([^\"]*+)\")|(\\S+)");

	public static String[] explode(String text) {
		Matcher m = splitPattern.matcher(text);
		List<String> list = new ArrayList<String>();
		while (m.find()){	
			String tok = m.group(1);
			if (tok == null){
				tok = m.group(2);
			}
			list.add(tok);
		}
		String[] out = new String[list.size()];
		return list.toArray(out);
	}

	@SuppressWarnings("deprecation")
	public static void main(String[] args) {
		String input = "";
		DataInputStream instrm = new DataInputStream(System.in);
		while (!input.equals("quit")) {
			System.out.print("String to explode: ");
			String[] output;
			try {
				output = StringExploder.explode(instrm.readLine());
				if (output == null) {
					System.err.println("No return values");
				} else {
					for (int i = 0; i < output.length; i++) {
						System.out.println(output[i]);
					}
				}
			} catch (IOException e) {

				e.printStackTrace();
			}

		}

	}
}
