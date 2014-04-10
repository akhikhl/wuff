/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

/**
 *
 * @author akhikhl
 */
class WrappedLibsConfig {

  Map<String, WrappedLibConfig> libConfigs = [:]

  def methodMissing(String libName, args) {
    WrappedLibConfig libConfig = libConfigs[libName]
    if(libConfig == null)
      libConfig = libConfigs[libName] = new WrappedLibConfig()
    args.each { arg ->
      if(!(arg instanceof Closure))
        throw new RuntimeException("Argument to ${libName} is expected to be a closure")
      if(arg instanceof Closure) {
        arg.resolveStrategy = Closure.DELEGATE_FIRST
        arg.delegate = libConfig
        arg()
      }
    }
  }
}
