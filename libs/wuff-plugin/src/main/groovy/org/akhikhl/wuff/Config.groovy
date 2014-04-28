/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Holds plugin configuration.
 * @author akhikhl
 */
class Config {

  protected static final Logger log = LoggerFactory.getLogger(Config)

  private static void merge(Config target, Config source) {
    if(source.parentConfig)
      merge(target, source.parentConfig)
    if(source.defaultEclipseVersion != null)
      target.defaultEclipseVersion = source.defaultEclipseVersion
    source.lazyVersions.each { String versionString, List<Closure> sourceClosureList ->
      List<Closure> targetClosureList = target.lazyVersions[versionString]
      if(targetClosureList == null)
        targetClosureList = target.lazyVersions[versionString] = []
      targetClosureList.addAll(sourceClosureList)
    }
    target.lazyWrappedLibs.addAll(source.lazyWrappedLibs)
  }

  Config parentConfig

  String defaultEclipseVersion = null

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

  Config getEffectiveConfig() {
    Config result = new Config()
    merge(result, this)
    return result
  }

  private void importModulesFromBaseConfigs(EclipseVersionConfig versionConfig) {
    def importModules
    importModules = { List<String> baseVersions ->
      for(String baseVersion in baseVersions) {
        EclipseVersionConfig baseVersionConfig = versionConfigs[baseVersion]
        if(baseVersionConfig == null)
          log.error 'base eclipse version {} is not defined', baseVersion
        else {
          baseVersionConfig.moduleConfigs.each { String moduleName, EclipseModuleConfig sourceModuleConfig ->
            EclipseModuleConfig targetModuleConfig = versionConfig.moduleConfigs[moduleName]
            if(targetModuleConfig == null)
              targetModuleConfig = versionConfig.moduleConfigs[moduleName] = new EclipseModuleConfig()
            for(Closure c in sourceModuleConfig.configure)
              targetModuleConfig.configure.add(c.rehydrate(c.delegate, c.owner, c.thisObject))
            for(Closure c in sourceModuleConfig.postConfigure)
              targetModuleConfig.postConfigure.add(c.rehydrate(c.delegate, c.owner, c.thisObject))
          }
          importModules(baseVersionConfig.baseVersions)
        }
      }
    }
    importModules(versionConfig, versionConfig.baseVersions)
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
      versionConfigs.each { String versionString, EclipseVersionConfig versionConfig ->
        importModulesFromBaseConfigs(versionConfig)
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

  void wrappedLibs(Closure closure) {
    wrappedLibs = null
    lazyWrappedLibs.add(closure)
  }
}

