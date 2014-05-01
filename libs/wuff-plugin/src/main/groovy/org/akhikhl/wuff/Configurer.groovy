/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Copy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class Configurer {

  protected static final Logger log = LoggerFactory.getLogger(Configurer)

  protected final Project project
  private unpuzzleConfigurer

  Configurer(Project project) {
    this.project = project
  }

  protected void afterEvaluate(Closure closure) {
    project.afterEvaluate(closure)
  }

  final void apply() {
    preConfigure()
    configure()
    afterEvaluate(this.&postConfigure)
  }

  protected void applyPlugins() {
    unpuzzleConfigurer = new org.akhikhl.unpuzzle.Configurer(project)
    if(!project.extensions.findByName('unpuzzle')) {
      unpuzzleConfigurer.apply()
      assert project.extensions.findByName('unpuzzle')
    }
  }

  protected void configure() {
  }

  protected void configureTasks() {
    configureTask_createExtraFiles()
    configureTask_scaffold()
  }

  protected void configureTask_createExtraFiles() {
    project.task('createExtraFiles') {
      group = 'wuff'
      description = 'creates project-specific extra files in buildDir/extra'
      inputs.properties getExtraFilesProperties()
      outputs.upToDateWhen {
        extraFilesUpToDate()
      }
      doLast {
        createExtraFiles()
      }
    }
  }

  protected void configureTask_scaffold() {
    String resourceDir = getScaffoldResourceDir()
    if(resourceDir != null)
      project.task('scaffold', type: Copy) {
        group = 'wuff'
        description = 'creates default source code files and configuration files'
        if(!resourceDir.endsWith('/'))
          resourceDir += '/'
        String path = URLDecoder.decode(Configurer.class.getProtectionDomain().getCodeSource().getLocation().getPath(), 'UTF-8')
        String packageName = project.name.toLowerCase().replace('-', '.')
        String packagePath = packageName.replace('.', '/')
        from project.zipTree(path)
        into project.projectDir
        include "${resourceDir}**"
        rename ~/(.+)\.java_$/, '$1.java'
        expand project: project, packageName: packageName
        eachFile { details ->
          String rpath = details.relativePath.toString()
          rpath = rpath.substring(resourceDir.length())
          rpath = rpath.replaceAll($/(.*)/_package_/(.*)/$, '$1/' + packagePath + '/$2')
          details.relativePath = new RelativePath(!details.directory, rpath)
        }
        includeEmptyDirs = false
      }
  }

  protected void createConfigurations() {
  }

  protected void createExtensions() {

    if(!project.extensions.findByName('wuff'))
      project.extensions.create('wuff', Config)

    def self = this

    project.metaClass {

      getEffectiveWuff = {
        self.getEffectiveConfig()
      }

      getEclipseMavenGroup = {
        self.getSelectedEclipseMavenGroup()
      }
    }
  }

  protected void createExtraFiles() {
  }

  protected void createVirtualConfigurations() {
  }

  protected boolean extraFilesUpToDate() {
    return true
  }

  protected String getDefaultVersion() {
    '1.0'
  }

  Config getEffectiveConfig() {
    if(!project.ext.has('_effectiveWuff')) {
      Config econfig = new Config()
      Config.merge(econfig, project.wuff)
      project.ext._effectiveWuff = econfig
    }
    return project._effectiveWuff
  }

  String getSelectedEclipseMavenGroup() {
    if(!project.ext.has('_selectedEclipseMavenGroup')) {
      project.ext._selectedEclipseMavenGroup = effectiveConfig.selectedVersionConfig?.eclipseMavenGroup
      populateUnpuzzleConfig()
      unpuzzleConfigurer.installEclipse()
    }
    return project.ext._selectedEclipseMavenGroup
  }

  protected String getScaffoldResourceDir() {
    null
  }

  protected Map getExtraFilesProperties() {
    [:]
  }

  protected List<String> getModules() {
    return []
  }

  private void populateUnpuzzleConfig() {
    project.unpuzzle.selectedEclipseVersion = project.wuff.selectedEclipseVersion
    project.wuff.languagePacks.each {
      project.unpuzzle.languagePack it
    }
    project.wuff.versionConfigs.each { String versionString, EclipseVersionConfig versionConfig ->
      project.unpuzzle.eclipseVersion(versionString) {
        eclipseMavenGroup = versionConfig.eclipseMavenGroup
        if(versionConfig.eclipseMirror)
          eclipseMirror = versionConfig.eclipseMirror
        if(versionConfig.eclipseArchiveMirror)
          eclipseArchiveMirror = versionConfig.eclipseArchiveMirror
        for(Closure sourcesClosure in versionConfig.lazySources)
          sources sourcesClosure
      }
    }
  }

  protected void postConfigure() {

    if(project.version == 'unspecified')
      project.version = getDefaultVersion()

    // guarded actuation of unpuzzle
    getSelectedEclipseMavenGroup()

    createVirtualConfigurations()

    new ModuleConfigurer(project).configureModules(getModules())

    configureTasks()
  }

  protected void preConfigure() {
    applyPlugins()
    createExtensions()
    setupConfigChain(project)
    createConfigurations()
  }

  private static void setupConfigChain(Project project) {
    if(project.wuff.parentConfig == null) {
      Project p = project.parent
      while(p != null && !p.extensions.findByName('wuff'))
        p = p.parent
      if(p == null) {
        log.debug '{}.wuff.parentConfig <- defaultConfig', project.name
        project.wuff.parentConfig = new ConfigReader().readFromResource('defaultConfig.groovy')
      }
      else {
        log.debug '{}.wuff.parentConfig <- {}.wuff', project.name, p.name
        project.wuff.parentConfig = p.wuff
        setupConfigChain(p)
      }
    } else
      log.debug '{}.wuff already has parentConfig, setupConfigChain skipped', project.name
  }
}
