/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 * jOVAL.org elects to include this software in this distribution
 * under the CDDL license.
 *
 * Portions Copyrighted 2009 Sun Microsystems, Inc.
 */

package jkeyring;

import jkeyring.intf.IKeyring;
import jkeyring.impl.crypto.CryptoProvider;
import jkeyring.impl.crypto.MasterPasswordEncryption;
import jkeyring.impl.gnome.GnomeProvider;
import jkeyring.impl.kde.KWalletProvider;
import jkeyring.impl.mac.MacProvider;
import jkeyring.impl.win32.DPAPIEncryption;

/**
 * The factory class for obtaining the system keyring.
 */
public class KeyringFactory {
    private static final IKeyring INSTANCE;
    static {
	String osName = System.getProperty("os.name");
	boolean windows = osName.toLowerCase().indexOf("windows") != -1;
	boolean mac = osName.toLowerCase().indexOf("darwin") != -1;
	boolean linux = osName.toLowerCase().indexOf("linux") != -1;

	if (windows) {
	    INSTANCE = new CryptoProvider(new DPAPIEncryption());
	} else if (mac) {
	    INSTANCE = new MacProvider();
	} else if (linux) {
	    IKeyring gk = new GnomeProvider();
	    if (gk.enabled()) {
		INSTANCE = gk;
	    } else {
		IKeyring kk = new KWalletProvider();
		if (kk.enabled()) {
		    INSTANCE = kk;
		} else {
		    INSTANCE = new CryptoProvider(new MasterPasswordEncryption());
		}
	    }
	} else {
	    INSTANCE = new CryptoProvider(new MasterPasswordEncryption());
	}
    }

    /**
     * Return the singleton IKeyring instance.
     */
    public static final IKeyring getKeyring() {
	return INSTANCE;
    }
}
