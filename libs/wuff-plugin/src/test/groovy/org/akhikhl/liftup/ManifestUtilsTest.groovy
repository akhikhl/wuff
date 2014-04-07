/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.liftup

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 *
 * @author akhikhl
 */
class ManifestUtilsTest extends Specification {

  private Project project

  def setup() {
    project = ProjectBuilder.builder().build()
  }

  def 'should read manifest from file'() {
    // ManifestUtils.getManifest(project)
  }
}

