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

  def 'should support selectedEclipseVersion inheritance'() {
  when:
    def c1 = new Config(selectedEclipseVersion: 'a')
    def c2 = new Config(parentConfig: c1)
    def c3 = new Config(parentConfig: c2, selectedEclipseVersion: 'b')
    def c4 = new Config(parentConfig: c3)
    def c5 = new Config(parentConfig: c4)
  then:
    c1.effectiveConfig.selectedEclipseVersion == 'a'
    c2.effectiveConfig.selectedEclipseVersion == 'a'
    c3.effectiveConfig.selectedEclipseVersion == 'b'
    c4.effectiveConfig.selectedEclipseVersion == 'b'
    c5.effectiveConfig.selectedEclipseVersion == 'b'
  }

  def 'should support eclipseMavenGroup inheritance'() {
  when:
    def c1 = new Config(selectedEclipseVersion: 'a')
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
    def theModule = module
    Project parentProject = ProjectBuilder.builder().withName('PARENT_PROJ').build()
    new EclipseConfigPlugin().apply(parentProject)
    parentProject.unpuzzle.with {
      eclipseVersion 'a', {}
    }
    parentProject.wuff.with {
      selectedEclipseVersion 'a'
      eclipseVersion 'a', {
        eclipseMavenGroup = 'eclipse1'
        moduleA {
          project.dependencies {
            compile "${eclipseMavenGroup}:dep1:+"
          }
        }
        moduleB {
          project.dependencies {
            compile "${eclipseMavenGroup}:dep2:+"
          }
        }
      }
    }
    Project project = ProjectBuilder.builder().withName('PROJ').withParent(parentProject).build()
    project.apply(plugin: 'java')
    def configurer = new Configurer(project) {
      protected List<String> getModules() {
        [ theModule ]
      }
    }
    configurer.apply()
    project.wuff.with {
      eclipseVersion 'a', {
        moduleA {
          project.dependencies {
            compile "${eclipseMavenGroup}:dep3:+"
          }
        }
        moduleC {
          project.dependencies {
            compile "${eclipseMavenGroup}:dep4:+"
          }
        }
      }
    }
    parentProject.evaluate()
    project.evaluate()
  expect:
    project.configurations.compile.dependencies.collect { "${it.group}:${it.name}" } == dependencyList

  where:
    module    | dependencyList
    'moduleA' | ['eclipse1:dep1', 'eclipse1:dep3']
    'moduleB' | ['eclipse1:dep2']
    'moduleC' | ['eclipse1:dep4']
  }
}
