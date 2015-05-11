/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.akhikhl.unpuzzle.PlatformConfig
import org.akhikhl.unpuzzle.eclipse2maven.EclipseDeployer
import org.apache.commons.configuration.plist.XMLPropertyListConfiguration
import org.eclipse.pde.internal.swt.tools.IconExe
import org.gradle.api.GradleException
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
    else if(platform == 'windows')
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
      } else
        project.logger.warn 'JRE folder {} does not exist', file.absolutePath
    }
    this.jreFolder = jreFolder

    productOutputDir = PluginUtils.getProductOutputDir(project, product)
  }

  void configure() {
    configureBuildTask()
    configureArchiveTask()
  }

  void configureBuildTask() {
    project.task(PluginUtils.getProductBuildTaskName(product)) { task ->
      group = 'wuff'
      if(language)
        description = "builds product for platform $platform, architecture $arch and language $language"
      else
        description = "builds product for platform $platform and architecture $arch"

      dependsOn project.tasks.jar
      dependsOn project.tasks.wrapLibs
      project.tasks.build.dependsOn task

      inputs.files { project.tasks.jar.archivePath }
      inputs.files { project.configurations.runtime }

      if(productConfig)
        inputs.files productConfig.files

      if(jreFolder)
        inputs.dir jreFolder

      outputs.dir productOutputDir

      doLast {
        writeConfigIni()

        def launchParameters = project.products.launchParameters.clone()

        if(PluginUtils.findPluginSplashFile(project))
          launchParameters.add '-showSplash'

        if(language) {
          launchParameters.add '-nl'
          launchParameters.add language
        }

        if(project.products.nativeLauncher) {
          writeNativeLauncher(launchParameters, project.products.jvmArgs.clone())
        } else {
          if(launchers.contains('shell'))
            writeShellLaunchFile(launchParameters, project.products.jvmArgs.clone())

          if(launchers.contains('windows'))
            writeWindowsLaunchFile(launchParameters, project.products.jvmArgs.clone())
        }

        writeVersionFile()

        if(jreFolder)
          project.copy {
            from jreFolder
            into new File(productOutputDir, 'jre')
          }
      } // doLast
    } // buildProduct_xxx task
  } // configureBuildTask

  void configureArchiveTask() {

    if(!project.products.archiveProducts)
      return

    def archiveType = launchers.contains('windows') ? Zip : Tar

    project.task(PluginUtils.getArchiveProductTaskName(product), type: archiveType) {
      group = 'wuff'
      if(language)
        description = "archives product for platform $platform, architecture $arch and language $language"
      else
        description = "archives product for platform $platform and architecture $arch"

      dependsOn PluginUtils.getProductBuildTaskName(product)
      project.tasks.build.dependsOn it

      baseName = project.name
      classifier = PluginUtils.getProductFileSuffix(product)
      destinationDir = productOutputDir.parentFile
      if(archiveType == Tar) {
        extension = 'tar.gz'
        compression = Compression.GZIP
      }

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
        for (def f in project.products.additionalFilesToArchive)
          addFileToArchive f, { into project.name }
      if(product.archiveFiles)
        for (def f in product.archiveFiles)
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

      doLast {
        ant.checksum file: it.archivePath
      }
    } // archiveTaskName
  } // configureArchiveTask

  void writeConfigIni() {
    // key is plugin name, value is complete launch entry for configuration
    def bundleLaunchList = [ : ]

    def addBundle = { File file ->
      String pluginName = PluginUtils.getPluginName(file.name)
      if(bundleLaunchList.containsKey(pluginName))
        return
      String launchOption = ''
      if(pluginName == 'org.eclipse.equinox.ds' || pluginName == 'org.eclipse.equinox.common')
        launchOption = '@2:start'
      else if(pluginName == 'org.eclipse.core.runtime' || pluginName == 'jersey-core' || project.products.autostartedBundles.contains(pluginName))
        launchOption = '@start'
      if(pluginName != PluginUtils.osgiFrameworkPluginName && !pluginName.startsWith(PluginUtils.equinoxLauncherPluginName))
        bundleLaunchList[pluginName] = "reference\\:file\\:${file.name}${launchOption}"

      if(file.name.startsWith(PluginUtils.equinoxLauncherPlatformSpecifiPluginNamePrefix)) {
        String outputDirName = file.name.replaceAll("${PluginUtils.eclipsePluginMask}.jar", '$1_$2')
        project.copy {
          from project.zipTree(file)
          into new File(productOutputDir, "plugins/$outputDirName")
        }
      } else {
        project.copy {
          from file
          into new File(productOutputDir, 'plugins')
          // need to rename them to ensure that platform-specific launcher fragments are automatically found
          if(file.name.startsWith(PluginUtils.equinoxLauncherPluginName))
            rename PluginUtils.eclipsePluginMask, '$1_$2'
        }
      }
    }

    addBundle project.tasks.jar.archivePath

    File wrappedLibsDir = PluginUtils.getWrappedLibsDir(project)
    if(wrappedLibsDir.exists())
      wrappedLibsDir.eachFileMatch(~/.*\.jar/) { addBundle it }

    project.configurations.runtime.each { file ->
      if(ManifestUtils.isBundle(project, file) && !(project.configurations.provided.find { it == file }))
        addBundle file
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
      if(project.ext.has('osgiExecutionEnvironment'))
        configWriter.println "org.osgi.framework.executionenvironment=${project.ext.osgiExecutionEnvironment}"
      configWriter.println 'osgi.bundles.defaultStartLevel=4'
      configWriter.println 'osgi.bundles=' + bundleLaunchList.values().join(',\\\n  ')
      if(PluginUtils.findPluginSplashFile(project))
        configWriter.println "osgi.splashPath=file\\:plugins/${project.tasks.jar.archivePath.name}"

      List osgiExtensionNames = PluginUtils.getOsgiExtensionFiles(project).collect { it.name };

      if(!osgiExtensionNames.empty) {
        configWriter.println "osgi.framework.extensions=reference\\:file\\:" + osgiExtensionNames.join(',\\\n  ')
      }
    }
  }

  void writeShellLaunchFile(List<String> launchParameters, List<String> jvmArgs) {
    String launchParametersStr = launchParameters.join(' ')
    if(launchParametersStr)
      launchParametersStr = ' ' + launchParametersStr
    File equinoxLauncherFile = PluginUtils.getEquinoxLauncherFile(project)
    String equinoxLauncherName = 'plugins/' + equinoxLauncherFile.name.replaceAll(PluginUtils.eclipsePluginMask, '$1_$2')
    String javaLocation = ''
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

    if(platform == 'macosx')
      jvmArgs += '-XstartOnFirstThread'
    String jvmArgsStr = jvmArgs.join(' ')
    launchScriptFile.text = "#!/bin/bash\n${javaLocation}java ${jvmArgsStr} -jar $equinoxLauncherName$launchParametersStr \"\$@\""
    launchScriptFile.setExecutable(true)
  }

  void writeVersionFile() {
    String lineSep = '\n'
    if(platform == 'windows' || launchers.contains('windows'))
      lineSep = '\r\n'
    new File(productOutputDir, 'VERSION').text = "product: ${project.name}" + lineSep +
            "version: ${project.version}" + lineSep +
            "platform: $platform" + lineSep +
            "architecture: $arch" + lineSep +
            "language: ${language ?: 'en'}" + lineSep
  }

  void writeWindowsLaunchFile(List<String> launchParameters, List<String> jvmArgs) {
    String launchParametersStr = launchParameters.join(' ')
    if(launchParametersStr)
      launchParametersStr = ' ' + launchParametersStr

    String jvmArgsStr = jvmArgs.join(' ')
    if(jvmArgsStr)
      jvmArgsStr = ' ' + jvmArgsStr

    File equinoxLauncherFile = PluginUtils.getEquinoxLauncherFile(project)
    String equinoxLauncherName = 'plugins/' + equinoxLauncherFile.name.replaceAll(PluginUtils.eclipsePluginMask, '$1_$2')
    String javaLocation = ''
    if(jreFolder)
      javaLocation = '%~dp0\\jre\\bin\\'
    File launchScriptFile = new File(productOutputDir, "${project.name}.bat")
    String scriptText = "${javaLocation}java.exe $jvmArgsStr -jar $equinoxLauncherName$launchParametersStr %*"
    if(PluginUtils.findPluginSplashFile(project))
      scriptText = '@start /min cmd /c ' + scriptText
    launchScriptFile.text = scriptText
  }

  void writeNativeLauncher(List<String> launchParameters, List<String> jvmArgs) {
    File baseLocation = getDeltapackDir();
    File[] equinoxExecutablePlugins = new File(baseLocation, 'features').listFiles({
      it.name.matches ~/^org\.eclipse\.equinox\.executable_(.+)$/
    } as FileFilter)
    if(!equinoxExecutablePlugins)
      throw new GradleException("Could not build feature: equinox executable not found in ${new File(baseLocation, 'features')}")
    File equinoxExecutablePlugin = equinoxExecutablePlugins[0]

    Platform platform = createPlatform()
    File source = new File(equinoxExecutablePlugin, "bin/${platform.ws}/${platform.os}/${platform.arch}")

    project.copy {
      from source
      exclude "**/eclipsec*"
      into productOutputDir
    }

    platform.applyPlatformSpecificCustomization()

    if(!launchParameters.empty || !jvmArgs.empty){
      customizeIniFile(platform, launchParameters, jvmArgs)
    }
  }

  private void customizeIniFile(Platform platform, List<String> launchParameters, List<String> jvmArgs){
    File iniFile = platform.iniFile

    StringBuffer buf = new StringBuffer()
    if(iniFile.exists()){
      buf.append(iniFile.getText("UTF-8"))
    }

    String launchParametersString = launchParameters.join("\n")
    if(!launchParametersString.empty){
      buf.insert(0, "${launchParametersString}\n")
    }

    if(!jvmArgs.empty){
      if(!buf.contains("-vmargs")){
        buf.append("-vmargs\n")
      }

      buf.append(jvmArgs.join("\n"))
      buf.append("\n")
    }

    iniFile.setText(buf.toString(), "UTF-8")
  }

  private File getDeltapackDir() {
    def unpuzzle = project.effectiveUnpuzzle
    def eclipseSource = project.effectiveUnpuzzle.selectedVersionConfig.sources.find { it.url.contains('delta') }
    EclipseDeployer.getUnpackDir(unpuzzle.unpuzzleDir, eclipseSource)
  }

  Platform createPlatform(){
    if(platform == "windows") {
      return new WindowsPlatform()
    }

    if(platform == "linux") {
      return new LinuxPlatform()
    }

    if(platform == "macosx") {
      return new MacOSXPlatform()
    }

    throw new IllegalArgumentException("Unknow platform '$platform'")
  }

  private abstract class Platform {
    abstract String getOs()
    abstract String getWs()
    abstract File getIniFile()
    abstract void applyPlatformSpecificCustomization()

    String getArch() {
      if(EquinoxProductConfigurer.this.arch == "x86_32") {
        return "x86"
      }

      if(EquinoxProductConfigurer.this.arch == "x86_64") {
        return "x86_64"
      }

      throw new IllegalArgumentException("Unknow arch '$platform'")
    }
  }

  private class WindowsPlatform extends Platform {
    private File destinationLauncher = new File(productOutputDir, "${project.name}.exe")
    private File iniFile = new File(productOutputDir, "${project.name}.ini")

    @Override
    String getOs() {
      return "win32"
    }

    @Override
    String getWs() {
      return "win32"
    }

    @Override
    File getIniFile() {
      return iniFile
    }

    @Override
    void applyPlatformSpecificCustomization() {
      new File(productOutputDir, "launcher.exe").renameTo(destinationLauncher)

      if(project.products.windowsIco != null) {
        IconExe.main([ destinationLauncher.absolutePath, project.products.windowsIco.absolutePath ] as String[]);
      } else {
        def icons = [
                project.products.windowsBmp_16_8b,
                project.products.windowsBmp_16_32b,
                project.products.windowsBmp_32_8b,
                project.products.windowsBmp_32_32b,
                project.products.windowsBmp_48_8b,
                project.products.windowsBmp_48_32b,
        ].findAll { it != null }.collect { it.absolutePath }

        if(!icons.isEmpty()) {
          IconExe.main(([ destinationLauncher.absolutePath ] + icons) as String[])
        }
      }
    }
  }

  private class MacOSXPlatform extends Platform {
    private File eclipseApp = new File(productOutputDir, "Eclipse.app");
    private File executable = new File(eclipseApp, "Contents/MacOS/${project.name}")
    private File targetApp = new File(productOutputDir, "${project.name}.app")
    private File iniFile = new File(targetApp, "Contents/MacOS/${project.name}.ini")

    @Override
    String getOs() {
      return "macosx"
    }

    @Override
    String getWs() {
      return "cocoa"
    }

    @Override
    File getIniFile() {
      return iniFile
    }

    @Override
    void applyPlatformSpecificCustomization() {
      new File(eclipseApp, "Contents/MacOS/launcher").renameTo(executable)

      executable.setExecutable(true, false)

      eclipseApp.renameTo(targetApp);

      File plistFile = new File(targetApp, "Contents/Info.plist")
      XMLPropertyListConfiguration plistConfiguration = new XMLPropertyListConfiguration()
      plistConfiguration.load(plistFile)
      plistConfiguration.rootNode.getChildren("CFBundleExecutable")[0].setValue(project.name)
      plistConfiguration.rootNode.getChildren("CFBundleIdentifier")[0].setValue([project.group, project.name, project.version].collect {it!=null && it!=""}.join(":"))
      plistConfiguration.rootNode.getChildren("CFBundleName")[0].setValue("${project.name}")
      plistConfiguration.rootNode.getChildren("CFBundleVersion")[0].setValue("${project.version}")
      plistConfiguration.rootNode.getChildren("CFBundleShortVersionString")[0].setValue("${project.version}")

      File icon = project.products.macosxIcns;
      if(icon != null) {
        project.copy {
          from icon
          into new File(targetApp, "Resources")
        }

        plistConfiguration.rootNode.getChildren("CFBundleIconFile")[0].setValue(icon.name)

        if(iniFile.exists() && iniFile.canWrite()) {
          StringBuffer buf = new StringBuffer(iniFile.getText("UTF-8"));
          int pos = buf.indexOf("Eclipse.icns");
          buf.replace(pos, pos + 12, icon.getName());
          iniFile.setText(buf.toString(), "UTF-8")
        }
      }

      plistConfiguration.save(plistFile)
    }
  }

  private class LinuxPlatform extends Platform {
    private File executable = new File(productOutputDir, "${project.name}")
    private File iniFile = new File(productOutputDir, "${project.name}.ini")

    @Override
    String getOs() {
      return "linux"
    }

    @Override
    String getWs() {
      return "gtk"
    }

    @Override
    File getIniFile() {
      return iniFile
    }

    @Override
    void applyPlatformSpecificCustomization() {
      new File(productOutputDir, "launcher").renameTo(executable)

      executable.setExecutable(true, false)

      File icon = project.products.linuxXpm;
      if(icon != null) {
        project.copy {
          from icon
          into productOutputDir
          rename(icon.getName(), "icon.xpm")
        }
      }
    }
  }
}
