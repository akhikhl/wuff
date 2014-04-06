/*
 * liftup
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.liftup

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

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

  def 'supports eclipse bundle definition'() {
  when:
    project.apply(plugin: 'java')
    plugin.apply(project)
    project.evaluate()
  then:
    project.extensions.findByName('eclipse')
    project.eclipse.defaultVersion == '4.3'
    project.configurations.findByName('privateLib')
    project.configurations.findByName('compile')
  }
}

