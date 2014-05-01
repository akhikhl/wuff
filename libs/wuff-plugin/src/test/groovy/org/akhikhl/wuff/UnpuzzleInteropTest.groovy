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
 * Test for interoperability with unpuzzle plugin
 * @author akhikhl
 */
class UnpuzzleInteropTest extends Specification {

  private EclipseConfigPlugin configPlugin

  def setup() {
    configPlugin = new EclipseConfigPlugin()
  }

  def 'should automatically create unpuzzle configuration'() {
  setup:
    Project project = ProjectBuilder.builder().withName('PROJ').build()
    project.apply(plugin: 'java')
    configPlugin.apply(project)
  expect:
    project.extensions.findByName('unpuzzle')
  }

  def 'should pass selectedEclipseVersion to unpuzzle'() {
  setup:
    Project project = ProjectBuilder.builder().withName('PROJ').build()
    project.apply(plugin: 'java')
    configPlugin.apply(project)
    project.wuff.with {
      selectedEclipseVersion = eversion
    }
    project.unpuzzle.dryRun = true
    project.evaluate()
  expect:
    project.unpuzzle.selectedEclipseVersion == eversion
  where:
    eversion << ['3.7', '4.2', '4.3']
  }

  def 'should pass eclipseMavenGroup to unpuzzle'() {
  setup:
    Project project = ProjectBuilder.builder().withName('PROJ').build()
    project.apply(plugin: 'java')
    configPlugin.apply(project)
    project.wuff.with {
      selectedEclipseVersion = eversion
      eclipseVersion(eversion) {
        eclipseMavenGroup = emavengroup
      }
    }
    project.unpuzzle.dryRun = true
    project.evaluate()
  expect:
    project.unpuzzle.versionConfigs[eversion].eclipseMavenGroup == emavengroup
  where:
    eversion << ['3.7', '4.2', '4.3']
    emavengroup << ['group1', 'group2', 'group3']
  }

  def 'should support source inheritance'() {
  when:
    Project p1 = ProjectBuilder.builder().withName('p1').build()
    configPlugin.apply(p1)
    p1.wuff.with {
      selectedEclipseVersion = 'a'
      eclipseVersion 'a', {
        sources {
          source 'source-1'
          source 'source-2'
        }
      }
      eclipseVersion 'b', {
      }
      eclipseVersion 'c', {
        sources {
          source 'source-3'
          source 'source-4'
        }
      }
    }
    Project p2 = ProjectBuilder.builder().withName('p2').withParent(p1).build()
    configPlugin.apply(p2)
    p2.wuff.with {
      eclipseVersion 'a', {
        sources {
          source 'source-5'
        }
      }
      eclipseVersion 'b', {
        sources {
          source 'source-6'
        }
      }
    }
    p1.unpuzzle.dryRun = true
    p1.evaluate()
    p2.evaluate()
  then:
    p1.effectiveUnpuzzle.versionConfigs.a.sources.collect { it.url } == ['source-1', 'source-2']
    p1.effectiveUnpuzzle.versionConfigs.b.sources.collect { it.url } == []
    p1.effectiveUnpuzzle.versionConfigs.c.sources.collect { it.url } == ['source-3', 'source-4']
    p2.effectiveUnpuzzle.versionConfigs.a.sources.collect { it.url } == ['source-1', 'source-2', 'source-5']
    p2.effectiveUnpuzzle.versionConfigs.b.sources.collect { it.url } == ['source-6']
    p2.effectiveUnpuzzle.versionConfigs.c.sources.collect { it.url } == ['source-3', 'source-4']
  }

  def 'should support mirror override'() {
  when:
    Project p1 = ProjectBuilder.builder().withName('p1').build()
    configPlugin.apply(p1)
    p1.wuff.with {
      selectedEclipseVersion = 'a'
      eclipseVersion 'a', {
        eclipseMirror = 'aaa'
        sources {
          source "${eclipseMirror}/source-1"
          source "${eclipseMirror}/source-2"
        }
      }
    }
    Project p2 = ProjectBuilder.builder().withName('p2').withParent(p1).build()
    configPlugin.apply(p2)
    p2.wuff.with {
      eclipseVersion 'a', {
        eclipseMirror = 'bbb'
        sources {
          source "${eclipseMirror}/source-3"
        }
      }
    }
    p1.unpuzzle.dryRun = true
    p1.evaluate()
    p2.evaluate()
  then:
    p1.effectiveUnpuzzle.versionConfigs.a.sources.collect { it.url } == ['aaa/source-1', 'aaa/source-2']
    p2.effectiveUnpuzzle.versionConfigs.a.sources.collect { it.url } == ['bbb/source-1', 'bbb/source-2', 'bbb/source-3']
  }

  def 'should support language pack templates'() {
  when:
    Project p1 = ProjectBuilder.builder().withName('p1').build()
    configPlugin.apply(p1)
    p1.wuff.with {
      selectedEclipseVersion = 'a'
      eclipseVersion 'a', {
        eclipseMirror = 'aaa'
        sources {
          // intentional: string, not GString (resolved when language pack is applied)
          languagePackTemplate '${eclipseMirror}/someFolder/somePackage_${language}.tar.gz'
          languagePack 'de'
        }
      }
    }
    Project p2 = ProjectBuilder.builder().withName('p2').withParent(p1).build()
    configPlugin.apply(p2)
    p2.wuff.with {
      eclipseVersion 'a', {
        eclipseMirror = 'bbb'
        sources {
          languagePack 'fr'
          languagePack 'es'
        }
      }
    }
    p1.unpuzzle.dryRun = true
    p1.evaluate()
    p2.evaluate()
  then:
    p1.effectiveUnpuzzle.versionConfigs.a.sources.collect { it.url } == ['aaa/someFolder/somePackage_de.tar.gz']
    p2.effectiveUnpuzzle.versionConfigs.a.sources.collect { it.url } == ['bbb/someFolder/somePackage_de.tar.gz', 'bbb/someFolder/somePackage_fr.tar.gz', 'bbb/someFolder/somePackage_es.tar.gz']
  }
}
