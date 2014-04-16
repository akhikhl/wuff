/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 *
 * @author akhikhl
 */
class ConfigHierarchyTest extends Specification {

  def 'should support defaultEclipseVersion inheritance'() {
  when:
    def c1 = new Config(defaultEclipseVersion: 'a')
    def c2 = new Config(parentConfig: c1)
    def c3 = new Config(parentConfig: c2, defaultEclipseVersion: 'b')
    def c4 = new Config(parentConfig: c3)
    def c5 = new Config(parentConfig: c4)
  then:
    c1.effectiveConfig.defaultEclipseVersion == 'a'
    c2.effectiveConfig.defaultEclipseVersion == 'a'
    c3.effectiveConfig.defaultEclipseVersion == 'b'
    c4.effectiveConfig.defaultEclipseVersion == 'b'
    c5.effectiveConfig.defaultEclipseVersion == 'b'
  }

  def 'should support eclipseMavenGroup inheritance'() {
  when:
    def c1 = new Config(defaultEclipseVersion: 'a')
    c1.eclipseVersion 'a', {
      eclipseMavenGroup = 'x'
    }
    c1.eclipseVersion 'b', {
    }
    c1.eclipseVersion 'c', {
      eclipseMavenGroup = 'x1'
    }
    def c2 = new Config(parentConfig: c1)
    c2.eclipseVersion 'a', {
      eclipseMavenGroup = 'y'
    }
    c2.eclipseVersion 'b', {
      eclipseMavenGroup = 'z'
    }
  then:
    c1.effectiveConfig.versionConfigs.a.eclipseMavenGroup == 'x'
    c1.effectiveConfig.versionConfigs.b.eclipseMavenGroup == null
    c1.effectiveConfig.versionConfigs.c.eclipseMavenGroup == 'x1'
    c2.effectiveConfig.versionConfigs.a.eclipseMavenGroup == 'y'
    c2.effectiveConfig.versionConfigs.b.eclipseMavenGroup == 'z'
    c2.effectiveConfig.versionConfigs.c.eclipseMavenGroup == 'x1'
  }

  def 'should support module inheritance'() {
  setup:
    def c1 = new Config(defaultEclipseVersion: 'a')
    c1.eclipseVersion 'a', {
      eclipseMavenGroup = 'eclipse1'
      moduleA {
        configure { proj ->
          proj.dependencies {
            compile "${eclipseMavenGroup}:dep1:+"
          }
        }
      }
      moduleB {
        configure { proj ->
          proj.dependencies {
            compile "${eclipseMavenGroup}:dep2:+"
          }
        }
      }
    }
    def c2 = new Config(parentConfig: c1)
    c2.eclipseVersion 'a', {
      moduleA {
        configure { proj ->
          proj.dependencies {
            compile "${eclipseMavenGroup}:dep3:+"
          }
        }
      }
      moduleC {
        configure { proj ->
          proj.dependencies {
            compile "${eclipseMavenGroup}:dep4:+"
          }
        }
      }
    }
    Project project = ProjectBuilder.builder().build()
    project.apply(plugin: 'java')
    project.repositories {
      mavenLocal()
      mavenCentral()
    }
    c2.effectiveConfig.versionConfigs.a.moduleConfigs[module].configure.each {
      it(project)
    }
  expect:
    project.configurations.compile.dependencies.collect { "${it.group}:${it.name}" } == dependencyList
  where:
    module    | dependencyList
    'moduleA' | ['eclipse1:dep1', 'eclipse1:dep3']
    'moduleB' | ['eclipse1:dep2']
    'moduleC' | ['eclipse1:dep4']
  }
}

