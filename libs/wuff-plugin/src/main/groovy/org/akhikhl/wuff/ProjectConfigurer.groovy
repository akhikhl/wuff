/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class ProjectConfigurer {

  private static final Logger log = LoggerFactory.getLogger(ProjectConfigurer)

  Project project
  String moduleName
  EclipseConfig defaultConfig
  String eclipseVersion

  ProjectConfigurer(Project project, String moduleName) {

    this.project = project
    this.moduleName = moduleName
    defaultConfig = new EclipseConfigReader().readFromResource('org/akhikhl/wuff/defaultEclipseConfig.groovy')
  }

  private void apply(Closure closure) {

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
        closure(moduleConfig)
      }
    }

    applyConfigs(defaultConfig)

    ProjectUtils.collectWithAllAncestors(project).each { Project p ->
      EclipseConfig config = p.extensions.findByName('eclipse')
      if(config)
        applyConfigs(config)
    }
  }

  void configure() {
    if(project.version == 'unspecified')
      project.version = '1.0.0.0'
    apply { EclipseModuleConfig moduleConfig ->
      for(Closure closure in moduleConfig.configure)
        closure(project)
    }
  }

  void preConfigure() {

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

    project.configurations {
      privateLib
      compile.extendsFrom privateLib
    }

    apply { EclipseModuleConfig moduleConfig ->
      for(Closure closure in moduleConfig.preConfigure)
        closure(project)
    }
  }
}
