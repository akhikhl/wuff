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
  List<Closure> preConfigure = []
  List<Closure> configure = []
  List<Closure> platformSpecific = []
  List<Closure> platformAndLanguageSpecific = []
}

