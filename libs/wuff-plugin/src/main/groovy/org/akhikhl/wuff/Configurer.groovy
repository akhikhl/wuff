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

  Configurer(Project project) {
    this.project = project
    this.defaultConfig = new ConfigReader().readFromResource('defaultConfig.groovy')
  }

  protected void afterEvaluate(Closure closure) {
    project.afterEvaluate(closure)
  }

  void apply() {
    preConfigure()
    configure()
    afterEvaluate(this.&postConfigure)
  }

  protected void applyPlugins() {
    if(!project.extensions.findByName('unpuzzle')) {
      def unpuzzleConfigurer = new org.akhikhl.unpuzzle.Configurer(project)
      unpuzzleConfigurer.apply()
      unpuzzleConfigurer.installEclipse()
    }
  }

  private void applyModuleAction(String action) {
    for(String moduleName in getModules())
      applyModuleAction(null, effectiveConfig.defaultEclipseVersion, moduleName, action)
  }

  private void applyModuleAction(delegate, String versionString, String moduleName, String action) {
    EclipseVersionConfig versionConfig = effectiveConfig.versionConfigs[versionString]
    if(versionConfig) {
      if(delegate == null) {
        delegate = new Expando()
        delegate.eclipseMavenGroup = versionConfig.eclipseMavenGroup
        delegate.supported_oses = PlatformConfig.supported_oses
        delegate.supported_archs = PlatformConfig.supported_archs
        delegate.supported_languages = PlatformConfig.supported_languages
        delegate.current_os = PlatformConfig.current_os
        delegate.current_arch = PlatformConfig.current_arch
        delegate.current_language = PlatformConfig.current_language
        delegate.supported_oses = PlatformConfig.supported_oses
        delegate.map_os_to_suffix = PlatformConfig.map_os_to_suffix
        delegate.map_os_to_filesystem_suffix = PlatformConfig.map_os_to_filesystem_suffix
        delegate.map_arch_to_suffix = PlatformConfig.map_arch_to_suffix
        delegate.current_os_suffix = PlatformConfig.current_os_suffix
        delegate.current_os_filesystem_suffix = PlatformConfig.current_os_filesystem_suffix
        delegate.current_arch_suffix = PlatformConfig.current_arch_suffix
        delegate.isLanguageFragment = PlatformConfig.&isLanguageFragment
        delegate.isPlatformFragment = PlatformConfig.&isPlatformFragment
        delegate.PluginUtils = PluginUtils
        delegate.project = project
      }
      for(String baseVersion in versionConfig.baseVersions)
        applyModuleAction(delegate, baseVersion, moduleName, action)
      EclipseModuleConfig moduleConfig = versionConfig.moduleConfigs[moduleName]
      if(moduleConfig) {
        for(Closure closure in moduleConfig[action]) {
          closure = closure.rehydrate(delegate, closure.owner, closure.thisObject)
          closure.resolveStrategy = Closure.DELEGATE_FIRST
          closure()
        }
      }
    } else
      log.error 'Eclipse version {} is not configured', versionString
  }

  protected void configure() {
    applyModuleAction('configure')
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
    project.extensions.create('wuff', Config)
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
      project.version = getDefaultVersion()
    createVirtualConfigurations()
    applyModuleAction('postConfigure')
    configureTasks()
  }

  protected void preConfigure() {

    applyPlugins()
    createExtensions()

    setupConfigChain(project)

    defaultConfig.defaultEclipseVersion = project.unpuzzle.effectiveConfig.defaultEclipseVersion
    def c = project.wuff
    while(c != null) {
      log.warn 'DBG wuff-chain defaultEclipseVersion={}', c.defaultEclipseVersion
      c = c.parentConfig
    }
    //log.warn '{}: defaultConfig eclipse version {}', project.name, defaultConfig.defaultEclipseVersion
    effectiveConfig = project.wuff.effectiveConfig
    log.warn '{}: using eclipse version {}', project.name, effectiveConfig.defaultEclipseVersion

    Project p = project
    while(p != null) {
      if(p.extensions.findByName('wuff')) {
        def econf = p.wuff.effectiveConfig
        p.ext.eclipseMavenGroup = econf.versionConfigs[econf.defaultEclipseVersion]?.eclipseMavenGroup
      }
      p = p.parent
    }

    createConfigurations()
  }

  private void setupConfigChain(Project project) {
    if(project.wuff.parentConfig == null) {
      Project p = project.parent
      while(p != null && !p.extensions.findByName('wuff'))
        p = p.parent
      if(p == null) {
        log.debug 'there\'s no parent wuff extension for {}, setting parent to defaultConfig', project.name
        project.wuff.parentConfig = defaultConfig
      }
      else {
        log.debug 'setting parent wuff {} -> {}', project.name, p.name
        project.wuff.parentConfig = p.wuff
        setupConfigChain(p)
      }
    }
  }
}
