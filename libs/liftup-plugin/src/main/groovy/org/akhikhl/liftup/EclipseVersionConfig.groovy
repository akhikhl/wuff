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

  /**
   * maven group containing artifacts of this eclipse version.
   */
  String eclipseGroup

  /**
   * module configurations of this eclipse version.
   * Key is module name, value is module configuration.
   */
  Map<String, EclipseModuleConfig> moduleConfigs = [:]

  def methodMissing(String moduleName, args) {
    if(moduleConfigs[moduleName] == null)
      moduleConfigs[moduleName] = new EclipseModuleConfig()
    args.each { arg ->
      if(arg instanceof Closure)
        moduleConfigs[moduleName].configure.add(arg)
      else if (arg instanceof Map)
        ['preConfigure', 'configure', 'platformSpecific', 'platformAndLanguageSpecific'].each { key ->
          if(arg[key] instanceof Closure)
            moduleConfigs[moduleName][key].add(arg[key])
        }
    }
  }
}

