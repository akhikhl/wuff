/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import java.util.jar.Manifest
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

/**
 *
 * @author akhikhl
 */
class ManifestUtilsTest extends Specification {

  private File projectDir
  private Project project
  private static final Logger log = LoggerFactory.getLogger(ManifestUtilsTest)

  def setup() {
    projectDir = new File('.').absoluteFile
    project = ProjectBuilder.builder().build()
  }

  def 'should read manifest from file'() {
  when:
    Manifest manifest = ManifestUtils.getManifest(project, new File(projectDir, 'src/test/resources/eclipse-bundle-1-1.0.0.0.jar'))
  then:
    manifest.mainAttributes.getValue('Bundle-SymbolicName') == 'eclipse-bundle-1'
    manifest.mainAttributes.getValue('Bundle-Version') == '1.0.0.0'
    manifest.mainAttributes.getValue('Require-Bundle') == 'org.eclipse.jface,org.eclipse.ui,org.eclipse.core.resources,org.eclipse.core.runtime,org.eclipse.swt'
  }
}

