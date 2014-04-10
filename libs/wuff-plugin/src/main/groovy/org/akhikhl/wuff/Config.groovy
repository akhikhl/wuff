/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

/**
 * Holds plugin configuration.
 * @author akhikhl
 */
class Config {

  String defaultEclipseVersion = null

  Map<String, EclipseVersionConfig> versionConfigs = [:]

  void eclipseVersion(String versionString, Closure versionDef) {
    if(versionConfigs[versionString] == null)
      versionConfigs[versionString] = new EclipseVersionConfig()
    versionDef.resolveStrategy = Closure.DELEGATE_FIRST
    versionDef.delegate = versionConfigs[versionString]
    versionDef()
  }
}

