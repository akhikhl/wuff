/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

/**
 * Holds plugin configuration specific to particular eclipse module.
 * @author akhikhl
 */
class EclipseModuleConfig {

  List<Closure> configure = []
  List<Closure> postConfigure = []

  void configure(Closure closure) {
    this.configure.add(closure)
  }

  void postConfigure(Closure closure) {
    this.postConfigure.add(closure)
  }
}

