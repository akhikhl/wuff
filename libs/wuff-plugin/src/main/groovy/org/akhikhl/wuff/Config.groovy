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

  File localMavenRepositoryDir
  File wuffDir
  String selectedEclipseVersion = null
  Map<String, List<Closure>> lazyVersions = [:]
  private Map<String, EclipseVersionConfig> versionConfigs = null
  Set<String> languagePacks = new LinkedHashSet()
  List<Closure> lazyWrappedLibs = []
  private WrappedLibsConfig wrappedLibs = null
  boolean filterPluginXml = false
  boolean filterManifest = false
  boolean filterProperties = false
  boolean filterHtml = false
  boolean ignoreManifest = true

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

  void languagePack(String language) {
    languagePacks.add(language)
  }

  protected static void merge(Config target, Config source) {
    if(source.parentConfig)
      merge(target, source.parentConfig)
    if(source.localMavenRepositoryDir != null)
      target.localMavenRepositoryDir = source.localMavenRepositoryDir
    if(source.wuffDir != null)
      target.wuffDir = source.wuffDir
    if(source.selectedEclipseVersion != null)
      target.selectedEclipseVersion = source.selectedEclipseVersion
    source.lazyVersions.each { String versionString, List<Closure> sourceClosureList ->
      List<Closure> targetClosureList = target.lazyVersions[versionString]
      if(targetClosureList == null)
        targetClosureList = target.lazyVersions[versionString] = []
      targetClosureList.addAll(sourceClosureList)
    }
    target.lazyWrappedLibs.addAll(source.lazyWrappedLibs)
    if(source.filterPluginXml)
      target.filterPluginXml = true
    if(source.filterManifest)
      target.filterManifest = true
    if(source.filterProperties)
      target.filterProperties = true
    if(source.filterHtml)
      target.filterHtml = true
    if(source.ignoreManifest)
      target.ignoreManifest = true
  }
  
  boolean supportsE4() {
    assert selectedEclipseVersion != null
    (selectedEclipseVersion.split('\\.')[0] as int) >= 4
  }

  void wrappedLibs(Closure closure) {
    wrappedLibs = null
    lazyWrappedLibs.add(closure)
  }
}

