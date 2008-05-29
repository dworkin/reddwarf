////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code for adherence to a set of rules.
// Copyright (C) 2001-2005  Oliver Burn
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
////////////////////////////////////////////////////////////////////////////////

package com.sun.sgs.tools.checkstyle;

import com.puppycrawl.tools.checkstyle.api.MessageDispatcher;
import com.puppycrawl.tools.checkstyle.checks.javadoc.PackageHtmlCheck;
import java.io.File;
import java.util.Iterator;
import java.util.Set;

/**
 * Checks that all packages have a package documentation.  This check is
 * similar to <code>PackageHtmlCheck</code>, but it checks for both
 * <code>package.html</code> and the new <code>package-info.java</code>
 * introduced in Java 5.  -tjb@sun.com (03/16/2007)
 */
public class PackageDocCheck extends PackageHtmlCheck {

    /** Creates an instance of this class. */
    public PackageDocCheck() {
        /*
	 * Every Java file should have a package.html or package-info.java
	 * sibling
	 */
        setFileExtensions(new String[] { "java" });
    }

    /**
     * Checks that each Java file in the fileset has a package.html or
     * package-info.java sibling and fires errors for the missing files.
     *
     * @param files a set of files
     */
    public void process(File[] aFiles) {
        final File[] javaFiles = filter(aFiles);
        final Set directories = getParentDirs(javaFiles);
        for (final Iterator it = directories.iterator(); it.hasNext(); ) {
	    final File dir = (File) it.next();
	    final File packageInfoJava = new File(dir, "package-info.java");
            final File packageHtml = new File(dir, "package.html");
            final MessageDispatcher dispatcher = getMessageDispatcher();
            final String path = packageInfoJava.getPath();
            dispatcher.fireFileStarted(path);
            if (!packageHtml.exists() && !packageInfoJava.exists()) {
                log(0, "javadoc.packageInfo");
                fireErrors(path);
            }
            dispatcher.fireFileFinished(path);
        }
    }
}
