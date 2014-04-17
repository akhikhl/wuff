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
  protected final Config defaultConfig
  protected Config effectiveConfig
  protected String eclipseVersion

  Configurer(Project project) {
    this.project = project
    this.defaultConfig = new ConfigReader().readFromResource('defaultConfig.groovy')
  }

  protected void afterEvaluate(Closure closure) {
    project.afterEvaluate(closure)
  }

  void apply() {
    configure()
    afterEvaluate(this.&postConfigure)
  }

  protected void applyPlugins() {
    project.apply plugin: 'osgi'
  }

  private void applyModuleAction(String action) {
    EclipseVersionConfig versionConfig = effectiveConfig.versionConfigs[effectiveConfig.defaultEclipseVersion]
    if(versionConfig) {
      for(String moduleName in getModules()) {
        EclipseModuleConfig moduleConfig = versionConfig.moduleConfigs[moduleName]
        if(moduleConfig) {
          for(Closure closure in moduleConfig[action]) {
            closure = closure.rehydrate(PlatformConfig, closure.owner, closure.thisObject)
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure(project)
          }
        }
      }
    } else
      log.error 'Eclipse version {} is not configured', effectiveConfig.defaultEclipseVersion
  }

  protected void configure() {

    applyPlugins()
    createExtensions()

    setupConfigChain(project)

    effectiveConfig = project.wuff.effectiveConfig
    eclipseVersion = effectiveConfig.defaultEclipseVersion

    Project p = project
    while(p != null) {
      if(p.extensions.findByName('wuff')) {
        def econf = p.wuff.effectiveConfig
        p.ext.eclipseMavenGroup = econf.versionConfigs[econf.defaultEclipseVersion]?.eclipseMavenGroup
      }
      p = p.parent
    }

    def unpuzzleConfigurer = new org.akhikhl.unpuzzle.Configurer(project.rootProject)
    unpuzzleConfigurer.apply()
    unpuzzleConfigurer.installEclipse()

    project.configurations {
      privateLib
      compile.extendsFrom privateLib
    }

    applyModuleAction('configure')
  }

  protected void configureProducts() {
    // by default there are no products
  }

  protected void configureTasks() {
    configureTask_createExtraFiles()
    configureTask_Jar()
    configureTask_scaffold()
  }

  private void configureTask_createExtraFiles() {
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

  private void configureTask_Jar() {
    project.tasks.jar {
      dependsOn project.tasks.createExtraFiles
      from PluginUtils.getExtraDir(project)
      // Normally these files should be placed in src/main/resources.
      // We support them in root to be backward-compatible with eclipse project layout.
      from 'splash.bmp'
      from 'OSGI-INF', {
        into 'OSGI-INF'
      }
      from 'intro', {
        into 'intro'
      }
      from 'nl', {
        into 'nl'
      }
    }
  }

  private void configureTask_scaffold() {
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
        expand projectName: project.name, packageName: packageName
        eachFile { details ->
          String rpath = details.relativePath.toString()
          rpath = rpath.substring(resourceDir.length())
          rpath = rpath.replaceAll($/(.*)/_package_/(.*)/$, '$1/' + packagePath + '/$2')
          details.relativePath = new RelativePath(!details.directory, rpath)
        }
        includeEmptyDirs = false
      }
  }

  protected void createExtensions() {
    project.extensions.create('wuff', Config)
  }

  protected void createExtraFiles() {
  }

  protected void createVirtualConfigurations() {
  }

  protected boolean extraFilesUpToDate() {
    return true
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

  protected void postConfigure() {
    if(project.version == 'unspecified')
      project.version = '1.0.0.0'
    createVirtualConfigurations()
    applyModuleAction('postConfigure')
    configureTasks()
    configureProducts()
  }

  private void setupConfigChain(Project project) {
    if(project.wuff.parentConfig == null) {
      Project p = project.parent
      while(p != null && !p.extensions.findByName('wuff'))
        p = p.parent
      if(p == null)
        project.wuff.parentConfig = defaultConfig
      else {
        project.wuff.parentConfig = p.wuff
        setupConfigChain(p)
      }
    }
  }
}
