/*
 * liftup
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.liftup

/**
 * Holds plugin configuration specific to particular eclipse version.
 * @author akhikhl
 */
class EclipseVersionConfig {

  String mavenGroup

  /**
   * key - module name
   * value - list of module configurations
   */
  Map<String, List<EclipseModuleConfig>> moduleConfigs = [:]

  def methodMissing(String moduleName, args) {
    if(moduleConfigs[moduleName] == null)
      moduleConfigs[moduleName] = []
    args.each {
      if(it instanceof Closure)
        moduleConfigs[moduleName].add(new EclipseModuleConfig(common: it))
      else if (it instanceof Map)
        moduleConfigs[moduleName].add(new EclipseModuleConfig(common: it.common, platformSpecific: it.platformSpecific, platformAndLanguageSpecific: it.platformAndLanguageSpecific))
    }
  }
}

