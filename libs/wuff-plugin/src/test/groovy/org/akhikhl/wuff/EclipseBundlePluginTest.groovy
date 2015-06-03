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
import org.akhikhl.unpuzzle.PlatformConfig

/**
 *
 * @author akhikhl
 */
class EclipseBundlePluginTest extends Specification {

  private Project project
  private EclipseBundlePlugin plugin

  def setup() {
    project = ProjectBuilder.builder().build()
    plugin = new EclipseBundlePlugin()
  }

  def 'should create wuff extension'() {
  when:
    project.apply(plugin: 'java')
    plugin.apply(project)
  then:
    project.extensions.findByName('wuff')
  }

  def 'should create configurations'() {
  when:
    project.apply(plugin: 'java')
    plugin.apply(project)
  then:
    project.configurations.findByName('compile')
    project.configurations.findByName('provided')
    project.configurations.findByName('privateLib')
  }

  def 'should inject dependencies'() {
  when:
    project.apply(plugin: 'java')
    project.repositories {
      mavenLocal()
      mavenCentral()
    }
    plugin.apply(project)
    project.evaluate()
  then:
    project.configurations.compile.dependencies.find { it.name.startsWith('org.eclipse.swt') }
    project.configurations.compile.dependencies.find { it.name.startsWith('org.eclipse.jface') }
    project.configurations.compile.dependencies.find { it.name.startsWith('org.eclipse.ui') }
    project.configurations.provided.dependencies.find { it.name.startsWith("org.eclipse.swt.${PlatformConfig.current_os_suffix}${PlatformConfig.current_arch_suffix}") }
  }
}

