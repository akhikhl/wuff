/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import java.nio.file.Paths

import groovy.transform.CompileStatic
import groovy.util.XmlParser

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.xml.sax.InputSource

/**
 * Eclipse Plugin utils
 *
 * @author akhikhl
 */
final class PluginUtils {

  private static final Logger log = LoggerFactory.getLogger(PluginUtils)

  static final String eclipsePluginMask = /([\da-zA-Z_.-]+?)-((\d+\.)+[\da-zA-Z_.-]*)/
  static final String osgiFrameworkPluginName = 'org.eclipse.osgi'
  static final String equinoxLauncherPluginName = 'org.eclipse.equinox.launcher'

  /**
   * Collects eclipse plugin configuration localization files, 'plugin*.properties'.
   *
   * @param project project being analyzed, not modified.
   * @return list of strings (absolute paths) to plugin configuration localization files
   * or empty list, if no localization files are found.
   */
  static List<String> collectPluginLocalizationFiles(Project project) {
    ([project.projectDir] + project.sourceSets.main.resources.srcDirs).findResult { File dir ->
      if(dir.exists()) {
        File locatizationDir = new File(dir, 'OSGI-INF/l10n')
        // if locatizationDir exists, it will be used by OSGi automatically
        // and there's no need for Bundle-Localization
        if(!locatizationDir.exists()) {
          List<String> localizationFiles = new FileNameFinder().getFileNames(dir.absolutePath, 'plugin*.properties')
          if(localizationFiles) {
            log.info '{}: Found bundle localization files: {}', project.name, localizationFiles
            return localizationFiles
          }
        }
      }
      null
    } ?: []
  }

  /**
   * Collects list of privateLib packages in the given project.
   * The function iterates over files of privateLib configuration in the given project.
   * Each file is treated as zip-tree, from which package names are extracted.
   *
   * @param project project being analyzed, not modified.
   * @return list of strings (java package names).
   */
  static List<String> collectPrivateLibPackages(Project project) {
    Set privatePackages = new LinkedHashSet()
    project.configurations.privateLib.files.each { File lib ->
      project.zipTree(lib).visit { f ->
        if(f.isDirectory())
          privatePackages.add(f.path.replaceAll('/', '.'))
      }
    }
    if(privatePackages)
      log.info 'Packages {} found in privateLib dependencies of the project {}', privatePackages, project.name
    return privatePackages as List
  }

  /**
   * Finds bundle activator.
   *
   * @param project project being analyzed, not modified.
   * @return qualified name (package.class) of the bundle activator, if present.
   */
  static String findBundleActivator(Project project) {
    String activator
    project.sourceSets.main.allSource.srcDirs.findResult { File srcDir ->
      project.fileTree(srcDir).include('**/Activator.groovy').files.findResult { File activatorSourceFile ->
        activator = Paths.get(srcDir.absolutePath).relativize(Paths.get(activatorSourceFile.absolutePath)).toString()
        activator = activator.substring(0, activator.length() - 7) // remove '.groovy' file extension
        activator = activator.replaceAll('/', '.') // convert to package.class
      }
      if(!activator)
        project.fileTree(srcDir).include('**/Activator.java').files.findResult { File activatorSourceFile ->
          activator = Paths.get(srcDir.absolutePath).relativize(Paths.get(activatorSourceFile.absolutePath)).toString()
          activator = activator.substring(0, activator.length() - 5) // remove '.java' file extension
          activator = activator.replaceAll('/', '.') // convert to package.class
        }
      activator
    }
    if(activator)
      log.info '{}: Found bundle activator: {}', project.name, activator
    return activator
  }

  static Node findGeneratedPluginConfig(Project project) {
    File f = getGeneratedPluginConfigFile(project)
    f.exists() ? new XmlParser().parse(f) : null
  }

