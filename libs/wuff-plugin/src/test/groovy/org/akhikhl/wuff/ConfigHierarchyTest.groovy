/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
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

  private EclipseConfigPlugin plugin

  def setup() {
    plugin = new EclipseConfigPlugin()
  }

  def 'should support default localMavenRepositoryDir'() {
  when:
    Project p = ProjectBuilder.builder().withName('p').build()
    plugin.apply(p)
  then:
    p.effectiveWuff.localMavenRepositoryDir == new File(System.getProperty('user.home'), '.wuff/m2_repository')
  }

  def 'should support localMavenRepositoryDir inheritance'() {
  when:
    Project p1 = ProjectBuilder.builder().withName('p1').build()
    plugin.apply(p1)
    def file1 = new File(System.getProperty('user.home'), 'someDirectory')
    p1.wuff.localMavenRepositoryDir = file1
    Project p2 = ProjectBuilder.builder().withName('p2').withParent(p1).build()
    plugin.apply(p2)
    Project p3 = ProjectBuilder.builder().withName('p3').withParent(p2).build()
    plugin.apply(p3)
    def file2 = new File(System.getProperty('user.home'), 'someDirectory2')
    p3.wuff.localMavenRepositoryDir = file2
    Project p4 = ProjectBuilder.builder().withName('p4').withParent(p3).build()
    plugin.apply(p4)
  then:
    p1.effectiveWuff.localMavenRepositoryDir == file1
    p2.effectiveWuff.localMavenRepositoryDir == file1
    p3.effectiveWuff.localMavenRepositoryDir == file2
    p4.effectiveWuff.localMavenRepositoryDir == file2
  }

  def 'should support default wuffDir'() {
  when:
    Project p = ProjectBuilder.builder().withName('p').build()
    plugin.apply(p)
  then:
    p.effectiveWuff.wuffDir == new File(System.getProperty('user.home'), '.wuff')
  }

  def 'should support wuffDir inheritance'() {
  when:
    Project p1 = ProjectBuilder.builder().withName('p1').build()
    plugin.apply(p1)
    def file1 = new File(System.getProperty('user.home'), 'someDirectory')
    p1.wuff.wuffDir = file1
    Project p2 = ProjectBuilder.builder().withName('p2').withParent(p1).build()
    plugin.apply(p2)
    Project p3 = ProjectBuilder.builder().withName('p3').withParent(p2).build()
    plugin.apply(p3)
    def file2 = new File(System.getProperty('user.home'), 'someDirectory2')
    p3.wuff.wuffDir = file2
    Project p4 = ProjectBuilder.builder().withName('p4').withParent(p3).build()
    plugin.apply(p4)
  then:
    p1.effectiveWuff.wuffDir == file1
    p2.effectiveWuff.wuffDir == file1
    p3.effectiveWuff.wuffDir == file2
    p4.effectiveWuff.wuffDir == file2
  }

  def 'should support selectedEclipseVersion inheritance and override'() {
  when:
    Project p1 = ProjectBuilder.builder().withName('p1').build()
    plugin.apply(p1)
    p1.wuff.with {
      selectedEclipseVersion = 'a'
      eclipseVersion 'a', {}
    }
    Project p2 = ProjectBuilder.builder().withName('p2').withParent(p1).build()
    plugin.apply(p2)
    Project p3 = ProjectBuilder.builder().withName('p3').withParent(p2).build()
    plugin.apply(p3)
    p3.wuff.with {
      selectedEclipseVersion = 'b'
      eclipseVersion 'b', {}
    }
    Project p4 = ProjectBuilder.builder().withName('p4').withParent(p3).build()
    plugin.apply(p4)
    Project p5 = ProjectBuilder.builder().withName('p5').withParent(p4).build()
    plugin.apply(p5)
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
    plugin.apply(p1)
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
    plugin.apply(p2)
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
