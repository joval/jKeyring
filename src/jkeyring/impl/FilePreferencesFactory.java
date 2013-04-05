package jkeystore.impl;
 
import java.io.File;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;
import java.util.prefs.BackingStoreException;
 
/**
 * PreferencesFactory implementation that stores the preferences in a user-defined file. To use it,
 * set the system property <tt>java.util.prefs.PreferencesFactory</tt> to
 * <tt>net.infotrek.util.prefs.FilePreferencesFactory</tt>
 * <p/>
 * The file defaults to [user.home]/.fileprefs, but may be overridden with the system property
 * <tt>net.infotrek.util.prefs.FilePreferencesFactory.file</tt>
 *
 * @author David Croft (<a href="http://www.davidc.net">www.davidc.net</a>)
 * @version $Id: FilePreferencesFactory.java 282 2009-06-18 17:05:18Z david $
 */
public class FilePreferencesFactory implements PreferencesFactory
{
  private static final Logger log = Logger.getLogger(FilePreferencesFactory.class.getName());
 
  Preferences rootPreferences;
  public static final String SYSTEM_PROPERTY_FILE =
    "net.infotrek.util.prefs.FilePreferencesFactory.file";
 
  public Preferences systemRoot()
  {
    return userRoot();
  }
 
  public Preferences userRoot()
  {
    if (rootPreferences == null) {
      log.finer("Instantiating root preferences");
 
      rootPreferences = new FilePreferences(null, "");
    }
    return rootPreferences;
  }
 
  private static File preferencesFile;
 
  public static File getPreferencesFile()
  {
    if (preferencesFile == null) {
      String prefsFile = System.getProperty(SYSTEM_PROPERTY_FILE);
      if (prefsFile == null || prefsFile.length() == 0) {
        prefsFile = System.getProperty("user.home") + File.separator + ".fileprefs";
      }
      preferencesFile = new File(prefsFile).getAbsoluteFile();
      log.finer("Preferences file is " + preferencesFile);
    }
    return preferencesFile;
  }
 
  public static void main(String[] args) throws BackingStoreException
  {
    System.setProperty("java.util.prefs.PreferencesFactory", FilePreferencesFactory.class.getName());
    System.setProperty(SYSTEM_PROPERTY_FILE, "myprefs.txt");
 
    Preferences p = Preferences.userNodeForPackage(my.class);
 
    for (String s : p.keys()) {
      System.out.println("p[" + s + "]=" + p.get(s, null));
    }
 
    p.putBoolean("hi", true);
    p.put("Number", String.valueOf(System.currentTimeMillis()));
  }
}

package net.infotrek.util.prefs;
 
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileOutputStream;
 
/**
 * Preferences implementation that stores to a user-defined file. See FilePreferencesFactory.
 *
 * @author David Croft (<a href="http://www.davidc.net">www.davidc.net</a>)
 * @version $Id: FilePreferences.java 283 2009-06-18 17:06:58Z david $
 */
public class FilePreferences extends AbstractPreferences
{
  private static final Logger log = Logger.getLogger(FilePreferences.class.getName());
 
  private Map<String, String> root;
  private Map<String, FilePreferences> children;
  private boolean isRemoved = false;
 
  public FilePreferences(AbstractPreferences parent, String name)
  {
    super(parent, name);
 
    log.finest("Instantiating node " + name);
 
    root = new TreeMap<String, String>();
    children = new TreeMap<String, FilePreferences>();
 
    try {
      sync();
    }
    catch (BackingStoreException e) {
      log.log(Level.SEVERE, "Unable to sync on creation of node " + name, e);
    }
  }
 
  protected void putSpi(String key, String value)
  {
    root.put(key, value);
    try {
      flush();
    }
    catch (BackingStoreException e) {
      log.log(Level.SEVERE, "Unable to flush after putting " + key, e);
    }
  }
 
  protected String getSpi(String key)
  {
    return root.get(key);
  }
 
  protected void removeSpi(String key)
  {
    root.remove(key);
    try {
      flush();
    }
    catch (BackingStoreException e) {
      log.log(Level.SEVERE, "Unable to flush after removing " + key, e);
    }
  }
 
  protected void removeNodeSpi() throws BackingStoreException
  {
    isRemoved = true;
    flush();
  }
 
  protected String[] keysSpi() throws BackingStoreException
  {
    return root.keySet().toArray(new String[root.keySet().size()]);
  }
 
  protected String[] childrenNamesSpi() throws BackingStoreException
  {
    return children.keySet().toArray(new String[children.keySet().size()]);
  }
 
  protected FilePreferences childSpi(String name)
  {
    FilePreferences child = children.get(name);
    if (child == null || child.isRemoved()) {
      child = new FilePreferences(this, name);
      children.put(name, child);
    }
    return child;
  }
 
 
  protected void syncSpi() throws BackingStoreException
  {
    if (isRemoved()) return;
 
    final File file = FilePreferencesFactory.getPreferencesFile();
 
    if (!file.exists()) return;
 
    synchronized (file) {
      Properties p = new Properties();
      try {
        p.load(new FileInputStream(file));
 
        StringBuilder sb = new StringBuilder();
        getPath(sb);
        String path = sb.toString();
 
        final Enumeration<?> pnen = p.propertyNames();
        while (pnen.hasMoreElements()) {
          String propKey = (String) pnen.nextElement();
          if (propKey.startsWith(path)) {
            String subKey = propKey.substring(path.length());
            // Only load immediate descendants
            if (subKey.indexOf('.') == -1) {
              root.put(subKey, p.getProperty(propKey));
            }
          }
        }
      }
      catch (IOException e) {
        throw new BackingStoreException(e);
      }
    }
  }
 
  private void getPath(StringBuilder sb)
  {
    final FilePreferences parent = (FilePreferences) parent();
    if (parent == null) return;
 
    parent.getPath(sb);
    sb.append(name()).append('.');
  }
 
  protected void flushSpi() throws BackingStoreException
  {
    final File file = FilePreferencesFactory.getPreferencesFile();
 
    synchronized (file) {
      Properties p = new Properties();
      try {
 
        StringBuilder sb = new StringBuilder();
        getPath(sb);
        String path = sb.toString();
 
        if (file.exists()) {
          p.load(new FileInputStream(file));
 
          List<String> toRemove = new ArrayList<String>();
 
          // Make a list of all direct children of this node to be removed
          final Enumeration<?> pnen = p.propertyNames();
          while (pnen.hasMoreElements()) {
            String propKey = (String) pnen.nextElement();
            if (propKey.startsWith(path)) {
              String subKey = propKey.substring(path.length());
              // Only do immediate descendants
              if (subKey.indexOf('.') == -1) {
                toRemove.add(propKey);
              }
            }
          }
 
          // Remove them now that the enumeration is done with
          for (String propKey : toRemove) {
            p.remove(propKey);
          }
        }
 
        // If this node hasn't been removed, add back in any values
        if (!isRemoved) {
          for (String s : root.keySet()) {
            p.setProperty(path + s, root.get(s));
          }
        }
 
        p.store(new FileOutputStream(file), "FilePreferences");
      }
      catch (IOException e) {
        throw new BackingStoreException(e);
      }
    }
  }
}
