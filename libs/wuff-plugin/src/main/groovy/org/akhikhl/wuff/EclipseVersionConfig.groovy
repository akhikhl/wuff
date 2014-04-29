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
   * names of other EclipseVersionConfig's, from which this one extends
   */
  List<String> baseVersions = []

  /**
   * module configurations of this eclipse version.
   * Key is module name, value is module configuration.
   */
  Map<String, List<Closure>> lazyModules = [:]

  void extendsFrom(String baseVersion) {
    baseVersions.add(baseVersion)
  }

  List<String> getModuleNames() {
    lazyModules.keySet() as List
  }

  def methodMissing(String moduleName, args) {
    List<Closure> closureList = lazyModules[moduleName]
    if(closureList == null)
      closureList = lazyModules[moduleName] = []
    args.each { arg ->
      if(!(arg instanceof Closure))
        throw new RuntimeException("Argument to ${moduleName} is expected to be a closure")
      closureList.add(arg)
    }
  }
}

