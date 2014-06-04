/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package activator.properties;

import java.util.Properties;

// This is a lame-o class that's kinda dirty.  maybe we can clean it up later, but we're using it across two scala versions right now.
public class ActivatorProperties {

  private static Properties loadProperties() {
    Properties props = new Properties();
    java.io.InputStream in = ActivatorProperties.class.getResourceAsStream("activator.properties");
    try {
      props.load(in);
    } catch(java.io.IOException e) { throw new RuntimeException(e); }
    finally { try { in.close(); } catch(java.io.IOException e) { throw new RuntimeException(e); }  }
    return props;
  }

  private static Properties props = loadProperties();

  private static String getPropertyNoOverrides(String name) {
    return props.getProperty(name);
  }

  // trying to avoid activator.activator.home here
  private static String ensureNamespacing(String name) {
    if (name.startsWith("activator."))
      return name;
    else
      return "activator." + name;
  }

  /** Checks the system properties, before the environment, before the hard coded defaults. */
  private static String getPropertyWithOverrides(String name) {
    String value = System.getProperty(name);
    if(value == null) {
      value = System.getenv(ensureNamespacing(name));
    }
    if(value == null) {
      value = System.getenv(ensureNamespacing(name).replace('.', '_').toUpperCase());
    }
    if (value == null) {
      value = getPropertyNoOverrides(name);
    }
    return value;
  }

  /** Looks up a property value, and parses its value as appropriate. */
  private static String lookupOr(String name, String defaultValue) {
    String value = getPropertyWithOverrides(name);
    if(value == null) {
      value = defaultValue;
    }
    return value;
  }

  private static String requirePropertyWithOverrides(String name) {
    String value = getPropertyWithOverrides(name);
    if (value == null)
      throw new RuntimeException("Property '" + name + "' has not been set");
    return value;
  }

  private static String requirePropertyNoOverrides(String name) {
    String value = getPropertyNoOverrides(name);
    if (value == null)
      throw new RuntimeException("Property '" + name + "' has not been set");
    return value;
  }

  public static String TEMPLATE_UUID_PROPERTY_NAME = "template.uuid";
  public static String SCRIPT_NAME = "activator";

  public static String APP_VERSION() {
    return requirePropertyNoOverrides("app.version");
  }

  public static String APP_ABI_VERSION() {
    // TODO - Encode ABI version in metadata...
    return APP_VERSION();
  }

  public static String APP_SCALA_VERSION() {
    return requirePropertyNoOverrides("app.scala.version");
  }

  public static String SBT_DEFAULT_VERSION() {
    return requirePropertyNoOverrides("sbt.default.version");
  }

  private static String cleanUriFileString(String file) {
    return file.replaceAll(" ", "%20");
  }

  private static String uriToFilename(String uri) {
    try {
      return new java.io.File(new java.net.URI(cleanUriFileString(uri))).getAbsolutePath();
    } catch(java.net.URISyntaxException ex) {
      // TODO - fix this error handling to not suck.
      throw new RuntimeException("BAD URI: " + uri);
    } catch(java.lang.IllegalArgumentException ex) {
      throw new RuntimeException("BAD URI: " + uri + "\n", ex);
    }
  }

  /** Returns the distribution home directory (or local project) as a URI string. */
  public static String ACTIVATOR_HOME_FILENAME() {
    // TODO - We should probably remove all spaces and URI-ify the string first.
    return uriToFilename("file://" + ACTIVATOR_HOME());
  }

  /** Returns the distribution home directory (or local project) as a URI string. */
  public static String ACTIVATOR_HOME() {
    return requirePropertyWithOverrides("activator.home");
  }

  public static String GLOBAL_USER_HOME() {
    return requirePropertyWithOverrides("user.home");
  }

  // If you need these directories, consider keeping them private
  // and instead just exporting a value for the final filename
  // you would construct, like ACTIVATOR_USER_CONFIG_FILE below.

  private static String ACTIVATOR_UNVERSIONED_USER_HOME() {
    return lookupOr("activator.user.home", GLOBAL_USER_HOME() + "/.activator");
  }

  private static String ACTIVATOR_VERSIONED_USER_HOME() {
    return ACTIVATOR_UNVERSIONED_USER_HOME() + "/" + APP_ABI_VERSION();
  }

