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

/**
 *
 * @author akhikhl
 */
class OsgiBundleConfigurer extends JavaConfigurer {

  protected final Map expandBinding

  OsgiBundleConfigurer(Project project) {
    super(project)
    expandBinding = [ project: project,
      current_os: PlatformConfig.current_os,
      current_arch: PlatformConfig.current_arch,
      current_language: PlatformConfig.current_language ]
  }

  @Override
  protected void applyPlugins() {
    super.applyPlugins()
    project.apply plugin: 'osgi'
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

    project.tasks.jar {

      dependsOn { project.tasks.createOsgiManifest }

      inputs.files { getGeneratedManifestFile() }

      from { project.configurations.privateLib }

      manifest {

        setName(project.name)
        setVersion(project.version)

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
            if(details.key == 'Require-Bundle')
              newValue = ManifestUtils.mergeRequireBundle(details.baseValue, mergeValue)
            else if(details.key == 'Import-Package' || details.key == 'Export-Package')
              newValue = ManifestUtils.mergePackageList(details.baseValue, mergeValue)
            else
              newValue = mergeValue ?: details.baseValue
            if(newValue)
              details.value = newValue
            else
              details.exclude()
          }
        }

        File userManifestFile = PluginUtils.findPluginManifestFile(project)
        // attention: call order is important here!
        if(userManifestFile != null)
          from userManifestFile.absolutePath, mergeManifest

        from getGeneratedManifestFile().absolutePath, mergeManifest
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

    project.tasks.processResources {

      from project.projectDir, {
        include 'splash.bmp'
        // "plugin.xml" and "plugin_customization.ini" are not included here,
        // because they are generated as extra-files in createExtraFiles
      }

      from project.projectDir, {
        include '*.properties'
        if(effectiveConfig.filterProperties)
          filter filterExpandProperties
      }

      from project.sourceSets.main.resources.srcDirs

      if(effectiveConfig.filterHtml)
        from project.sourceSets.main.resources.srcDirs, {
          include '**/*.html', '**/*.htm'
          expand expandBinding
        }

      if(effectiveConfig.filterProperties)
        from project.sourceSets.main.resources.srcDirs, {
          include '**/*.properties'
          filter filterExpandProperties
        }

      from project.file('OSGI-INF'), {
        into 'OSGI-INF'
      }

      if(effectiveConfig.filterProperties)
        from project.file('OSGI-INF'), {
          include '**/*.properties'
          filter filterExpandProperties
          into 'OSGI-INF'
        }

      from project.file('intro'), {
        into 'intro'
      }

      if(effectiveConfig.filterHtml)
        from project.file('intro'), {
          include '**/*.html', '**/*.htm'
          expand expandBinding
          into 'intro'
        }

      from project.file('nl'), {
        into 'nl'
      }

      if(effectiveConfig.filterHtml)
        from project.file('nl'), {
          include '**/*.html', '**/*.htm'
          expand expandBinding
          into 'nl'
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

  protected void createSourceSets() {
    project.sourceSets {
      main {
        resources {
          include 'splash.bmp'
          include 'plugin*.properties'
          include 'OSGI-INF/**'
          include 'intro/**'
          include 'nl/**'
        }
      }
    }
  }

  protected Manifest createManifest() {

    def m = project.osgiManifest {
      setName project.name
      setVersion project.version
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
      m.attributes['Bundle-SymbolicName'] = "${project.name}; singleton:=true" as String
      Map importPackages = PluginUtils.findImportPackagesInPluginConfigFile(project, pluginXml).collectEntries { [ it, '' ] }
      importPackages << ManifestUtils.parsePackages(m.attributes['Import-Package'])
      m.attributes['Import-Package'] = ManifestUtils.packagesToString(importPackages)
    }
    else
      m.attributes['Bundle-SymbolicName'] = project.name

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
    project.configurations.compile.allDependencies.each {
      if(it.name.startsWith('org.eclipse.') && !PlatformConfig.isPlatformFragment(it) && !PlatformConfig.isLanguageFragment(it))
        requiredBundles.add it.name
    }
    m.attributes 'Require-Bundle': requiredBundles.sort().join(',')

    def bundleClasspath = m.attributes['Bundle-Classpath']
    if(bundleClasspath)
      bundleClasspath = bundleClasspath.split(',\\s*').collect()
    else
      bundleClasspath = []

    bundleClasspath.add(0, '.')

    project.configurations.privateLib.files.each {
      bundleClasspath.add(it.name)
    }

    bundleClasspath.unique(true)

    m.attributes['Bundle-Classpath'] = bundleClasspath.join(',')
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
    '1.0.0.0'
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
    if(project.hasProperty('pluginCustomization')) {
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
}
