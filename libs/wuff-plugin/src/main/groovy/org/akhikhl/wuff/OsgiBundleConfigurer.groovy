/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import groovy.xml.MarkupBuilder
import org.gradle.api.Project
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.java.archives.Manifest
import org.gradle.api.plugins.osgi.OsgiManifest

/**
 *
 * @author akhikhl
 */
class OsgiBundleConfigurer extends Configurer {

  OsgiBundleConfigurer(Project project) {
    super(project)
  }

  @Override
  protected void configureTasks() {
    super.configureTasks()
    configureTask_createOsgiManifest()
    configureTask_Jar()
  }

  private void configureTask_createOsgiManifest() {

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

  private void configureTask_Jar() {

    project.tasks.jar {

      dependsOn project.tasks.createOsgiManifest

      inputs.files { getGeneratedManifestFile() }

      from { project.configurations.privateLib }

      // Normally these files should be placed in src/main/resources.
      // We support them in root to be backward-compatible with eclipse project layout.
      from 'splash.bmp'
      from 'OSGI-INF', {
        into 'OSGI-INF'
      }

      from 'intro', {
        into 'intro'
      }

      from 'nl', {
        into 'nl'
      }

      manifest {

        def mergeManifest = {
          eachEntry { details ->
            def newValue
            if(details.key == 'Require-Bundle')
              newValue = ManifestUtils.mergeRequireBundle(details.baseValue, details.mergeValue)
            else if(details.key == 'Import-Package' || details.key == 'Export-Package')
              newValue = ManifestUtils.mergePackageList(details.baseValue, details.mergeValue)
            else
              newValue = details.mergeValue ?: details.baseValue
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
  } // configureTask_Jar

  @Override
  protected void createConfigurations() {
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
    Properties customization = PluginUtils.findPluginCustomization(project)
    if(customization == null)
      customization = new Properties()
    populatePluginCustomization(customization)
    project.ext.pluginCustomization = customization.isEmpty() ? null : customization
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
    if(!FileUtils.stringToFileUpToDate(getPluginXmlString(), PluginUtils.getExtraPluginXmlFile(project)))
      return false
    if(!FileUtils.stringToFileUpToDate(getPluginCustomizationString(), PluginUtils.getExtraPluginCustomizationFile(project)))
      return false
    return super.extraFilesUpToDate()
  }

  @Override
  protected Map getExtraFilesProperties() {
    Map result = [:]
    result.pluginXml = getPluginXmlString()
    result.pluginCustomization = getPluginCustomizationString()
    return result
  }

  protected final File getGeneratedManifestFile() {
    new File(project.buildDir, 'osgi/MANIFEST.MF')
  }

  @Override
  protected List<String> getModules() {
    return [ 'osgiBundle' ]
  }

  protected final String getPluginCustomizationString() {
    if(project.hasProperty('pluginCustomization') && project.pluginCustomization != null) {
      def writer = new StringWriter()
      project.pluginCustomization.store(writer, null)
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

  protected void populatePluginCustomization(Properties props) {
  }
}
