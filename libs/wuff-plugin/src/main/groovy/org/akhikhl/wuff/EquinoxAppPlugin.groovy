/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.apache.commons.io.FilenameUtils
import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.*

/**
 *
 * @author akhikhl
 */
class EquinoxAppPlugin implements Plugin<Project> {

  private static final String eclipsePluginMask = /([\da-zA-Z_.-]+?)-((\d+\.)+[\da-zA-Z_.-]*)/

  private static final String osgiFrameworkPluginName = 'org.eclipse.osgi'
  private static final String equinoxLauncherPluginName = 'org.eclipse.equinox.launcher'

  void apply(final Project project) {

    def configurer = new ProjectConfigurer(project, 'equinoxApp')
    configurer.configure()

    project.afterEvaluate {

      configurer.postConfigure()

      TaskUtils.defineTask_createBundleManifest(project)

      project.equinox.beforeProductGeneration.each { obj ->
        if(obj instanceof Closure)
          obj()
      }

      defineTask_wrapLibs(project)

      File equinoxLauncherFile = project.configurations.runtime.find({ getPluginName(it.name) == equinoxLauncherPluginName })
      File frameworkFile = project.configurations.runtime.find({ getPluginName(it.name) == osgiFrameworkPluginName })

      String runDir = "${project.buildDir}/run"
      String runConfigDir = "${runDir}/configuration"
      File runConfigFile = new File("$runConfigDir/config.ini")
      String pluginsDir = "$runDir/plugins"

      project.task('prepareRunConfig') {
        dependsOn project.tasks.jar
        dependsOn project.tasks.wrapLibs
        inputs.files project.configurations.runtime.files
        outputs.files runConfigFile
        doLast {
          // need to delete config-subdirs, otherwise osgi uses cached bundles,
          // not the bundles updated by prepareRunConfig task
          new File(runConfigDir).with {
            if(it.exists())
              it.eachDir { f -> f.deleteDir() }
          }

          // key is plugin name, value is complete launch entry for configuration
          def bundleLaunchList = [:]

          def addBundle = { File file ->
            String pluginName = getPluginName(file.name)
            if(bundleLaunchList.containsKey(pluginName))
              return
            String launchOption = ''
            if(pluginName == 'org.eclipse.equinox.ds' || pluginName == 'org.eclipse.equinox.common')
              launchOption = '@2:start'
            else if(pluginName == 'org.eclipse.core.runtime' || pluginName == 'jersey-core')
              launchOption = '@start'
            if(pluginName != osgiFrameworkPluginName && !pluginName.startsWith(equinoxLauncherPluginName))
              bundleLaunchList[pluginName] = "reference\\:file\\:${file.absolutePath}${launchOption}"
          }

          addBundle project.tasks.jar.archivePath

          wrappedLibsDir.eachFileMatch(~/.*\.jar/) { addBundle it }

          project.configurations.runtime.each {
            if(ManifestUtils.isBundle(project, it))
              addBundle it
          }

          if(project.run.language) {
            project.configurations.findAll({ it.name.endsWith("${PlatformConfig.current_os}_${PlatformConfig.current_arch}_${project.run.language}") }).each { config ->
              config.files.each { file ->
                def m = file.name =~ /([\da-zA-Z_.-]+?)/ + "\\.nl_${project.run.language}" + /-((\d+\.)+[\da-zA-Z_.-]*)/
                if(m) {
                  String pluginName = m[0][1]
                  if(project.configurations.runtime.files.find { getPluginName(it.name) == pluginName })
                    addBundle file
                }
              }
            }
          }

          bundleLaunchList = bundleLaunchList.sort()

          runConfigFile.parentFile.mkdirs()
          runConfigFile.withPrintWriter { PrintWriter configWriter ->
            String applicationId = getEclipseApplicationId(project)
            if(applicationId)
              configWriter.println "eclipse.application=$applicationId"
            String productId = getEclipseProductId(project)
            if(productId)
              configWriter.println "eclipse.product=$productId"
            project.sourceSets.main.resources.srcDirs.each { File srcDir ->
              File splashFile = new File(srcDir, 'splash.bmp')
              if(splashFile.exists())
                configWriter.println "osgi.splashLocation=${splashFile.absolutePath}"
            }
            configWriter.println "osgi.framework=file\\:${frameworkFile.absolutePath}"
            configWriter.println 'osgi.bundles.defaultStartLevel=4'
            configWriter.println 'osgi.bundles=' + bundleLaunchList.values().join(',\\\n  ')
          }

          project.copy {
            from project.configurations.runtime.findAll { it.name.startsWith(equinoxLauncherPluginName) }
            into pluginsDir
            // need to rename them to ensure that platform-specific launcher fragments are automatically found
            rename eclipsePluginMask, '$1_$2'
          }
        }
      } // task prepareRunConfig

      List programArgs = [
        '-configuration',
        runConfigDir,
        '-data',
        runDir,
        '-consoleLog'
      ]

      project.sourceSets.main.resources.srcDirs.each { File srcDir ->
        File splashFile = new File(srcDir, 'splash.bmp')
        if(splashFile.exists())
          programArgs.add '-showSplash'
      }

      programArgs.addAll project.run.args

      if(project.run.language) {
        programArgs.add '-nl'
        programArgs.add project.run.language
      }

      project.tasks.run {
        dependsOn project.tasks.prepareRunConfig
        classpath = project.files(new File(pluginsDir, equinoxLauncherFile.name.replaceAll(eclipsePluginMask, '$1_$2')))
        main = 'org.eclipse.equinox.launcher.Main'
        args = programArgs
      }

      project.tasks.debug {
        dependsOn project.tasks.prepareRunConfig
        classpath = project.files(new File(pluginsDir, equinoxLauncherFile.name.replaceAll(eclipsePluginMask, '$1_$2')))
        main = 'org.eclipse.equinox.launcher.Main'
        args = programArgs
        debug = true
      }

      String outputBaseDir = "${project.buildDir}/output"

      def findFileInProducts = { file ->
        project.configurations.find { config ->
          config.name.startsWith('product_') && config.find { it == file }
        }
      }

      project.equinox.products.each { product ->

        def productConfig = project.configurations.findByName("product_${product.name}")

        String platform = product.platform ?: PlatformConfig.current_os
        String arch = product.arch ?: PlatformConfig.current_arch
        String language = product.language ?: ''

        String suffix = ''
        if(product.name != 'default')
          suffix = product.suffix ?: product.name

        def launchers
        if(product.launchers)
          launchers = product.launchers
        else if(product.launcher)
          launchers = [product.launcher]
        else if(product.platform == 'windows')
          launchers = ['windows']
        else
          launchers = ['shell']

        def jreFolder = null
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

        String productOutputDir = "${outputBaseDir}/${project.name}-${project.version}"
        if(suffix)
          productOutputDir += '-' + suffix

        String buildTaskName = 'buildProduct'
        if(product.name != 'default')
          buildTaskName += '_' + product.name

        project.task(buildTaskName) { task ->

          dependsOn project.tasks.jar
          dependsOn project.tasks.wrapLibs
          project.tasks.build.dependsOn task

          inputs.files project.configurations.runtime.files

          if(productConfig)
            inputs.files productConfig.files

          if(jreFolder)
            inputs.dir jreFolder

          outputs.dir productOutputDir

          doLast {
            // key is plugin name, value is complete launch entry for configuration
            def bundleLaunchList = [:]

            def addBundle = { File file ->
              String pluginName = getPluginName(file.name)
              if(bundleLaunchList.containsKey(pluginName))
                return
              String launchOption = ''
              if(pluginName == 'org.eclipse.equinox.ds' || pluginName == 'org.eclipse.equinox.common')
                launchOption = '@2:start'
              else if(pluginName == 'org.eclipse.core.runtime' || pluginName == 'jersey-core')
                launchOption = '@start'
              if(pluginName != osgiFrameworkPluginName && !pluginName.startsWith(equinoxLauncherPluginName))
                bundleLaunchList[pluginName] = "reference\\:file\\:${file.name}${launchOption}"
              project.copy {
                from file
                into "$productOutputDir/plugins"
                // need to rename them to ensure that platform-specific launcher fragments are automatically found
                if(file.name.startsWith(equinoxLauncherPluginName))
                  rename eclipsePluginMask, '$1_$2'
              }
            }

            addBundle project.tasks.jar.archivePath

            wrappedLibsDir.eachFileMatch(~/.*\.jar/) { addBundle it }

            project.configurations.runtime.each {
              if(ManifestUtils.isBundle(project, it) && !findFileInProducts(it))
                addBundle it
            }

            productConfig?.each {
              if(ManifestUtils.isBundle(project, it))
                addBundle it
            }

            bundleLaunchList = bundleLaunchList.sort()

            File configFile = new File("$productOutputDir/configuration/config.ini")
            configFile.parentFile.mkdirs()
            configFile.withPrintWriter { PrintWriter configWriter ->
              String applicationId = getEclipseApplicationId(project)
              if(applicationId)
                configWriter.println "eclipse.application=$applicationId"
              String productId = getEclipseProductId(project)
              if(productId)
                configWriter.println "eclipse.product=$productId"
              configWriter.println "osgi.framework=file\\:plugins/${frameworkFile.name}"
              configWriter.println 'osgi.bundles.defaultStartLevel=4'
              configWriter.println 'osgi.bundles=' + bundleLaunchList.values().join(',\\\n  ')
              project.sourceSets.main.resources.srcDirs.each { File srcDir ->
                if(new File(srcDir, 'splash.bmp').exists())
                  configWriter.println "osgi.splashPath=file\\:plugins/${project.tasks.jar.archivePath.name}"
              }
            }

            String equinoxLauncherName = 'plugins/' + equinoxLauncherFile.name.replaceAll(eclipsePluginMask, '$1_$2')

            if(jreFolder)
              project.copy {
                from jreFolder
                into "$productOutputDir/jre"
              }

            def launchParameters = project.equinox.launchParameters.clone()

            project.sourceSets.main.resources.srcDirs.each { File srcDir ->
              File splashFile = new File(srcDir, 'splash.bmp')
              if(splashFile.exists())
                launchParameters.add '-showSplash'
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
              File launchScriptFile = new File("${productOutputDir}/${project.name}.sh")
              launchScriptFile.text = "#!/bin/bash\n${javaLocation}java -jar $equinoxLauncherName$launchParameters \"\$@\""
              launchScriptFile.setExecutable(true)
            }

            if(launchers.contains('windows')) {
              def javaLocation = ''
              if(jreFolder)
                javaLocation = '%~dp0\\jre\\bin\\'
              File launchScriptFile = new File("${productOutputDir}/${project.name}.bat")
              launchScriptFile.text = "@start /min cmd /c ${javaLocation}java.exe -jar $equinoxLauncherName$launchParameters %*"
              javaLocation = ''
              if(jreFolder)
                javaLocation = 'jre\\bin\\'
              launchScriptFile = new File("${productOutputDir}/${project.name}.vbs")
              launchScriptFile.text = "Set shell = CreateObject(\"WScript.Shell\")\r\nshell.Run \"cmd /c ${javaLocation}java.exe -jar $equinoxLauncherName$launchParameters\", 0"
            }

            String versionFileName = "${productOutputDir}/VERSION"
            String lineSep = '\n'
            if(platform == 'windows' || launchers.contains('windows')) {
              versionFileName += '.txt'
              lineSep = '\r\n'
            }
            new File(versionFileName).text = "product: ${project.name}" + lineSep +
              "version: ${project.version}" + lineSep +
              "platform: $platform" + lineSep +
              "architecture: $arch" + lineSep +
              "language: ${language ?: 'en'}" + lineSep
          } // doLast
        } // buildProduct_xxx task

        if(project.equinox.archiveProducts) {
          def archiveTaskName = 'archiveProduct'
          if(product.name != 'default')
            archiveTaskName += '_' + product.name

          def archiveType = launchers.contains('windows') ? Zip : Tar

          project.task(archiveTaskName, type: archiveType) { task ->
            task.dependsOn buildTaskName
            project.tasks.build.dependsOn task
            from new File(productOutputDir), { into project.name }
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
            if(project.equinox.additionalFilesToArchive)
              for(def f in project.equinox.additionalFilesToArchive)
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
            destinationDir = new File(outputBaseDir)
            classifier = suffix
            if(archiveType == Tar) {
              extension = '.tar.gz'
              compression = Compression.GZIP
            }
            task.doLast {
              ant.checksum file: it.archivePath
            }
          }
        }

      } // each product
    } // project.afterEvaluate
  } // apply

