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
 *
 * Portions Copyrighted 2010 Sun Microsystems, Inc.
 */

package jkeyring.impl.kde;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.MissingResourceException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jkeyring.intf.IKeyring;

/**
 *
 * @author psychollek, ynov
 */
public class KWalletProvider implements IKeyring {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String appName = "jKeyring";
    private static final String defaultLocalWallet = "kdewallet";

    private String handler = "0";
    private boolean timeoutHappened = false;

    @Override
    public boolean enabled() {
        CommandResult result = runCommand("isEnabled");
        if(new String(result.retVal, UTF8).equals("true")) {        
            return updateHandler();
        }                   
        return false;
    }

    @Override
    public byte[] read(String key) {
        if (updateHandler()){
            CommandResult result = runCommand("readPassword", handler, appName, key, appName);
            if (result.exitCode != 0){
                warning("read action returned not 0 exitCode");
            }
            return result.retVal.length > 0 ? result.retVal : null;
        }
        return null;
        //throw new KwalletException("read");
    }

    @Override
    public void save(String key, byte[] data, String description) {
        //description is forgoten ! kdewallet dosen't have any facility to store
        //it by default and I don't want to do it by adding new fields to kwallet
        if (updateHandler()) {
            CommandResult result = runCommand("writePassword", handler , appName, key, new String(data, UTF8), appName);
            if (result.exitCode != 0 || (new String(result.retVal, UTF8)).equals("-1")){
                warning("save action failed");
            }
            return;
        }
        //throw new KwalletException("save");
    }

    @Override
    public void delete(String key) {
        if (updateHandler()) {
            CommandResult result = runCommand("removeEntry", handler, appName, key, appName);
            if (result.exitCode != 0  || (new String(result.retVal, UTF8)).equals("-1")) {
                warning("delete action failed");
            }
            return;
        }
        //throw new KwalletException("delete");
    }

    private boolean updateHandler() {
        if(timeoutHappened) {
            return false;
        }
        handler = handler.equals("") ? "0" : handler;
        CommandResult result = runCommand("isOpen", handler);
        if(new String(result.retVal, UTF8).equals("true")) {
            return true;
        }
        String localWallet = defaultLocalWallet;
        result = runCommand("localWallet");                      
        if (result.exitCode == 0) {                    
            localWallet = new String(result.retVal, UTF8);
        }
            
        if (localWallet.contains(".service")) {
            //Temporary workaround for the bug in kdelibs/kdeui/util/kwallet.cpp
            //The bug was fixed http://svn.reviewboard.kde.org/r/5885/diff/
            //but many people currently use buggy kwallet
            return false;
        }
        result = runCommand("open", localWallet, "0", appName);
        if (result.exitCode == 2) { 
            warning("time out happened while accessing KWallet");
            //don't try to open KWallet anymore until bug https://bugs.kde.org/show_bug.cgi?id=259229 is fixed
            timeoutHappened = true;
            return false;
        }      
        if(result.exitCode != 0 || new String(result.retVal, UTF8).equals("-1")) {
            warning("failed to access KWallet");
            return false;
        }         
        handler = new String(result.retVal, UTF8);
        return true;
    }

    private CommandResult runCommand(String command, String... commandArgs) {
        String[] argv = new String[commandArgs.length+4];
        argv[0] = "qdbus";
        argv[1] = "org.kde.kwalletd";
        argv[2] = "/modules/kwalletd";
        argv[3] = "org.kde.KWallet."+command;
        for (int i = 0; i < commandArgs.length; i++) {
            //unfortunatelly I cannot pass char[] to the exec in any way - so this poses a security issue with passwords in String() !
            //TODO: find a way to avoid changing char[] into String
            argv[i+4] = commandArgs[i];
        }
        Runtime rt = Runtime.getRuntime();
        String retVal = "";
        String errVal = "";
        int exitCode = 0;
        try {
            Process pr = rt.exec(argv);
            
            BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));

            String line;
            while((line = input.readLine()) != null) {
                if (!retVal.equals("")){
                    retVal = retVal.concat("\n");
                }
                retVal = retVal.concat(line);
            }            
            input.close();
            input = new BufferedReader(new InputStreamReader(pr.getErrorStream()));

            while((line = input.readLine()) != null) {
                if (!errVal.equals("")){
                    errVal = errVal.concat("\n");
                }
                errVal = errVal.concat(line);
            }
            input.close();

            exitCode = pr.waitFor();
        } catch (InterruptedException ex) {
	    ex.printStackTrace();
        } catch (IOException ex) {
	    ex.printStackTrace();
        }
        return new CommandResult(exitCode, retVal.trim().getBytes(UTF8), errVal.trim());
    }

    private void warning(String descr) {
        System.err.println("Something went wrong: " + descr);
    }      
  
    private class CommandResult {
        private int exitCode;
        private byte[] retVal;
        private String errVal;

        public CommandResult(int exitCode, byte[] retVal, String errVal) {
            this.exitCode = exitCode;
            this.retVal = retVal;
            this.errVal = errVal;
        }                        
    }
}