  private static String ACTIVATOR_USER_CONFIG_HOME() {
    return ACTIVATOR_UNVERSIONED_USER_HOME() + "/"
        + requirePropertyWithOverrides("app.config.version");
  }

  private static String ACTIVATOR_PREVIOUS_USER_CONFIG_HOME() {
    return ACTIVATOR_UNVERSIONED_USER_HOME() + "/"
        + requirePropertyWithOverrides("app.config.previousVersion");
  }

  public static String ACTIVATOR_USER_CONFIG_FILE() {
    return ACTIVATOR_USER_CONFIG_HOME() + "/config.json";
  }

  public static String ACTIVATOR_PREVIOUS_USER_CONFIG_FILE() {
    return ACTIVATOR_PREVIOUS_USER_CONFIG_HOME() + "/config.json";
  }

  public static String ACTIVATOR_USER_REPOSITORIES_FILE() {
    return ACTIVATOR_USER_CONFIG_HOME() + "/repositories.properties";
  }

  public static String ACTIVATOR_VERSION_FILE() {
    // this filename is also constructed in the launcher config file, so keep
    // in sync with that...
    return ACTIVATOR_UNVERSIONED_USER_HOME() + "/version-" + ACTIVATOR_LAUNCHER_GENERATION()
        + ".properties";
  }

  // where to get the latest version
  public static String ACTIVATOR_LATEST_URL() {
    return lookupOr("activator.latest.url", "https://typesafe.com/activator/latest");
  }

  // We will consume latest versions only if they match this.
  // So we would bump this if we want to require people to
  // update their launcher.
  public static int ACTIVATOR_LAUNCHER_GENERATION() {
    return Integer.parseInt(requirePropertyWithOverrides("activator.launcher.generation"));
  }

  public static String ACTIVATOR_TEMPLATE_CACHE() {
    return lookupOr("activator.template.cache", ACTIVATOR_VERSIONED_USER_HOME() + "/templates");
  }

  public static String ACTIVATOR_TEMPLATE_LOCAL_REPO() {
    String defaultValue = ACTIVATOR_HOME_FILENAME();
    if(defaultValue != null) {
      defaultValue = defaultValue + "/templates";
    }
    return lookupOr("activator.template.localrepo", defaultValue);
  }

  public static String ACTIVATOR_LAUNCHER_JAR_NAME() {
    String version = APP_VERSION();
    if(version != null) {
      // TODO - synch this with build in some better fashion!
      return SCRIPT_NAME+"-launch-"+version+".jar";
    }
    return null;
  }

  public static String ACTIVATOR_LAUNCHER_JAR() {
    String value = ACTIVATOR_HOME_FILENAME();
    String jarname = ACTIVATOR_LAUNCHER_JAR_NAME();
    if(value != null && jarname != null) {
      // TODO - synch this with build in some better fashion!
      return value+"/"+jarname;
    }
    return null;
  }

  public static String ACTIVATOR_LAUNCHER_BAT() {
    String value = ACTIVATOR_HOME_FILENAME();
    if(value != null) {
      value = value+"/"+SCRIPT_NAME+".bat";
    }
    return value;
  }
  public static String ACTIVATOR_LAUNCHER_BASH() {
    String value = ACTIVATOR_HOME_FILENAME();
    if(value != null) {
      value = value+"/"+SCRIPT_NAME;
    }
    return value;
  }

  public static java.io.File ACTIVATOR_LOCK_FILE() {
    return new java.io.File(ACTIVATOR_VERSIONED_USER_HOME() + "/.lock");
  }

  public static java.io.File ACTIVATOR_PID_FILE() {
    return new java.io.File(ACTIVATOR_VERSIONED_USER_HOME() + "/.currentpid");
  }

  public static boolean ACTIVATOR_CHECK_FOR_UPDATES() {
    try {
      return Boolean.parseBoolean(lookupOr("activator.checkForUpdates", "true"));
    } catch (Exception e) {
      System.err.println("Warning: bad value for activator.checkForUpdates: " + e.getMessage());
      return true;
    }
  }

  public static boolean ACTIVATOR_PROXY_DEBUG() {
    try {
      return Boolean.parseBoolean(lookupOr("activator.proxyDebug", "false"));
    } catch(Exception e){
      System.err.println("Warning: bad value for activator.proxyDebug: " + e.getMessage());
      return false;
    }
  }
}