  /**
   * Finds list of import-packages in the given plugin configuration file, 'plugin.xml'.
   *
   * @param project project being analyzed, not modified.
   * @param pluginConfig could have one of the following types: File, InputSource, InputStream, Reader, String.
   * Should resolve to 'plugin.xml' file in the file system.
   * @return list of qualified names of java-packages imported by elements in 'plugin.xml'.
   */
  static List<String> findImportPackagesInPluginConfigFile(Project project, pluginConfig) {
    log.info 'Analyzing import packages in {}', pluginConfig
    if(!(pluginConfig instanceof Node)) {
      if(!(pluginConfig.getClass() in [File, InputSource, InputStream, Reader, String]))
        pluginConfig = new File(pluginConfig)
      pluginConfig = new XmlParser().parse(pluginConfig)
    }
    def classes = pluginConfig.extension.'**'.findAll({ it.'@class' })*.'@class' + pluginConfig.extension.'**'.findAll({ it.'@contributorClass' })*.'@contributorClass'
    def packages = classes.collect { it.substring(0, it.lastIndexOf('.')) }.unique(false)
    List importPackages = []
    packages.each { String packageName ->
      String packagePath = packageName.replaceAll(/\./, '/')
      if(project.sourceSets.main.allSource.srcDirs.find { new File(it, packagePath).exists() })
        log.info 'Found package {} within {}, no import needed', packageName, project.name
      else {
        log.info 'Did not find package {} within {}, will be imported', packageName, project.name
        importPackages.add(packageName)
      }
    }
    return importPackages
  }

  /**
   * Finds eclipse plugin configuration file, 'plugin.xml'.
   *
   * @param project project being analyzed, not modified.
   * @return groovy.util.Node, containing DOM-tree for 'plugin.xml', or null, if configuration file does not exist.
   */
  static Node findPluginConfig(Project project) {
    File f = findPluginConfigFile(project)
    f ? new XmlParser().parse(f) : null
  }

  /**
   * Finds eclipse plugin configuration file, 'plugin.xml'.
   *
   * @param project project being analyzed, not modified.
   * @return java.io.File, pointing to 'plugin.xml', or null, if configuration file does not exist.
   */
  static File findPluginConfigFile(Project project) {
    File result = ([project.projectDir] + project.sourceSets.main.resources.srcDirs).findResult { File dir ->
      File f = new File(dir, 'plugin.xml')
      f.exists() ? f : null
    }
    if(result)
      log.info '{}: Found eclipse plugin configuration: {}', project.name, result
    return result
  }

  static String getEclipseApplicationId(Project project) {
    String result
    def pluginConfig = findPluginConfig(project)
    if(pluginConfig == null)
      pluginConfig = findGeneratedPluginConfig(project)
    if(pluginConfig != null)
      result = pluginConfig.extension.find({ it.'@point' == 'org.eclipse.core.runtime.applications' })?.'@id'
    if(result)
      result = "${project.name}.${result}"
    return result
  }

  static String getEclipseIntroId(Project project) {
    String result
    def pluginConfig = findPluginConfig(project)
    if(pluginConfig == null)
      pluginConfig = findGeneratedPluginConfig(project)
    if(pluginConfig != null)
      result = pluginConfig.extension.find({ it.'@point' == 'org.eclipse.ui.intro' })?.intro?.'@id'
    if(result)
      result = "${project.name}.$result"
    return result
  }

  static String getEclipseProductId(Project project) {
    String result
    def pluginConfig = findPluginConfig(project)
    if(pluginConfig == null)
      pluginConfig = findGeneratedPluginConfig(project)
    if(pluginConfig != null)
      result = pluginConfig.extension.find({ it.'@point' == 'org.eclipse.core.runtime.products' })?.'@id'
    if(result)
      result = "${project.name}.$result"
    return result
  }

  static File getEquinoxLauncherFile(Project project) {
    return project.configurations.runtime.find { getPluginName(it.name) == equinoxLauncherPluginName }
  }

  static File getGeneratedDir(Project project) {
    new File(project.buildDir, 'generated')
  }

  static File getGeneratedPluginConfigFile(Project project) {
    new File(getGeneratedDir(project), 'plugin.xml')
  }

  static File getOsgiFrameworkFile(Project project) {
    return project.configurations.runtime.find { getPluginName(it.name) == osgiFrameworkPluginName }
  }

  static String getPluginName(String fileName) {
    return fileName.replaceAll(eclipsePluginMask, '$1')
  }

  static File getProductOutputBaseDir(Project project) {
    return new File(project.buildDir, 'output')
  }

  static File getWrappedLibsDir(Project project) {
    return new File(project.buildDir, 'wrappedLibs')
  }
}

