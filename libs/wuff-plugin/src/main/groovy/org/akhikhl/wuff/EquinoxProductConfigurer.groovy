/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip

/**
 *
 * @author akhikhl
 */
class EquinoxProductConfigurer {

  private final Project project
  private final Map product
  private final String platform
  private final String arch
  private final String language
  private final String productFileSuffix
  private final String productTaskSuffix
  private final Configuration productConfig
  private final List launchers
  private final File jreFolder
  private final File productOutputDir

  EquinoxProductConfigurer(Project project, String productConfigPrefix, Map product) {

    this.project = project
    this.product = product

    platform = product.platform ?: PlatformConfig.current_os
    if(!PlatformConfig.supported_oses.contains(platform))
      log.error 'Platform {} is not supported', platform

    arch = product.arch ?: PlatformConfig.current_arch
    if(!PlatformConfig.supported_archs.contains(arch))
      log.error 'Architecture {} is not supported', arch

    language = product.language ?: ''
    if(language && !PlatformConfig.supported_languages.contains(language))
      log.error 'Language {} is not supported', language

    if(product.fileSuffix)
      productFileSuffix = product.fileSuffix
    else {
      String productNamePrefix = product.name ? "${product.name}-" : ''
      String languageSuffix = language ? "-${language}" : ''
      productFileSuffix = "${productNamePrefix}${platform}-${arch}${languageSuffix}"
    }

    if(product.taskSuffix)
      productTaskSuffix = product.taskSuffix
    else {
      String productNamePrefix = product.name ? "${product.name}_" : ''
      String languageSuffix = language ? "_${language}" : ''
      productTaskSuffix = "${productNamePrefix}${platform}_${arch}${languageSuffix}"
    }

    String configName
    if(product.configName)
      configName = product.configName
    else {
      String productNamePrefix = product.name ? "${product.name}_" : ''
      String languageSuffix = language ? "_${language}" : ''
      configName = "${productConfigPrefix}${productNamePrefix}${platform}_${arch}${languageSuffix}"
    }

    productConfig = project.configurations.findByName(configName)

    if(product.launchers)
      launchers = product.launchers
    else if(product.launcher)
      launchers = [ product.launcher ]
    else if(product.platform == 'windows')
      launchers = [ 'windows' ]
    else
      launchers = [ 'shell' ]

    File jreFolder = null
    if(product.jre) {
      def file = new File(product.jre)
      if(!file.isAbsolute())
        file = new File(project.projectDir, file.path)
      if(file.exists()) {
        if(file.isDirectory())
          jreFolder = file
        else
          project.logger.warn 'the specified JRE path {} represents a file (it should be a folder)', file.absolutePath
      }
      else
        project.logger.warn 'JRE folder {} does not exist', file.absolutePath
    }
    this.jreFolder = jreFolder

    String productOutputDirName = "${project.name}-${project.version}"
    if(productFileSuffix)
      productOutputDirName += '-' + productFileSuffix
    productOutputDir = new File(PluginUtils.getProductOutputBaseDir(project), productOutputDirName)
  }

  void configure() {
    configureBuildTask()
    configureArchiveTask()
  }

