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
  Map<String, EclipseModuleConfig> moduleConfigs = [:]

  def methodMissing(String moduleName, args) {
    if(moduleConfigs[moduleName] == null)
      moduleConfigs[moduleName] = new EclipseModuleConfig()
    args.each {
      if(it instanceof Closure)
        moduleConfigs[moduleName].common.add(it)
      else if (it instanceof Map) {
        if(it.common instanceof Closure)
          moduleConfigs[moduleName].common.add(it.common)
        if(it.platformSpecific instanceof Closure)
          moduleConfigs[moduleName].platformSpecific.add(it.platformSpecific)
        if(it.platformAndLanguageSpecific instanceof Closure)
          moduleConfigs[moduleName].platformAndLanguageSpecific.add(it.platformAndLanguageSpecific)
      }
    }
  }
}

