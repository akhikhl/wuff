/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.apache.commons.configuration.AbstractFileConfiguration
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.lang3.StringEscapeUtils
import groovy.text.SimpleTemplateEngine
import groovy.xml.MarkupBuilder
import org.gradle.api.Project
import org.gradle.api.java.archives.Manifest
import org.gradle.api.plugins.osgi.OsgiManifest
import org.gradle.api.tasks.bundling.Jar
import org.akhikhl.unpuzzle.PlatformConfig

/**
 *
 * @author akhikhl
 */
class OsgiBundleConfigurer extends JavaConfigurer {

  protected Map buildProperties
  protected java.util.jar.Manifest userManifest
  protected final Map expandBinding
  protected final String snapshotQualifier

  OsgiBundleConfigurer(Project project) {
    super(project)
    expandBinding = [ project: project,
      current_os: PlatformConfig.current_os,
      current_arch: PlatformConfig.current_arch,
      current_language: PlatformConfig.current_language ]
    snapshotQualifier = '.' + (new Date().format('YYYYMMddHHmm'))
  }

  @Override
  protected void applyPlugins() {
    super.applyPlugins()
    project.apply plugin: 'osgi'
  }

  @Override
  protected void configureDependencies() {
    def addBundle = { bundleName ->
      if(!project.configurations.compile.dependencies.find { it.name == bundleName }) {
        def proj = project.rootProject.subprojects.find {
          it.ext.has('bundleSymbolicName') && it.ext.bundleSymbolicName == bundleName
        }
        if(proj)
          project.dependencies.add 'compile', proj
        else
          project.dependencies.add 'compile', "${project.ext.eclipseMavenGroup}:$bundleName:+"
      }
    }
    userManifest?.mainAttributes?.getValue('Require-Bundle')?.split(',')?.each { bundle ->
      def bundleName = bundle.contains(';') ? bundle.split(';')[0] : bundle
      addBundle bundleName
    }
    def pluginXml = project.pluginXml
    if(pluginXml) {
      if(pluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.ui.views' })
        addBundle 'org.eclipse.ui.views'
      if(pluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.core.expressions' })
        addBundle 'org.eclipse.core.expressions'
    }
  }

  @Override
  protected void createSourceSets() {
    super.createSourceSets()
    buildProperties?.source?.each { sourceName, sourceDir ->
      def sourceSetName = sourceName == '.' ? 'main' : sourceName
      def sourceSet = project.sourceSets.findByName(sourceSetName) ?: project.sourceSets.create(sourceSetName)
      sourceSet.java {
        srcDirs = [ sourceDir ]
      }
      if(sourceSet.compileConfigurationName != 'compile')
        project.configurations[sourceSet.compileConfigurationName].extendsFrom project.configurations.compile
    }
  }

  @Override
  protected void configureTasks() {
    super.configureTasks()
    configureTask_createOsgiManifest()
  }

  protected void configureTask_createOsgiManifest() {

    project.task('createOsgiManifest') {
      group = 'wuff'
      description = 'creates OSGi manifest'

      File generatedManifestFile = getGeneratedManifestFile()
      dependsOn project.tasks.classes
      inputs.files { project.configurations.runtime }
      outputs.files generatedManifestFile
      doLast {
        // workaround for OsgiManifest bug: it fails, when classesDir does not exist,
        // i.e. when the project contains no java/groovy classes (resources-only project)
        project.sourceSets.main.output.classesDir.mkdirs()
        generatedManifestFile.parentFile.mkdirs()
        generatedManifestFile.withWriter { createManifest().writeTo it }
      }
    }
  } // configureTask_createOsgiManifest

  @Override
  protected void configureTask_Jar() {

    super.configureTask_Jar()

    buildProperties?.source?.each { sourceName, sourceDir ->
      def sourceSetName = sourceName == '.' ? 'main' : sourceName
      def sourceSet = project.sourceSets[sourceSetName]
      def jarTask = project.tasks.findByName(sourceSet.jarTaskName)
      if(jarTask == null)
        jarTask = project.task(sourceSet.jarTaskName, type: Jar) {
          dependsOn project.tasks[sourceSet.classesTaskName]
          from project.tasks[sourceSet.compileJavaTaskName].destinationDir
          from project.tasks[sourceSet.processResourcesTaskName].destinationDir
          destinationDir = new File(project.buildDir, 'libs')
          archiveName = sourceSetName
        }
    }

    project.tasks.jar { thisTask ->

      dependsOn { project.tasks.createOsgiManifest }

      inputs.files { getGeneratedManifestFile() }

      from { project.configurations.privateLib }

      buildProperties?.source?.each { sourceName, sourceDir ->
        def sourceSetName = sourceName == '.' ? 'main' : sourceName
        if(sourceSetName != 'main' && buildProperties.bin?.includes?.contains(sourceSetName)) {
          def thatJarTask = project.tasks[project.sourceSets[sourceSetName].jarTaskName]
          thisTask.dependsOn thatJarTask
          thisTask.from thatJarTask.archivePath
        }
      }

      manifest {

        setName(project.name)
        setVersion(project.version.replace('-SNAPSHOT', snapshotQualifier))

        def templateEngine

        def mergeManifest = {
          eachEntry { details ->
            String mergeValue
            if(project.wuff.filterManifest && details.mergeValue) {
              if(!templateEngine)
                templateEngine = new groovy.text.SimpleTemplateEngine()
              mergeValue = templateEngine.createTemplate(details.mergeValue).make(expandBinding).toString()
            } else
              mergeValue = details.mergeValue
            String newValue
            if(details.key.equalsIgnoreCase('Require-Bundle'))
              newValue = ManifestUtils.mergeRequireBundle(details.baseValue, mergeValue)
            else if(details.key.equalsIgnoreCase('Import-Package') || details.key.equalsIgnoreCase('Export-Package'))
              newValue = ManifestUtils.mergePackageList(details.baseValue, mergeValue)
            else if(details.key.equalsIgnoreCase('Bundle-ClassPath'))
              newValue = ManifestUtils.mergeClassPath(details.baseValue, mergeValue)
            else
              newValue = mergeValue ?: details.baseValue
            if(newValue)
              details.value = newValue
            else
              details.exclude()
          }
        }

        // attention: call order is important here!

        from getGeneratedManifestFile().absolutePath, mergeManifest

        File userManifestFile = PluginUtils.findPluginManifestFile(project)
        if(userManifestFile != null)
          from userManifestFile.absolutePath, mergeManifest
      }
    }
  } // configureTask_Jar

  protected void configureTask_processResources() {

    super.configureTask_processResources()

    def templateEngine = new SimpleTemplateEngine()

    /*
     * This is special filter for *.property files.
     * According to javadoc on java.util.Properties, such files need to be
     * encoded in ISO 8859-1 encoding.
     * Non-ASCII Unicode characters must be encoded as java unicode escapes
     * in property files. We use escapeJava for such encoding.
     */
    def filterExpandProperties = { line ->
      def w = new StringWriter()
      templateEngine.createTemplate(new StringReader(line)).make(expandBinding).writeTo(w)
      StringEscapeUtils.escapeJava(w.toString())
    }

    def effectiveResources = [] as Set
    if(buildProperties) {
      // "plugin.xml" and "plugin_customization.ini" are not included,
      // because they are generated as extra-files in createExtraFiles.
      def virtualResources = ['.', 'META-INF/', 'plugin.xml', 'plugin_customization.ini']
      buildProperties?.bin?.includes?.each { relPath ->
        if(!(relPath in virtualResources))
          effectiveResources.add(relPath)
      }
    } else {
      effectiveResources.addAll(['splash.bmp', 'OSGI-INF/', 'intro/', 'nl/'])
      effectiveResources.addAll(project.projectDir.listFiles({ (it.name =~ /plugin.*\.properties/) as boolean } as FileFilter).collect { it.name })
    }
    log.debug 'configureTask_processResources, effectiveResources: {}', effectiveResources

    project.tasks.processResources {

      from project.sourceSets.main.resources.srcDirs, {
        if(effectiveConfig.filterProperties)
          exclude '**/*.properties'
        if(effectiveConfig.filterHtml)
          exclude '**/*.html', '**/*.htm'
      }

      if(effectiveConfig.filterProperties)
        from project.sourceSets.main.resources.srcDirs, {
          include '**/*.properties'
          filter filterExpandProperties
        }

      if(effectiveConfig.filterHtml)
        from project.sourceSets.main.resources.srcDirs, {
          include '**/*.html', '**/*.htm'
          expand expandBinding
        }

      for(String res in effectiveResources) {
        def f = project.file(res)
        if(f.isDirectory()) {
          from f, {
            if(effectiveConfig.filterProperties)
              exclude '**/*.properties'
            if(effectiveConfig.filterHtml)
              exclude '**/*.html', '**/*.htm'
            into res
          }
          if(effectiveConfig.filterProperties)
            from f, {
              include '**/*.properties'
              filter filterExpandProperties
              into res
            }
          if(effectiveConfig.filterHtml)
            from f, {
              include '**/*.html', '**/*.htm'
              expand expandBinding
              into res
            }
        } else
          from project.projectDir, {
            include res
            if(res.endsWith('.properties') && effectiveConfig.filterProperties)
              filter filterExpandProperties
          }
      }
    }
  }

  @Override
  protected void createConfigurations() {
    super.createConfigurations()
    if(!project.configurations.findByName('privateLib'))
      project.configurations {
        privateLib
        compile.extendsFrom privateLib
      }
  }

  @Override
  protected void createExtraFiles() {
    super.createExtraFiles()
    FileUtils.stringToFile(getPluginXmlString(), PluginUtils.getExtraPluginXmlFile(project))
    FileUtils.stringToFile(getPluginCustomizationString(), PluginUtils.getExtraPluginCustomizationFile(project))
  }

  protected Manifest createManifest() {

    def m = project.osgiManifest {
      setName project.name
      setVersion project.version.replace('-SNAPSHOT', snapshotQualifier)
      setClassesDir project.sourceSets.main.output.classesDir
      setClasspath (project.configurations.runtime - project.configurations.privateLib)
    }

    m = m.effectiveManifest

    String activator = PluginUtils.findClassInSources(project, '**/Activator.groovy', '**/Activator.java')
    if(activator) {
      m.attributes['Bundle-Activator'] = activator
      m.attributes['Bundle-ActivationPolicy'] = 'lazy'
    }

    def pluginXml = project.pluginXml
    if(pluginXml) {
      m.attributes['Bundle-SymbolicName'] = "${project.bundleSymbolicName}; singleton:=true" as String
      Map importPackages = PluginUtils.findImportPackagesInPluginConfigFile(project, pluginXml).collectEntries { [ it, '' ] }
      importPackages << ManifestUtils.parsePackages(m.attributes['Import-Package'])
      m.attributes['Import-Package'] = ManifestUtils.packagesToString(importPackages)
    }
    else
      m.attributes['Bundle-SymbolicName'] = project.bundleSymbolicName

    def localizationFiles = PluginUtils.collectPluginLocalizationFiles(project)
    if(localizationFiles)
      m.attributes['Bundle-Localization'] = 'plugin'

    if(project.configurations.privateLib.files) {
      Map importPackages = ManifestUtils.parsePackages(m.attributes['Import-Package'])
      PluginUtils.collectPrivateLibPackages(project).each { privatePackage ->
        def packageValue = importPackages.remove(privatePackage)
        if(packageValue != null) {
          project.logger.info 'Package {} is referenced by private library, will be excluded from Import-Package.', privatePackage
          importPackages['!' + privatePackage] = packageValue
        }
      }
      m.attributes['Import-Package'] = ManifestUtils.packagesToString(importPackages)
    }

    def requiredBundles = new LinkedHashSet()
    if(pluginXml && pluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.core.expressions' })
      requiredBundles.add 'org.eclipse.core.expressions'
    if(pluginXml && pluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.ui.views' })
      requiredBundles.add 'org.eclipse.ui.views'
    project.configurations.compile.allDependencies.each {
      if(it.name.startsWith('org.eclipse.') && !PlatformConfig.isPlatformFragment(it) && !PlatformConfig.isLanguageFragment(it))
        requiredBundles.add it.name
    }
    m.attributes 'Require-Bundle': requiredBundles.sort().join(',')

    def bundleClasspath = m.attributes['Bundle-ClassPath']
    if(bundleClasspath)
      bundleClasspath = bundleClasspath.split(',\\s*').collect()
    else
      bundleClasspath = []

    bundleClasspath.add(0, '.')

    project.configurations.privateLib.files.each {
      bundleClasspath.add(it.name)
    }

    bundleClasspath.unique(true)

    m.attributes['Bundle-ClassPath'] = bundleClasspath.join(',')
    return m
  } // createManifest

  protected void createPluginCustomization() {
    def m = [:]
    def existingFile = PluginUtils.findPluginCustomizationFile(project)
    if(existingFile) {
      def props = new PropertiesConfiguration()
      props.load(existingFile)
      for(def key in props.getKeys())
        m[key] = props.getProperty(key)
    }
    populatePluginCustomization(m)
    project.ext.pluginCustomization = m.isEmpty() ? null : m
  }

  protected void createPluginXml() {
    def pluginXml = new XmlParser().parseText(createPluginXmlBuilder().buildPluginXml())
    project.ext.pluginXml = (pluginXml.iterator() as boolean) ? pluginXml : null
  }

  protected PluginXmlBuilder createPluginXmlBuilder() {
    new PluginXmlBuilder(project)
  }

  @Override
  protected void createVirtualConfigurations() {
    super.createVirtualConfigurations()
    createPluginXml()
    createPluginCustomization()
  }

  protected boolean extraFilesUpToDate() {
    if(!FileUtils.stringToFileUpToDate(getPluginXmlString(), PluginUtils.getExtraPluginXmlFile(project))) {
      log.debug '{}: plugin-xml is not up-to-date', project.name
      return false
    }
    if(!FileUtils.stringToFileUpToDate(getPluginCustomizationString(), PluginUtils.getExtraPluginCustomizationFile(project))) {
      log.debug '{}: plugin-customization is not up-to-date', project.name
      return false
    }
    return super.extraFilesUpToDate()
  }

  @Override
  protected String getDefaultVersion() {
    (userManifest?.mainAttributes?.getValue('Bundle-Version') ?: '1.0.0.0').replace('.qualifier', '-SNAPSHOT')
  }

  @Override
  protected Map getExtraFilesProperties() {
    Map result = [:]
    result.pluginXml = getPluginXmlString()
    result.pluginCustomization = getPluginCustomizationString()
    return result
  }

  protected final File getGeneratedManifestFile() {
    new File(project.buildDir, 'tmp-osgi/MANIFEST.MF')
  }

  @Override
  protected List<String> getModules() {
    return super.getModules() + [ 'osgiBundle' ]
  }

  protected final String getPluginCustomizationString() {
    if(project.hasProperty('pluginCustomization') && project.pluginCustomization != null) {
      def props = new PropertiesConfiguration()
      project.pluginCustomization.each { key, value ->
        props.setProperty(key, value)
      }
      def writer = new StringWriter()
      props.save(writer)
      return writer.toString()
    }
    return null
  }

  protected final String getPluginXmlString() {
    if(project.hasProperty('pluginXml') && project.pluginXml != null) {
      def writer = new StringWriter()
      new XmlNodePrinter(new PrintWriter(writer)).print(project.pluginXml)
      return writer.toString()
    }
    return null
  }

  protected void populatePluginCustomization(Map props) {
  }

  @Override
  protected void preConfigure() {
    // attention: call order is important here!
    readBuildProperties()
    super.preConfigure()
    readManifest()
  }

  protected void readBuildProperties() {
    def m = [:]
    File buildPropertiesFile = project.file('build.properties')
    if(buildPropertiesFile.exists()) {
      def props = new PropertiesConfiguration()
      props.load(buildPropertiesFile)
      for(String key in props.getKeys()) {
        def value = props.getProperty(key)
        int dotPos = key.indexOf('.')
        if(dotPos >= 0) {
          String key1 = key.substring(0, dotPos)
          String key2 = key.substring(dotPos + 1)
          Map valueMap = m[key1]
          if(valueMap == null)
            valueMap = m[key1] = [:]
          valueMap[key2] = value
        } else
          m[key] = value
      }
    }
    buildProperties = m.isEmpty() ? null : m
  }

  protected void readManifest() {
    File userManifestFile = PluginUtils.findPluginManifestFile(project)
    if(userManifestFile) {
      userManifestFile.withInputStream {
        userManifest = new java.util.jar.Manifest(it)
      }
      def bundleSymbolicName = userManifest?.mainAttributes?.getValue('Bundle-SymbolicName')
      bundleSymbolicName = bundleSymbolicName.contains(';') ? bundleSymbolicName.split(';')[0] : bundleSymbolicName
      project.ext.bundleSymbolicName = bundleSymbolicName
    }
    else {
      userManifest = null
      project.ext.bundleSymbolicName = project.name
    }
  }
}
