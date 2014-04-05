/*
 * liftup
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.liftup

/**
 * Holds plugin configuration specific to particular eclipse module.
 * @author akhikhl
 */
class EclipseModuleConfig {
  def common
  def platformSpecific
  def platformAndLanguageSpecific
}

