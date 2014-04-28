/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project
import org.gradle.api.file.FileCopyDetails
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
      }
      for(String baseVersion in versionConfig.baseVersions)
        applyModuleAction(delegate, baseVersion, moduleName, action)
      EclipseModuleConfig moduleConfig = versionConfig.moduleConfigs[moduleName]
      if(moduleConfig) {
        for(Closure closure in moduleConfig[action]) {
          closure = closure.rehydrate(delegate, closure.owner, closure.thisObject)
          closure.resolveStrategy = Closure.DELEGATE_FIRST
          closure(project)
        }
      }
    } else
      log.error 'Eclipse version {} is not configured', versionString
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

    createSourceSets()
    createConfigurations()

    applyModuleAction('configure')
  }

  protected void configureProducts() {
    // by default there are no products
  }

  protected void configureTasks() {
    configureTask_createExtraFiles()
    configureTask_processResources()
    configureTask_Jar()
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

  protected void configureTask_Jar() {
  }

  protected void configureTask_processResources() {
    project.tasks.processResources {
      dependsOn project.tasks.createExtraFiles
      from PluginUtils.getExtraDir(project)
      // Here we exclude any resources/classes that are present in project,
      // but overridden by extra-files.
      // Typical example would be "plugin.xml": this file may be present (or not) in project,
      // so we always generate extra-file "plugin.xml" which should be processed
      // as a resource instead of original "plugin.xml".
      mainSpec.eachFile { FileCopyDetails details ->
        [project.projectDir, project.sourceSets.main.output.classesDir, project.sourceSets.main.output.resourcesDir].each { dir ->
          if(details.file.absolutePath.startsWith(dir.absolutePath)) {
            String relPath = dir.toPath().relativize(details.file.toPath()).toString()
            File extraFile = new File(PluginUtils.getExtraDir(project), relPath)
            if(extraFile.exists()) {
              log.debug 'excluding {}', details.file
              log.debug 'including {}', extraFile
              details.exclude()
            }
          }
        }
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
    if(!project.configurations.findByName('provided'))
      project.configurations {
        provided
        compile.extendsFrom provided
      }
  }

  protected void createExtensions() {
    project.extensions.create('wuff', Config)
  }

  protected void createExtraFiles() {
  }

  protected void createSourceSets() {
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