  private static String getEclipseApplicationId(Project project) {
    String result
    project.sourceSets.main.resources.srcDirs.each { File srcDir ->
      File pluginConfigFile = new File(srcDir, 'plugin.xml')
      if(pluginConfigFile.exists()) {
        def pluginConfig = new XmlParser().parse(pluginConfigFile)
        result = pluginConfig.extension.find({ it.'@point' == 'org.eclipse.core.runtime.applications' })?.'@id'
      }
    }
    if(result)
      result = "${project.name}.$result"
    return result
  }

  private static String getEclipseProductId(Project project) {
    String result
    project.sourceSets.main.resources.srcDirs.each { File srcDir ->
      File pluginConfigFile = new File(srcDir, 'plugin.xml')
      if(pluginConfigFile.exists()) {
        def pluginConfig = new XmlParser().parse(pluginConfigFile)
        result = pluginConfig.extension.find({ it.'@point' == 'org.eclipse.core.runtime.products' })?.'@id'
      }
    }
    if(result)
      result = "${project.name}.$result"
    return result
  }

  private static String getPluginName(String fileName) {
    return fileName.replaceAll(eclipsePluginMask, '$1')
  }

  private static void defineTask_wrapLibs(Project project) {

    File wrappedLibsDir = ProjectUtils.getWrappedLibsDir(project)

    project.task('wrapLibs') {
      inputs.files project.configurations.runtime
      outputs.dir wrappedLibsDir
      doLast {
        wrappedLibsDir.mkdirs()
        inputs.files.each { lib ->
          def libManifest = ManifestUtils.getManifest(project, lib)
          if(ManifestUtils.isBundle(libManifest))
            return
          String baseLibName = FilenameUtils.getBaseName(lib.name)
          def bundleName
          def bundleVersion = ManifestUtils.getManifestEntry(libManifest, 'Implementation-Version')
          if(bundleVersion) {
            def match = baseLibName =~ '(.+)-' + bundleVersion.replaceAll(/\./, /\\./)
            if(match)
              bundleName = match[0][1]
          }
          if(!bundleName) {
            bundleVersion = ManifestUtils.getManifestEntry(libManifest, 'Specification-Version')
            if(bundleVersion) {
              def match = baseLibName =~ '(.+)-' + bundleVersion.replaceAll(/\./, /\\./)
              if(match)
                bundleName = match[0][1]
            }
          }
          if(!bundleName) {
            def match = baseLibName =~ /(.+)-([\d+\.]*\d+[a-zA-Z_-]*)/
            if(match) {
              bundleName = match[0][1]
              bundleVersion = match[0][2]
            }
          }
          if(bundleVersion) {
            // check for too long version numbers, replace to valid bundle version if needed
            def match = bundleVersion =~ /(\d+\.)(\d+\.)(\d+\.)(([\w-]+\.)+[\w-]+)/
            if(match)
              bundleVersion = match[0][1] + match[0][2] + match[0][3] + match[0][4].replaceAll(/\./, '-')
          }
          String fragmentHost
          if(bundleVersion) {
            def match = bundleVersion =~ /([\d+\.]*\d+)([a-zA-Z_-]+)/
            if(match) {
              def suffix = match[0][2]
              if(suffix.startsWith('-'))
                suffix = suffix.substring(1)
              if(suffix) {
                if(suffix != 'patch')
                  fragmentHost = bundleName
                bundleName += '-' + suffix
              }
              bundleVersion = match[0][1]
            }
          }
          bundleName = bundleName ?: baseLibName
          bundleVersion = bundleVersion ?: '1.0'
          String bundlePackageName = "${bundleName}-bundle-${bundleVersion}"
          File manifestFile = new File("${wrappedLibsDir}/${bundlePackageName}-MANIFEST.MF")
          def m = project.osgiManifest {
            setName bundleName
            setSymbolicName bundleName
            setVersion bundleVersion
            setClassesDir lib
            setClasspath project.files(lib)
            instruction 'Bundle-Classpath', lib.name
            instruction 'Wrapped-Library', lib.name
            if(fragmentHost)
              instruction 'Fragment-Host', fragmentHost
          }
          m = m.effectiveManifest
          def packages = ManifestUtils.parsePackages(m.attributes['Import-Package'])
          // workarounds for dynamically referenced classes
          if(bundleName.startsWith('ant-optional'))
            packages.remove 'COM.ibm.netrexx.process'
          else if(bundleName.startsWith('commons-logging'))
            packages = packages.findAll { !it.key.startsWith('org.apache.log') && !it.key.startsWith('org.apache.avalon.framework.logger') }
          else if(bundleName.startsWith('avalon-framework'))
            packages = packages.findAll { !it.key.startsWith('org.apache.log') && !it.key.startsWith('org.apache.avalon.framework.parameters') }
          else if(bundleName == 'batik-js')
            packages.remove 'org.apache.xmlbeans'
          else if(bundleName == 'batik-script')
            packages.remove 'org.mozilla.javascript'
          else if(bundleName == 'fop') {
            packages.remove 'javax.media.jai'
            packages = packages.findAll { !it.key.startsWith('org.apache.tools.ant') }
          }
          else if(bundleName.startsWith('jaxb-impl')) {
            packages = packages.findAll { !it.key.startsWith('com.sun.xml.fastinfoset') }
            packages.remove 'org.jvnet.fastinfoset'
            packages.remove 'org.jvnet.staxex'
          }
          else if(bundleName == 'jdom' || bundleName == 'jdom-b8') {
            packages.remove 'oracle.xml.parser'
            packages.remove 'oracle.xml.parser.v2'
            packages.remove 'org.apache.xerces.dom'
            packages.remove 'org.apache.xerces.parsers'
            packages.remove 'org.jaxen.jdom'
            packages.remove 'org.jaxen'
          }
          else if(bundleName == 'jdom2') {
            packages.remove 'oracle.xml.parser'
            packages.remove 'oracle.xml.parser.v2'
            packages.remove 'org.apache.xerces.dom'
            packages.remove 'org.apache.xerces.parsers'
          }
          else if(bundleName.startsWith('ojdbc')) {
            packages.remove 'javax.resource'
            packages.remove 'javax.resource.spi'
            packages.remove 'javax.resource.spi.endpoint'
            packages.remove 'javax.resource.spi.security'
            packages.remove 'oracle.i18n.text.converter'
            packages.remove 'oracle.ons'
            packages.remove 'oracle.security.pki'
          }
          else if(bundleName.startsWith('saxon'))
            packages.remove 'com.saxonica.validate'
          else if(bundleName.startsWith('svnkit')) {
            packages = packages.findAll { !it.key.startsWith('org.tmatesoft.sqljet') }
            packages.remove 'org.tigris.subversion.javahl'
          }
          else if(bundleName == 'xalan')
            packages.remove 'sun.io'
          else if(bundleName == 'xmlgraphics-commons')
            packages = packages.findAll { !it.key.startsWith('com.sun.image.codec') }
          else if(bundleName == 'jaxen') {
            packages.remove 'nu.xom'
            packages = packages.findAll { !it.key.startsWith('org.jdom') && !it.key.startsWith('org.dom4j') }
          } else if(bundleName == 'xercesImpl')
            packages.remove 'sun.io'
          else if(bundleName == 'commons-jxpath')
            packages.remove 'ant-optional'
          m.attributes.remove 'Import-Package'
          if(packages)
            m.attributes(['Import-Package': ManifestUtils.packagesToString(packages)])

          m.attributes.remove 'Class-Path'

          manifestFile.withWriter { m.writeTo it }

          ant.jar(destFile: "${wrappedLibsDir}/${bundlePackageName}.jar", manifest: manifestFile) { fileset(file: lib) }
          manifestFile.delete()
        }
      }
    } // task wrapLibs
  }
}
