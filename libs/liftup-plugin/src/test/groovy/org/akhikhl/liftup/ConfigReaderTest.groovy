/*
 * liftup
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.liftup

import spock.lang.Specification

/**
 *
 * @author akhikhl
 */
class ConfigReaderTest extends Specification {

  ConfigReader reader

  def setup() {
    reader = new ConfigReader()
  }

  def 'should read empty configuration'() {
  when:
    Config config = reader.readFromResource('emptyConfig.groovy')
  then:
    config.defaultEclipseVersion == null
    config.eclipseVersionConfigs.isEmpty()
  }

  def 'should read default eclipse version'() {
  when:
    Config config = reader.readFromResource('defaultEclipseVersionConfig.groovy')
  then:
    config.defaultEclipseVersion == '4.3'
    config.eclipseVersionConfigs.isEmpty()
  }

  def 'should read eclipse versions'() {
  when:
    Config config = reader.readFromResource('eclipseVersions.groovy')
  then:
    config.eclipseVersionConfigs.size() == 3
    config.eclipseVersionConfigs.containsKey('3.7')
    config.eclipseVersionConfigs.containsKey('4.2')
    config.eclipseVersionConfigs.containsKey('4.3')
  }
}

