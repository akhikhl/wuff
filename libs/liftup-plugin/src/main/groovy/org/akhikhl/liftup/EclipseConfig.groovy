/*
 * liftup
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.liftup

/**
 * Holds plugin configuration.
 * @author akhikhl
 */
class EclipseConfig {

  String defaultEclipseVersion = null
  
  Map<String, EclipseVersionConfig> eclipseVersionConfigs = [:]

  void eclipseVersion(String versionString, Closure versionDef) {
    if(eclipseVersionConfigs[versionString] == null)
      eclipseVersionConfigs[versionString] = new EclipseVersionConfig()
    versionDef.resolveStrategy = Closure.DELEGATE_FIRST
    versionDef.delegate = eclipseVersionConfigs[versionString]
    versionDef()
  }
}

