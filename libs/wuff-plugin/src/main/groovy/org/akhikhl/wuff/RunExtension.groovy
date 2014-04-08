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
 * @author ahi
 */
class RunExtension {

  def args = []
  String language

  def arg(String newValue) {
    args.add newValue
  }

  def args(Object[] newValue) {
    args.addAll newValue
  }

  def language(String newValue) {
    language = newValue
  }
}
