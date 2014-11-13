/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff
import org.apache.commons.io.FilenameUtils
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource

import java.nio.file.Paths
import java.util.regex.Matcher
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
        if(f.isDirectory()) {
          privatePackages.add(f.path.replace('/', '.').replace('\\', '.'))
        }
      }
    }
    if(privatePackages) {
      log.info 'Packages {} found in privateLib dependencies of the project {}', privatePackages, project.name
    }
    return privatePackages as List
  }

  static void deleteGeneratedFile(Project project, File file) {
    if (file.exists())
      file.isDirectory() ? file.deleteDir() : file.delete()
    if(file.absolutePath.startsWith(project.projectDir.absolutePath)) {
      File dir = file.parentFile
      while (dir != project.projectDir && dir.exists() && !dir.listFiles()) {
        dir.deleteDir()
        dir = dir.parentFile
      }
    }
  }

  static Set<String> findApplicationClassesInSources(Project project) {
    PluginUtils.findClassesInSources(project, '**/Application.groovy', '**/Application.java')
  }

  /**
   * Finds classes in the sources of the project.
   *
   * @param project - the project being analyzed, not modified.
   * @param sourceMasks - list of Ant-style file patterns
   * @return list of qualified names (package.class) of the found classes or empty list, if classes are not found.
   */
  static Set<String> findClassesInSources(Project project, String... sourceMasks) {
    Set result = new LinkedHashSet()
    project.sourceSets.main.allSource.srcDirs.each { File srcDir ->
      sourceMasks.each { String sourceMask ->
        project.fileTree(srcDir).include(sourceMask).files.each { File sourceFile ->
          String path = Paths.get(srcDir.absolutePath).relativize(Paths.get(sourceFile.absolutePath)).toString()
          result.add(FilenameUtils.removeExtension(path).replace(File.separator, '.'))
        }
      }
    }
    return result
  }

  /**
   * Finds a class in the sources of the project.
   *
   * @param project - the project being analyzed, not modified.
   * @param sourceMasks - list of Ant-style file patterns
   * @return qualified name (package.class) of the found class or null, if class is not found.
   */
  static String findClassInSources(Project project, String... sourceMasks) {
    Set classes = findClassesInSources(project, sourceMasks)
    return classes ? classes[0] : null
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
      if(!(pluginConfig.getClass() in [File, InputSource, InputStream, Reader, String])) {
        pluginConfig = new File(pluginConfig)
      }
      pluginConfig = new XmlParser().parse(pluginConfig)
    }
    def classes = pluginConfig.extension.'**'.findAll({ it.'@class' })*.'@class' + pluginConfig.extension.'**'.findAll({ it.'@contributorClass' })*.'@contributorClass'
    def packages = classes.findResults {
      int dotPos = it.lastIndexOf('.')
      dotPos >= 0 ? it.substring(0, dotPos) : null
    }.unique(false)
    List importPackages = []
    packages.each { String packageName ->
      String packagePath = packageName.replaceAll(/\./, Matcher.quoteReplacement(File.separator))
      if(project.sourceSets.main.allSource.srcDirs.find { new File(it, packagePath).exists() }) {
        log.info 'Found package {} within {}, no import needed', packageName, project.name
      } else {
        log.info 'Did not find package {} within {}, will be imported', packageName, project.name
        importPackages.add(packageName)
      }
    }
    return importPackages
  }

  static Set<String> findPerspectiveClassesInSources(Project project) {
    PluginUtils.findClassesInSources(project, '**/*Perspective.groovy', '**/*Perspective.java', '**/Perspective*.groovy', '**/Perspective*.java')
  }

  static File findPluginSplashFile(Project project) {
    File result = ([project.projectDir] + project.sourceSets.main.resources.srcDirs).findResult { File dir ->
      File f = new File(dir, 'splash.bmp')
      f.exists() ? f : null
    }
    if(result) {
      log.info '{}: Found eclipse plugin splash: {}', project.name, result
    }
    return result
  }

  /**
   * Finds user-defined bundle file.
   * If wuff.generateBundleFiles is true, the file is searched in src/main/bundle.
   * If wuff.generateBundleFiles is false, the file is searched in project root and src/main/resources.
   *
   * @param project project being analyzed (not modified).
   * @param path relative path to user-defined bundle file.
   * @return java.io.File, pointing to user-defined bundle file, or null, if such file does not exist.
   */
  static File findUserBundleFile(Project project, String path) {
    List files = findUserBundleFiles(project, path)
    files ? files[0] : null
  }

  static List<File> findUserBundleFiles(Project project, String path) {
    getSourceBundleDirs(project).findResults { File dir ->
      File f = new File(dir, path)
      f.exists() ? f : null
    }
  }

  static File findUserIntroDir(Project project, String language) {
    String subDir = language ? "nl/$language/" : ''
    findUserBundleFile(project, subDir + 'intro')
  }

  static File findUserIntroHtmlFile(Project project, String language = null) {
    String prefix = language ? "nl/$language/" : ''
    String relPath = "${prefix}intro/welcome.html"
    String relPath2 = "${prefix}intro/welcome.htm"
    File result = getSourceBundleDirs(project).findResult { File dir ->
      File f = new File(dir, relPath)
      if(!f.exists()) {
        f = new File(dir, relPath2)
      }
      f.exists() ? f : null
    }
    if(result)
      log.info '{}: Found user-defined intro html: {}', project.name, result
    return result
  }

  static List<File> findUserLocalizationDirs(Project project) {
    List result = []
    for(File dir in getSourceBundleDirs(project)) {
      File f = new File(dir, 'nl')
      if(f.exists())
        result.addAll f.listFiles({ it.isDirectory() } as FileFilter)
    }
    result
  }

  static File findUserOsgiInfDir(Project project) {
    findUserBundleFile(project, 'OSGI-INF')
  }

  /**
   * Finds eclipse plugin customization file, 'plugin_customization.ini'.
   *
   * @param project project being analyzed (not modified).
   * @return java.io.File, pointing to 'plugin_customization.ini', or null, if such file does not exist.
   */
  static File findUserPluginCustomizationFile(Project project) {
    findUserBundleFile(project, 'plugin_customization.ini')
  }

  /**
   * Collects eclipse plugin configuration localization files, 'plugin*.properties'.
   *
   * @param project project being analyzed, not modified.
   * @return list of strings (absolute paths) to plugin configuration localization files
   * or empty list, if no localization files are found.
   */
  static List<File> findUserPluginLocalizationFiles(Project project) {
    if(getSourceBundleDirs(project).find { new File(it, 'OSGI-INF/l10n').exists() })
      // if locatizationDir exists, OSGi will use it automatically and there's no need for Bundle-Localization files
      return []
    getSourceBundleDirs(project).collectMany { File dir ->
      def localizationFiles = new FileNameFinder().getFileNames(dir.absolutePath, 'plugin*.properties')
      if(localizationFiles) {
        log.debug '{}: Found bundle localization files: {}', project.name, localizationFiles
        return localizationFiles
      }
      []
    }
  }

  /**
   * Finds user-defined plugin.xml file.
   *
   * @param project project being analyzed (not modified).
   * @return java.io.File, pointing to plugin.xml, or null, if such file does not exist.
   */
  static File findUserPluginXmlFile(Project project) {
    findUserBundleFile(project, 'plugin.xml')
  }

  static Set<String> findViewClassesInSources(Project project) {
    PluginUtils.findClassesInSources(project, '**/*View.groovy', '**/*View.java', '**/View*.groovy', '**/View*.java')
  }

  static String getEclipseApplicationId(Project project) {
    String result
    if(project.effectivePluginXml)
      result = project.effectivePluginXml.extension.find({ it.'@point' == 'org.eclipse.core.runtime.applications' })?.'@id'
    if(result)
      result = "${project.name}.${result}"
    return result
  }

  static boolean hasEclipseIntro(Project project) {
    boolean result
    if(project.effectivePluginXml)
      result = project.effectivePluginXml.extension.find({ it.'@point' == 'org.eclipse.ui.intro' })?.intro?.'@id' as boolean
    else
      result = PluginUtils.findUserIntroHtmlFile(project) != null
    result
  }

  static String getEclipseIntroId(Project project) {
    String result
    if(project.effectivePluginXml)
      result = project.effectivePluginXml.extension.find({ it.'@point' == 'org.eclipse.ui.intro' })?.intro?.'@id'
    if(result)
      result = "${project.name}.$result"
    return result
  }

  static String getEclipseProductId(Project project) {
    String result
    if(project.effectivePluginXml)
      result = project.effectivePluginXml.extension.find({ it.'@point' == 'org.eclipse.core.runtime.products' })?.'@id'
    if(result)
      result = "${project.name}.$result"
    return result
  }

  /**
   * Returns effective bundle file.
   * If wuff.generateBundleFiles is true, the file is located in the project root.
   * If wuff.generateBundleFiles is false, the file is in the project root or src/main/resources.
   * The returned file may exist or not.
   *
   * @param project project being analyzed (not modified).
   * @param path relative path to user-defined bundle file.
   * @return java.io.File, pointing to effective bundle file.
   */
  static File getEffectiveBundleFile(Project project, String path) {
    File result
    if(project.effectiveWuff.generateBundleFiles)
      result = new File(project.projectDir, path)
    else {
      result = ([project.projectDir] + project.sourceSets.main.resources.srcDirs).findResult { File dir ->
        File f = new File(dir, path)
        f.exists() ? f : null
      }
      if(!result)
        result = new File(project.projectDir, path)
    }
    if(result != null) {
      log.debug '{}: Effective file: {}', project, result
    }
    return result
  }

  /**
   * Finds effective MANIFEST.MF file.
   *
   * @param project project being analyzed (not modified).
   * @return java.io.File, pointing to MANIFEST.MF, or null, if such file does not exist.
   */
  static File getEffectiveManifestFile(Project project) {
    getEffectiveBundleFile(project, 'META-INF/MANIFEST.MF')
  }

  static List<File> getEffectivePluginLocalizationFiles(Project project) {
    if(project.effectiveWuff.generateBundleFiles)
      getGeneratedPluginLocalizationFiles(project)
    else
      findUserPluginLocalizationFiles(project)
  }

  static File getEffectivePluginCustomizationFile(Project project) {
    getEffectiveBundleFile(project, 'plugin_customization.ini')
  }

  /**
   * Finds effective plugin.xml file.
   *
   * @param project project being analyzed (not modified).
   * @return java.io.File, pointing to plugin.xml, or null, if such file does not exist.
   */
  static File getEffectivePluginXmlFile(Project project) {
    getEffectiveBundleFile(project, 'plugin.xml')
  }

  static File getEquinoxLauncherFile(Project project) {
    return project.configurations.runtime.find { getPluginName(it.name) == equinoxLauncherPluginName }
  }

  static File getGeneratedBundleFile(Project project, String path) {
    new File(project.projectDir, path)
  }

  static File getGeneratedIntroContentXmlFile(Project project, String language) {
    new File(getGeneratedIntroDir(project, language), 'introContent.xml')
  }

  static File getGeneratedIntroDir(Project project, String language) {
    String subdir = language ? "nl/$language/intro" : 'intro'
    new File(project.projectDir, subdir)
  }

  static List<File> getGeneratedPluginLocalizationFiles(Project project) {
    findUserPluginLocalizationFiles(project).collect { new File(project.projectDir, it.name) }
  }

  static File getGeneratedPluginCustomizationFile(Project project) {
    getGeneratedBundleFile(project, 'plugin_customization.ini')
  }

  static File getGeneratedPluginXmlFile(Project project) {
    getGeneratedBundleFile(project, 'plugin.xml')
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

  static List<File> getSourceBundleDirs(Project project) {
    if(project.effectiveWuff.generateBundleFiles) {
      def bundleSourceDir = project.effectiveWuff.bundleSourceDir
      if (!(bundleSourceDir instanceof File))
        bundleSourceDir = new File(bundleSourceDir)
      if (!bundleSourceDir.isAbsolute())
        bundleSourceDir = new File(project.projectDir, bundleSourceDir.getPath())
      return bundleSourceDir.exists() ? [ bundleSourceDir ] : []
    }
    ([ project.projectDir ] + project.sourceSets.main.resources.srcDirs).findAll { it.exists() }
  }

  static File getWrappedLibsDir(Project project) {
    return new File(project.buildDir, 'wrappedLibs')
  }
}
