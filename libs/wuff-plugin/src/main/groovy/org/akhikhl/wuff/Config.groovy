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

  WrappedLibsConfig wrappedLibsConfig = new WrappedLibsConfig()

  void eclipseVersion(String versionString, Closure closure) {
    if(versionConfigs[versionString] == null)
      versionConfigs[versionString] = new EclipseVersionConfig()
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure.delegate = versionConfigs[versionString]
    closure()
  }

  void wrappedLibs(Closure closure) {
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure.delegate = wrappedLibsConfig
    closure()
  }
}

