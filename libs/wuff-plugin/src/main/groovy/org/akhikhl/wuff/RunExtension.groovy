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
class RunExtension {

  def args = []
  def jvmArgs = []
  String language
  def autostartedBundles = []

  def arg(String newValue) {
    args.add newValue
  }

  def jvmArg(String newValue) {
    jvmArgs.add newValue
  }

  def jvmArg(Object[] newValue) {
      jvmArgs.addAll newValue
  }

  def args(Object[] newValue) {
    args.addAll newValue
  }

  def language(String newValue) {
    language = newValue
  }

  void autostartedBundle(String newBundle) {
    autostartedBundles.add newBundle
  }
}
