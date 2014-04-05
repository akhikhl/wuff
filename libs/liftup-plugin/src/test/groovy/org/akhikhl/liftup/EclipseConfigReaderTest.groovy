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
class EclipseConfigReaderTest extends Specification {

  EclipseConfigReader reader

  def setup() {
    reader = new EclipseConfigReader()
  }

  def 'should read empty configuration'() {
  when:
    EclipseConfig config = reader.readFromResource('emptyConfig.groovy')
  then:
    config.defaultVersion == null
    config.versionConfigs.isEmpty()
  }

  def 'should read default eclipse version'() {
  when:
    EclipseConfig config = reader.readFromResource('defaultEclipseVersionConfig.groovy')
  then:
    config.defaultVersion == '4.3'
    config.versionConfigs.isEmpty()
  }

  def 'should read eclipse versions configuration'() {
  when:
    EclipseConfig config = reader.readFromResource('eclipseVersionsConfig.groovy')
  then:
    config.versionConfigs.size() == 3
    config.versionConfigs.containsKey('3.7')
    config.versionConfigs['3.7'].eclipseGroup == 'eclipse-indigo'
    config.versionConfigs.containsKey('4.2')
    config.versionConfigs['4.2'].eclipseGroup == 'eclipse-juno'
    config.versionConfigs.containsKey('4.3')
    config.versionConfigs['4.3'].eclipseGroup == 'eclipse-kepler'
  }

  def 'should read eclipse modules configuration'() {
  when:
    EclipseConfig config = reader.readFromResource('eclipseModulesConfig.groovy')
  then:
    config.versionConfigs.size() == 3
    config.versionConfigs['3.7'].moduleConfigs.size() == 2
    config.versionConfigs['3.7'].moduleConfigs.containsKey('moduleA')
    config.versionConfigs['3.7'].moduleConfigs.containsKey('moduleB')
    config.versionConfigs['4.2'].moduleConfigs.size() == 2
    config.versionConfigs['4.2'].moduleConfigs.containsKey('moduleC')
    config.versionConfigs['4.2'].moduleConfigs.containsKey('moduleD')
    config.versionConfigs['4.3'].moduleConfigs.size() == 2
    config.versionConfigs['4.3'].moduleConfigs.containsKey('moduleE')
    config.versionConfigs['4.3'].moduleConfigs.containsKey('moduleF')
  }

  def 'should read eclipse module details configuration'() {
  when:
    EclipseConfig config = reader.readFromResource('eclipseModuleDetailsConfig.groovy')
  then:
    config.versionConfigs.size() == 1
    config.versionConfigs['4.3'].moduleConfigs.size() == 2
    config.versionConfigs['4.3'].moduleConfigs.containsKey('moduleA')
    config.versionConfigs['4.3'].moduleConfigs['moduleA'] instanceof EclipseModuleConfig
    config.versionConfigs['4.3'].moduleConfigs['moduleA'].configure.size() == 1
    config.versionConfigs['4.3'].moduleConfigs['moduleA'].configure[0] instanceof Closure
    config.versionConfigs['4.3'].moduleConfigs['moduleA'].platformSpecific.size() == 0
    config.versionConfigs['4.3'].moduleConfigs['moduleA'].platformAndLanguageSpecific.size() == 0
    config.versionConfigs['4.3'].moduleConfigs.containsKey('moduleB')
    config.versionConfigs['4.3'].moduleConfigs['moduleB'] instanceof EclipseModuleConfig
    config.versionConfigs['4.3'].moduleConfigs['moduleB'].configure.size() == 1
    config.versionConfigs['4.3'].moduleConfigs['moduleB'].configure[0] instanceof Closure
    config.versionConfigs['4.3'].moduleConfigs['moduleB'].platformSpecific.size() == 1
    config.versionConfigs['4.3'].moduleConfigs['moduleB'].platformSpecific[0] instanceof Closure
    config.versionConfigs['4.3'].moduleConfigs['moduleB'].platformAndLanguageSpecific.size() == 1
    config.versionConfigs['4.3'].moduleConfigs['moduleB'].platformAndLanguageSpecific[0] instanceof Closure
  }
}
