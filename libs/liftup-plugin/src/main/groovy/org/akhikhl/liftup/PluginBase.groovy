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
abstract class PluginBase implements Plugin<Project> {

  Project project
  String eclipseVersion
  EclipseConfig defaultConfig

  void apply(final Project project) {

    this.project = project
    defaultConfig = new EclipseConfigReader().readFromResource('defaultEclipseConfig.groovy')

    project.apply plugin: 'osgi'
    project.extensions.create('eclipse', EclipseConfig)

    if(project.hasProperty('eclipseVersion'))
      // project properties are inherently hierarchical, so parent's eclipseVersion will be inherited
      eclipseVersion = project.eclipseVersion
    else {
      Project p = ProjectUtils.findUpAncestorChain(project, { it.extensions.findByName('eclipse')?.defaultVersion != null })
      eclipseVersion = p != null ? p.eclipse.defaultVersion : defaultConfig.defaultVersion
      if(eclipseVersion == null)
        eclipseVersion = defaultConfig.defaultVersion
    }

    project.eclipse.defaultVersion = eclipseVersion

    configure { EclipseModuleConfig moduleConfig ->
      for(Closure preConfigure in moduleConfig.preConfigure)
        preConfigure(project)
    }
  }

  void configure(Closure moduleAction) {

    def applyConfigs = { EclipseConfig eclipseConfig ->
      EclipseVersionConfig versionConfig = eclipseConfig.versionConfigs[eclipseVersion]
      if(versionConfig != null) {
        if(versionConfig.eclipseGroup != null)
          project.ext.eclipseGroup = versionConfig.eclipseGroup
        EclipseModuleConfig moduleConfig = versionConfig.moduleConfigs[moduleName]
        moduleConfig.properties.each { key, value ->
          if(value instanceof Collection)
            value.each { item ->
              if(item instanceof Closure) {
                item.delegate = PlatformConfig
                item.resolveStrategy = Closure.DELEGATE_FIRST
              }
            }
        }
        moduleAction(moduleConfig)
      }
    }

    applyConfigs(defaultConfig)

    ProjectUtils.collectWithAllAncestors(project).each { Project p ->
      EclipseConfig config = p.extensions.findByName('eclipse')
      if(config)
        applyConfigs(config)
    }
  }

  abstract String getModuleName()
}

