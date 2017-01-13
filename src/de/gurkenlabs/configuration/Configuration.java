package de.gurkenlabs.configuration;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import de.gurkenlabs.annotation.ConfigurationGroupInfo;
import de.gurkenlabs.util.io.FileUtilities;

public class Configuration {
  /** The Constant CONFIGURATION_FILE_NAME. */
  private static final String DEFAULT_CONFIGURATION_FILE_NAME = "config.properties";

  private final List<ConfigurationGroup> configurationGroups;

  private final String fileName;

  public Configuration(final ConfigurationGroup... configurationGroups) {
    this(DEFAULT_CONFIGURATION_FILE_NAME, configurationGroups);
  }

  public Configuration(final String fileName, final ConfigurationGroup... configurationGroups) {
    this.fileName = fileName;
    this.configurationGroups = new ArrayList<>();
    if (configurationGroups != null && configurationGroups.length > 0) {
      for (final ConfigurationGroup group : configurationGroups) {
        this.configurationGroups.add(group);
      }
    }
  }

  public void load() {
    this.loadFromFile();
  }

  @SuppressWarnings("unchecked")
  public <T extends ConfigurationGroup> T getConfigurationGroup(final Class<T> groupClass) {
    for (final ConfigurationGroup group : this.getConfigurationGroups()) {
      if (group.getClass().equals(groupClass)) {
        return (T) group;
      }
    }

    return null;
  }

  @SuppressWarnings("unchecked")
  public <T extends ConfigurationGroup> T getConfigurationGroup(String prefix) {
    for (final ConfigurationGroup group : this.getConfigurationGroups()) {

      ConfigurationGroupInfo info = group.getClass().getAnnotation(ConfigurationGroupInfo.class);
      if (info == null) {
        continue;
      }

      if (info.prefix().equals(prefix)) {
        return (T) group;
      }
    }

    return null;
  }

  public List<ConfigurationGroup> getConfigurationGroups() {
    return this.configurationGroups;
  }

  public String getFileName() {
    return this.fileName;
  }

  public void save() {
    final File settingsFile = new File(this.getFileName());
    try {
      final OutputStream out = new FileOutputStream(settingsFile, false);
      for (final ConfigurationGroup group : this.getConfigurationGroups()) {
        storeConfigurationGroup(out, group);
      }
      out.close();
      System.out.println("Configuration " + this.getFileName() + " saved");
    } catch (final IOException e) {
      e.printStackTrace();
    }
  }

  private static void storeConfigurationGroup(final OutputStream out, final ConfigurationGroup group) {
    try {
      final Properties groupProperties = new Properties();
      group.storeProperties(groupProperties);
      groupProperties.store(out, group.getPrefix() + "SETTINGS");
      out.flush();
    } catch (final IOException e) {
      e.printStackTrace();
    }
  }

  private void initializeSettingsByProperties(final Properties properties) {
    for (final String key : properties.stringPropertyNames()) {
      for (final ConfigurationGroup group : this.getConfigurationGroups()) {
        if (key.startsWith(group.getPrefix())) {
          group.initializeByProperty(key, properties.getProperty(key));
        }
      }
    }
  }

  private void createDefaultSettingsFile(final OutputStream out) {
    for (final ConfigurationGroup group : this.getConfigurationGroups()) {
      storeConfigurationGroup(out, group);
    }
  }

  /**
   * Tries to load configuration from file in the application folder. If none
   * exists, it tires to load the file from any resource folder. If none exists,
   * it creates a new configuration file in the application folder.
   */
  private void loadFromFile() {
    final File settingsFile = new File(this.getFileName());
    InputStream settingsStream = FileUtilities.getGameResource(this.getFileName());
    if (!settingsFile.exists() && settingsStream == null) {
      if (!settingsFile.exists() || !settingsFile.isFile()) {
        try {
          final OutputStream out = new FileOutputStream(settingsFile);
          this.createDefaultSettingsFile(out);
          out.close();
        } catch (final IOException e) {
          e.printStackTrace();
        }

        System.out.printf("Default configuration %s created \n", this.getFileName());
        return;
      }
    }

    if (settingsFile.exists()) {
      try {
        settingsStream = new FileInputStream(settingsFile);
      } catch (final FileNotFoundException e) {
        e.printStackTrace();
      }
    }

    final Properties properties = new Properties();
    BufferedInputStream stream;
    try {
      stream = new BufferedInputStream(settingsStream);
      properties.load(stream);
      stream.close();
    } catch (final FileNotFoundException e1) {
      e1.printStackTrace();
    } catch (final IOException e) {
      e.printStackTrace();
    }

    this.initializeSettingsByProperties(properties);
    System.out.printf("Configuration %s loaded \n", this.getFileName());
  }
}
