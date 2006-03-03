/**
 *
 * <p>Title: StringExploder.java</p>
 * <p>Description: </p>
 
 * @author Jeff Kesselman
 * @version 1.0
 */

/*****************************************************************************
     * Copyright (c) 2006 Sun Microsystems, Inc.  All Rights Reserved.
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions are met:
     *
     * - Redistribution of source code must retain the above copyright notice,
     *   this list of conditions and the following disclaimer.
     *
     * - Redistribution in binary form must reproduce the above copyright notice,
     *   this list of conditions and the following disclaimer in the documentation
     *   and/or other materails provided with the distribution.
     *
     * Neither the name Sun Microsystems, Inc. or the names of the contributors
     * may be used to endorse or promote products derived from this software
     * without specific prior written permission.
     *
     * This software is provided "AS IS," without a warranty of any kind.
     * ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
     * ANY IMPLIED WARRANT OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
     * NON-INFRINGEMEN, ARE HEREBY EXCLUDED.  SUN MICROSYSTEMS, INC. ("SUN") AND
     * ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS
     * A RESULT OF USING, MODIFYING OR DESTRIBUTING THIS SOFTWARE OR ITS
     * DERIVATIVES.  IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
     * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
     * INCIDENTAL OR PUNITIVE DAMAGES.  HOWEVER CAUSED AND REGARDLESS OF THE THEORY
     * OF LIABILITY, ARISING OUT OF THE USE OF OUR INABILITY TO USE THIS SOFTWARE,
     * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
     *
     * You acknowledge that this software is not designed or intended for us in
     * the design, construction, operation or maintenance of any nuclear facility
     *
     *****************************************************************************/

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
