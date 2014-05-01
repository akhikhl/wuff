/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project

/**
 * Holds plugin configuration.
 * @author akhikhl
 */
class Config {

  Config parentConfig

  String selectedEclipseVersion = null

  Map<String, List<Closure>> lazyVersions = [:]
  private Map<String, EclipseVersionConfig> versionConfigs = null
  List<Closure> lazyWrappedLibs = []
  private WrappedLibsConfig wrappedLibs = null
  boolean filterPluginXml = false
  boolean filterManifest = false

  void eclipseVersion(String versionString, Closure closure) {
    List<Closure> closureList = lazyVersions[versionString]
    if(closureList == null)
      closureList = lazyVersions[versionString] = []
    closureList.add(closure)
    versionConfigs = null
  }

  EclipseVersionConfig getSelectedVersionConfig() {
    getVersionConfigs()[selectedEclipseVersion]
  }

  Map<String, EclipseVersionConfig> getVersionConfigs() {
    if(versionConfigs == null) {
      versionConfigs = [:]
      lazyVersions.each { String versionString, List<Closure> closureList ->
        def versionConfig = versionConfigs[versionString] = new EclipseVersionConfig()
        for(Closure closure in closureList) {
          closure = closure.rehydrate(versionConfig, closure.owner, closure.thisObject)
          closure.resolveStrategy = Closure.DELEGATE_FIRST
          closure()
        }
      }
    }
    return versionConfigs
  }

  WrappedLibsConfig getWrappedLibs() {
    if(wrappedLibs == null) {
      wrappedLibs = new WrappedLibsConfig()
      for(Closure closure in lazyWrappedLibs) {
        closure = closure.rehydrate(wrappedLibs, closure.owner, closure.thisObject)
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure()
      }
    }
    return wrappedLibs
  }

  protected static void merge(Config target, Config source) {
    if(source.parentConfig)
      merge(target, source.parentConfig)
    if(source.selectedEclipseVersion != null)
      target.selectedEclipseVersion = source.selectedEclipseVersion
    source.lazyVersions.each { String versionString, List<Closure> sourceClosureList ->
      List<Closure> targetClosureList = target.lazyVersions[versionString]
      if(targetClosureList == null)
        targetClosureList = target.lazyVersions[versionString] = []
      targetClosureList.addAll(sourceClosureList)
    }
    target.lazyWrappedLibs.addAll(source.lazyWrappedLibs)
  }

  void wrappedLibs(Closure closure) {
    wrappedLibs = null
    lazyWrappedLibs.add(closure)
  }
}

