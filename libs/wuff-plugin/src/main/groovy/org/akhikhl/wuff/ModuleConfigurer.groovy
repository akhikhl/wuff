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
class ModuleConfigurer {

  protected static final Logger log = LoggerFactory.getLogger(ModuleConfigurer)

  private final Project project
  private final Config effectiveConfig
  private final Expando delegate

  ModuleConfigurer(Project project) {
    this.project = project
    effectiveConfig = project.wuff.effectiveConfig
    log.warn 'Project {} is using eclipse version {}', project.name, effectiveConfig.defaultEclipseVersion
    delegate = new Expando()
    delegate.eclipseMavenGroup = effectiveConfig.defaultEclipseVersion
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

  private EclipseVersionConfig findVersionConfig(String versionString) {
    EclipseVersionConfig versionConfig = effectiveConfig.versionConfigs[versionString]
    if(versionConfig == null)
      log.error 'Eclipse version {} is not configured', versionString
    return versionConfig
  }

  void configureModules(Iterable<String> modules) {
    EclipseVersionConfig versionConfig = findVersionConfig(effectiveConfig.defaultEclipseVersion)
    if(versionConfig != null)
      for(String moduleName in modules)
        configureModule(versionConfig, moduleName)
  }

  private void configureModule(EclipseVersionConfig versionConfig, String moduleName) {
    assert versionConfig != null
    for(String baseVersion in versionConfig.baseVersions) {
      EclipseVersionConfig baseVersionConfig = findVersionConfig(baseVersion)
      if(baseVersionConfig != null)
        configureModule(baseVersionConfig, moduleName)
    }
    List<Closure> closureList = versionConfig.lazyModules[moduleName]
    for(Closure closure in closureList) {
      closure = closure.rehydrate(delegate, closure.owner, closure.thisObject)
      closure.resolveStrategy = Closure.DELEGATE_FIRST
      closure()
    }
  }
}
