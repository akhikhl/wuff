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
class Configurer {

  protected static final Logger log = LoggerFactory.getLogger(Configurer)

  protected final Project project
  protected final String moduleName
  protected final EclipseConfig defaultConfig
  protected String eclipseVersion

  Configurer(Project project, String moduleName) {

    this.project = project
    this.moduleName = moduleName
    this.defaultConfig = new EclipseConfigReader().readFromResource('org/akhikhl/wuff/defaultEclipseConfig.groovy')
  }

  protected void afterEvaluate(Closure closure) {
    project.afterEvaluate(closure)
  }

  void apply() {
    configure()
    afterEvaluate(this.&postConfigure)
  }

  private void applyModuleConfig(Closure closure) {

    def applyConfigs = { EclipseConfig eclipseConfig ->
      EclipseVersionConfig versionConfig = eclipseConfig.versionConfigs[eclipseVersion]
      if(versionConfig != null) {
        if(versionConfig.eclipseGroup != null)
          project.ext.eclipseGroup = versionConfig.eclipseGroup
        EclipseModuleConfig moduleConfig = versionConfig.moduleConfigs[moduleName]
        moduleConfig.properties.each { key, value ->
          if(value instanceof Collection)
            value.each { item ->
              if(item instanceof Closure && item.delegate != PlatformConfig) {
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

  protected void applyPlugins() {
    project.apply plugin: 'osgi'
  }

  protected void configure() {

    applyPlugins()
    createExtensions()

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

    applyModuleConfig { EclipseModuleConfig moduleConfig ->
      for(Closure closure in moduleConfig.configure)
        closure(project)
    }
  }

  protected void configureProducts() {
    // by default there are no products
  }

  protected void configureTasks() {
    // by default there are no tasks
  }

  protected void createExtensions() {
    project.extensions.create('eclipse', EclipseConfig)
  }

  protected void postConfigure() {
    if(project.version == 'unspecified')
      project.version = '1.0.0.0'
    applyModuleConfig { EclipseModuleConfig moduleConfig ->
      for(Closure closure in moduleConfig.postConfigure)
        closure(project)
    }
    configureTasks()
    configureProducts()
  }
}
