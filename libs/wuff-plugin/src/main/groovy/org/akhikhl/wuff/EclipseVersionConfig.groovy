/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

/**
 * Holds plugin configuration specific to particular eclipse version.
 * @author akhikhl
 */
class EclipseVersionConfig {

  String eclipseMavenGroup
  String eclipseMirror
  String eclipseArchiveMirror
  List<String> baseVersions = []
  Map<String, List<Closure>> lazyModules = [:]
  List<Closure> lazySources = []

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

  void sources(Closure closure) {
    lazySources.add(closure)
  }
}

