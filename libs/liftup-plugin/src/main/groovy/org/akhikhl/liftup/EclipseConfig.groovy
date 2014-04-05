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

  String defaultVersion = null

  Map<String, EclipseVersionConfig> versionConfigs = [:]

  void version(String versionString, Closure versionDef) {
    if(versionConfigs[versionString] == null)
      versionConfigs[versionString] = new EclipseVersionConfig()
    versionDef.resolveStrategy = Closure.DELEGATE_FIRST
    versionDef.delegate = versionConfigs[versionString]
    versionDef()
  }
}

