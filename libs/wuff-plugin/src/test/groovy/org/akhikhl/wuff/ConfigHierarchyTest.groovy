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
 * Test for hierarchical wuff configurations
 * @author akhikhl
 */
class ConfigHierarchyTest extends Specification {

  private EclipseConfigPlugin configPlugin

  def setup() {
    configPlugin = new EclipseConfigPlugin()
  }

  def 'should support selectedEclipseVersion inheritance and override'() {
  when:
    Project p1 = ProjectBuilder.builder().withName('p1').build()
    configPlugin.apply(p1)
    p1.wuff.with {
      selectedEclipseVersion = 'a'
      eclipseVersion 'a', {}
    }
    Project p2 = ProjectBuilder.builder().withName('p2').withParent(p1).build()
    configPlugin.apply(p2)
    Project p3 = ProjectBuilder.builder().withName('p3').withParent(p2).build()
    configPlugin.apply(p3)
    p3.wuff.with {
      selectedEclipseVersion = 'b'
      eclipseVersion 'b', {}
    }
    Project p4 = ProjectBuilder.builder().withName('p4').withParent(p3).build()
    configPlugin.apply(p4)
    Project p5 = ProjectBuilder.builder().withName('p5').withParent(p4).build()
    configPlugin.apply(p5)
  then:
    p1.effectiveWuff.selectedEclipseVersion == 'a'
    p2.effectiveWuff.selectedEclipseVersion == 'a'
    p3.effectiveWuff.selectedEclipseVersion == 'b'
    p4.effectiveWuff.selectedEclipseVersion == 'b'
    p5.effectiveWuff.selectedEclipseVersion == 'b'
  }

  def 'should support eclipseMavenGroup inheritance'() {
  when:
    Project p1 = ProjectBuilder.builder().withName('p1').build()
    configPlugin.apply(p1)
    p1.wuff.with {
      selectedEclipseVersion = 'a'
      eclipseVersion 'a', {
        eclipseMavenGroup = 'x'
      }
      eclipseVersion 'b', {
      }
      eclipseVersion 'c', {
        eclipseMavenGroup = 'x1'
      }
    }
    Project p2 = ProjectBuilder.builder().withName('p2').withParent(p1).build()
    configPlugin.apply(p2)
    p2.wuff.with {
      eclipseVersion 'a', {
        eclipseMavenGroup = 'y'
      }
      eclipseVersion 'b', {
        eclipseMavenGroup = 'z'
      }
    }
  then:
    p1.effectiveWuff.versionConfigs.a.eclipseMavenGroup == 'x'
    p1.effectiveWuff.versionConfigs.b.eclipseMavenGroup == null
    p1.effectiveWuff.versionConfigs.c.eclipseMavenGroup == 'x1'
    p2.effectiveWuff.versionConfigs.a.eclipseMavenGroup == 'y'
    p2.effectiveWuff.versionConfigs.b.eclipseMavenGroup == 'z'
    p2.effectiveWuff.versionConfigs.c.eclipseMavenGroup == 'x1'
  }

  def 'should support module inheritance'() {
  setup:
    def theModule = module
    Project parentProject = ProjectBuilder.builder().withName('PARENT_PROJ').build()
    new EclipseConfigPlugin().apply(parentProject)
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
    parentProject.unpuzzle.dryRun = true
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
