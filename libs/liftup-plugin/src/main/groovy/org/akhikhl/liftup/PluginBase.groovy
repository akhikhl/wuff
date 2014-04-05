/*
 * liftup
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.liftup

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 *
 * @author akhikhl
 */
class PluginBase implements Plugin<Project> {

  private static EclipseConfig defaultConfig

  void apply(final Project project) {

    if(defaultConfig == null)
      defaultConfig = new EclipseConfigReader().readFromResource('defaultEclipseConfig.groovy')

    project.apply plugin: 'osgi'
    project.extensions.create('eclipse', EclipseConfig)

    String eclipseVersion

    if(project.hasProperty('eclipseVersion'))
      // project properties are inherently hierarchical, so parent's eclipseVersion will be inherited
      eclipseVersion = project.eclipseVersion
    else {
      Project p = ProjectUtils.findUpAncestorChain(project, { it.extensions.findByName('eclipse')?.defaultVersion != null })
      eclipseVersion = p != null ? p.eclipse.defaultVersion : defaultConfig.defaultVersion
      if(eclipseVersion == null)
        eclipseVersion = defaultConfig.defaultVersion
    }
  }
}

