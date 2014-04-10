/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

/**
 * Holds plugin configuration specific to particular eclipse version.
 * @author akhikhl
 */
class EclipseVersionConfig {

  /**
   * maven group containing artifacts of this eclipse version.
   */
  String eclipseMavenGroup

  /**
   * module configurations of this eclipse version.
   * Key is module name, value is module configuration.
   */
  Map<String, EclipseModuleConfig> moduleConfigs = [:]

  def methodMissing(String moduleName, args) {
    EclipseModuleConfig moduleConfig = moduleConfigs[moduleName]
    if(moduleConfig == null)
      moduleConfig = moduleConfigs[moduleName] = new EclipseModuleConfig()
    args.each { arg ->
      if(!(arg instanceof Closure))
        throw new RuntimeException("Argument to ${moduleName} is expected to be a closure")
      if(arg instanceof Closure) {
        arg.resolveStrategy = Closure.DELEGATE_FIRST
        arg.delegate = moduleConfig
        arg()
      }
    }
  }
}