  void configureBuildTask() {

    project.task("buildProduct_${productTaskSuffix}") { task ->

      dependsOn project.tasks.jar
      dependsOn project.tasks.wrapLibs
      project.tasks.build.dependsOn task

      inputs.files { project.configurations.runtime.files }

      if(productConfig)
        inputs.files productConfig.files

      if(jreFolder)
        inputs.dir jreFolder

      outputs.dir productOutputDir

      doLast {
        // key is plugin name, value is complete launch entry for configuration
        def bundleLaunchList = [:]

        def addBundle = { File file ->
          String pluginName = PluginUtils.getPluginName(file.name)
          if(bundleLaunchList.containsKey(pluginName))
            return
          String launchOption = ''
          if(pluginName == 'org.eclipse.equinox.ds' || pluginName == 'org.eclipse.equinox.common')
            launchOption = '@2:start'
          else if(pluginName == 'org.eclipse.core.runtime' || pluginName == 'jersey-core')
            launchOption = '@start'
          if(pluginName != PluginUtils.osgiFrameworkPluginName && !pluginName.startsWith(PluginUtils.equinoxLauncherPluginName))
            bundleLaunchList[pluginName] = "reference\\:file\\:${file.name}${launchOption}"
          project.copy {
            from file
            into new File(productOutputDir, 'plugins')
            // need to rename them to ensure that platform-specific launcher fragments are automatically found
            if(file.name.startsWith(PluginUtils.equinoxLauncherPluginName))
              rename PluginUtils.eclipsePluginMask, '$1_$2'
          }
        }

        addBundle project.tasks.jar.archivePath

        File wrappedLibsDir = PluginUtils.getWrappedLibsDir(project)
        if(wrappedLibsDir.exists())
          wrappedLibsDir.eachFileMatch(~/.*\.jar/) { addBundle it }

        project.configurations.runtime.each {
          if(ManifestUtils.isBundle(project, it) && !ProjectUtils.findFileInProducts(project, it))
            addBundle it
        }

        productConfig?.each {
          if(ManifestUtils.isBundle(project, it))
            addBundle it
        }

        bundleLaunchList = bundleLaunchList.sort()

        File configFile = new File(productOutputDir, 'configuration/config.ini')
        configFile.parentFile.mkdirs()
        configFile.withPrintWriter { PrintWriter configWriter ->
          String eclipseApplicationId = PluginUtils.getEclipseApplicationId(project)
          if(eclipseApplicationId)
            configWriter.println "eclipse.application=$eclipseApplicationId"
          String eclipseProductId = PluginUtils.getEclipseProductId(project)
          if(eclipseProductId)
            configWriter.println "eclipse.product=$eclipseProductId"
          File osgiFrameworkFile = PluginUtils.getOsgiFrameworkFile(project)
          configWriter.println "osgi.framework=file\\:plugins/${osgiFrameworkFile.name}"
          configWriter.println 'osgi.bundles.defaultStartLevel=4'
          configWriter.println 'osgi.bundles=' + bundleLaunchList.values().join(',\\\n  ')
          ([project.projectDir] + project.sourceSets.main.resources.srcDirs).find { File srcDir ->
            if(new File(srcDir, 'splash.bmp').exists()) {
              configWriter.println "osgi.splashPath=file\\:plugins/${project.tasks.jar.archivePath.name}"
              true
            }
          }
        }

        File equinoxLauncherFile = PluginUtils.getEquinoxLauncherFile(project)
        String equinoxLauncherName = 'plugins/' + equinoxLauncherFile.name.replaceAll(PluginUtils.eclipsePluginMask, '$1_$2')

        if(jreFolder)
          project.copy {
            from jreFolder
            into new File(productOutputDir, 'jre')
          }

        def launchParameters = project.products.launchParameters.clone()

        ([project.projectDir] + project.sourceSets.main.resources.srcDirs).find { File srcDir ->
          File splashFile = new File(srcDir, 'splash.bmp')
          if(splashFile.exists()) {
            launchParameters.add '-showSplash'
            true
          }
        }

        if(language) {
          launchParameters.add '-nl'
          launchParameters.add language
        }

        launchParameters = launchParameters.join(' ')
        if(launchParameters)
          launchParameters = ' ' + launchParameters

        if(launchers.contains('shell')) {
          def javaLocation = ''
          if(jreFolder)
            javaLocation = 'SOURCE="${BASH_SOURCE[0]}"\n' +
              'while [ -h "$SOURCE" ]; do # resolve $SOURCE until the file is no longer a symlink\n' +
              'DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )\n' +
              'SOURCE="$(readlink "$SOURCE")\n' +
              '[[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE" # if $SOURCE was a relative symlink, we need to resolve it relative to the path where the symlink file was located\n' +
              'done\n' +
              'DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )\n' +
              '${DIR}/jre/bin/'
          File launchScriptFile = new File(productOutputDir, "${project.name}.sh")
          launchScriptFile.text = "#!/bin/bash\n${javaLocation}java -jar $equinoxLauncherName$launchParameters \"\$@\""
          launchScriptFile.setExecutable(true)
        }

        if(launchers.contains('windows')) {
          def javaLocation = ''
          if(jreFolder)
            javaLocation = '%~dp0\\jre\\bin\\'
          File launchScriptFile = new File(productOutputDir, "${project.name}.bat")
          launchScriptFile.text = "@start /min cmd /c ${javaLocation}java.exe -jar $equinoxLauncherName$launchParameters %*"
          javaLocation = ''
          if(jreFolder)
            javaLocation = 'jre\\bin\\'
          launchScriptFile = new File(productOutputDir, "${project.name}.vbs")
          launchScriptFile.text = "Set shell = CreateObject(\"WScript.Shell\")\r\nshell.Run \"cmd /c ${javaLocation}java.exe -jar $equinoxLauncherName$launchParameters\", 0"
        }

        String versionFileName = 'VERSION'
        String lineSep = '\n'
        if(platform == 'windows' || launchers.contains('windows')) {
          versionFileName += '.txt'
          lineSep = '\r\n'
        }
        new File(productOutputDir, versionFileName).text = "product: ${project.name}" + lineSep +
          "version: ${project.version}" + lineSep +
          "platform: $platform" + lineSep +
          "architecture: $arch" + lineSep +
          "language: ${language ?: 'en'}" + lineSep
      } // doLast
    } // buildProduct_xxx task
  } // configureBuildTask

  void configureArchiveTask() {

    if(!project.products.archiveProducts)
      return

    def archiveType = launchers.contains('windows') ? Zip : Tar

    project.task("archiveProduct_${productTaskSuffix}", type: archiveType) {
      dependsOn "buildProduct_${productTaskSuffix}"
      project.tasks.build.dependsOn it
      from productOutputDir, { into project.name }
      def addedFiles = new HashSet()
      def addFileToArchive = { f, Closure closure ->
        File file = f instanceof File ? f : new File(f)
        if(!file.isAbsolute())
          file = new File(project.projectDir, file.path)
        if(!addedFiles.contains(file.absolutePath)) {
          addedFiles.add(file.absolutePath)
          if(file.exists())
            from file, closure
          else
            project.logger.info 'additional file/folder {} does not exist', file.absolutePath
        }
      }
      if(project.products.additionalFilesToArchive)
        for(def f in project.products.additionalFilesToArchive)
          addFileToArchive f, { into project.name }
      if(product.archiveFiles)
        for(def f in product.archiveFiles)
          addFileToArchive f, { into project.name }
      addFileToArchive 'CHANGES', { into project.name }
      addFileToArchive 'README', { into project.name }
      addFileToArchive 'LICENSE', { into project.name }
      if(platform == 'windows')
        addFileToArchive 'appicon.ico', {
          into project.name
          rename 'appicon.ico', "${project.name}.ico"
        }
      else
        addFileToArchive 'appicon.xpm', {
          into project.name
          rename 'appicon.xpm', "${project.name}.xpm"
        }
      destinationDir = productOutputDir.parentFile
      classifier = productFileSuffix
      if(archiveType == Tar) {
        extension = 'tar.gz'
        compression = Compression.GZIP
      }
      doLast {
        ant.checksum file: it.archivePath
      }
    } // archiveTaskName
  } // configureArchiveTask
}
