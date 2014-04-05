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

  def 'should read eclipse versions configuration'() {
  when:
    Config config = reader.readFromResource('eclipseVersionsConfig.groovy')
  then:
    config.eclipseVersionConfigs.size() == 3
    config.eclipseVersionConfigs.containsKey('3.7')
    config.eclipseVersionConfigs['3.7'].mavenGroup == 'eclipse-indigo'
    config.eclipseVersionConfigs.containsKey('4.2')
    config.eclipseVersionConfigs['4.2'].mavenGroup == 'eclipse-juno'
    config.eclipseVersionConfigs.containsKey('4.3')
    config.eclipseVersionConfigs['4.3'].mavenGroup == 'eclipse-kepler'
  }

  def 'should read eclipse modules configuration'() {
  when:
    Config config = reader.readFromResource('eclipseModulesConfig.groovy')
  then:
    config.eclipseVersionConfigs.size() == 3
    config.eclipseVersionConfigs['3.7'].moduleConfigs.size() == 2
    config.eclipseVersionConfigs['3.7'].moduleConfigs.containsKey('moduleA')
    config.eclipseVersionConfigs['3.7'].moduleConfigs.containsKey('moduleB')
    config.eclipseVersionConfigs['4.2'].moduleConfigs.size() == 2
    config.eclipseVersionConfigs['4.2'].moduleConfigs.containsKey('moduleC')
    config.eclipseVersionConfigs['4.2'].moduleConfigs.containsKey('moduleD')
    config.eclipseVersionConfigs['4.3'].moduleConfigs.size() == 2
    config.eclipseVersionConfigs['4.3'].moduleConfigs.containsKey('moduleE')
    config.eclipseVersionConfigs['4.3'].moduleConfigs.containsKey('moduleF')
  }
}
