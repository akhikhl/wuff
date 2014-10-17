/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import groovy.text.SimpleTemplateEngine
import org.akhikhl.unpuzzle.PlatformConfig
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.lang3.StringEscapeUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestMergeSpec
import org.gradle.api.tasks.bundling.Jar

/**
 *
 * @author akhikhl
 */
class OsgiBundleConfigurer extends JavaConfigurer {

  protected Map buildProperties
  protected Manifest userManifest
  protected final Map expandBinding
  protected final String snapshotQualifier
  protected SimpleTemplateEngine templateEngine

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
    def dependOnBundle = { bundleName ->
      if(!project.configurations.compile.dependencies.find { it.name == bundleName }) {
        def proj = project.rootProject.subprojects.find {
          it.ext.has('bundleSymbolicName') && it.ext.bundleSymbolicName == bundleName
        }
        if(proj) {
          project.dependencies.add 'compile', proj
        } else {
          project.dependencies.add 'compile', "${project.ext.eclipseMavenGroup}:$bundleName:+"
        }
      }
    }
    userManifest?.attributes?.'Require-Bundle'?.split(',')?.each { bundle ->
      def bundleName = bundle.contains(';') ? bundle.split(';')[0] : bundle
      dependOnBundle bundleName
    }
    def pluginXml = project.pluginXml
    if(pluginXml) {
      if(pluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.ui.views' }) {
        dependOnBundle 'org.eclipse.ui.views'
      }
      if(pluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.core.expressions' }) {
        dependOnBundle 'org.eclipse.core.expressions'
      }
    }
  }

  @Override
  protected void createSourceSets() {
    super.createSourceSets()
    buildProperties?.source?.each { sourceName, sourceDir ->
      def sourceSetName = sourceName == '.' ? 'main' : sourceName
      def sourceSet = project.sourceSets.findByName(sourceSetName) ?: project.sourceSets.create(sourceSetName)
      sourceSet.java {
        srcDirs = (sourceDir instanceof Collection ? sourceDir.toList() : [ sourceDir ])
      }
      if(sourceSet.compileConfigurationName != 'compile') {
        project.configurations[sourceSet.compileConfigurationName].extendsFrom project.configurations.compile
      }
    }
  }
  
  @Override
  protected void configureTasks() {
    super.configureTasks()
    configureTask_processBundleFiles()
  }

  @Override
  protected void configureTask_Jar() {

    super.configureTask_Jar()

    buildProperties?.source?.each { sourceName, sourceDir ->
      def sourceSetName = sourceName == '.' ? 'main' : sourceName
      def sourceSet = project.sourceSets[sourceSetName]
      def jarTask = project.tasks.findByName(sourceSet.jarTaskName)
      if(jarTask == null) {
        jarTask = project.task(sourceSet.jarTaskName, type: Jar) {
          dependsOn project.tasks[sourceSet.classesTaskName]
          from project.tasks[sourceSet.compileJavaTaskName].destinationDir
          from project.tasks[sourceSet.processResourcesTaskName].destinationDir
          destinationDir = new File(project.buildDir, 'libs')
          archiveName = sourceSetName
        }
      }
    }

    project.tasks.jar { thisTask ->
      
      dependsOn { project.tasks.processBundleFiles }
      
      inputs.files {
        project.effectiveWuff.generateBundleFiles ? [ getManifestFile() ] : []
      }

      from { project.configurations.privateLib }

      def namePart1 = [baseName, appendix].findResults { it ?: null }.join('-')
      def namePart2 = [version, classifier].findResults { it ?: null }.join('-')
      def namePart3 = [namePart1, namePart2].findResults { it ?: null }.join('_')
      archiveName = [namePart3, extension].findResults { it ?: null }.join('.')

      buildProperties?.source?.each { sourceName, sourceDir ->
        def sourceSetName = sourceName == '.' ? 'main' : sourceName
        if(sourceSetName != 'main' && buildProperties.bin?.includes?.contains(sourceSetName)) {
          def thatJarTask = project.tasks[project.sourceSets[sourceSetName].jarTaskName]
          thisTask.dependsOn thatJarTask
          thisTask.from thatJarTask.archivePath
        }
      }

      manifest = project.manifest {
        from getManifestFile()
      }
    }
  } // configureTask_Jar
  
  protected void configureTask_processBundleFiles() {
    
    project.task('processBundleFiles') {
      group = 'wuff'
      description = 'generates or merges bundle files'
      dependsOn project.tasks.classes
      inputs.property 'generateBundleFiles', { project.effectiveWuff.generateBundleFiles }
      inputs.property 'projectVersion', { project.version }
      inputs.property 'localizationFiles', { PluginUtils.collectPluginLocalizationFiles(project) }
      inputs.properties getExtraFilesProperties()
      inputs.files { project.configurations.runtime }
      outputs.files {
        project.effectiveWuff.generateBundleFiles ? [ getManifestFile() ] : []
      }
      doLast {
        if(project.effectiveWuff.generateBundleFiles) {
          Manifest effectiveManifest = project.manifest {
            // attention: call order is important here!
            from generateManifest(), mergeManifest
            if(userManifest != null)
              from userManifest, mergeManifest
          }
          StringWriter sw = new StringWriter()
          effectiveManifest.writeTo sw
          String effectiveManifestText = sw.toString()
          File generatedManifestFile = getManifestFile()
          if(!generatedManifestFile.exists() || generatedManifestFile.text != effectiveManifestText) {
            generatedManifestFile.parentFile.mkdirs()
            generatedManifestFile.text = effectiveManifestText
          }
        }
      }      
    }
  }

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

    Set effectiveResources = new LinkedHashSet()
    if(buildProperties) {
      // "plugin.xml" and "plugin_customization.ini" are not included,
      // because they are generated as extra-files in createExtraFiles.
      def virtualResources = ['.', 'META-INF/', 'plugin.xml', 'plugin_customization.ini']
      buildProperties?.bin?.includes?.each { relPath ->
        if(!(relPath in virtualResources)) {
          effectiveResources.add(relPath)
        }
      }
    } else {
      effectiveResources.addAll(['splash.bmp', 'OSGI-INF/', 'intro/', 'nl/', 'Application.e4xmi'])
      effectiveResources.addAll(project.projectDir.listFiles({ (it.name =~ /plugin.*\.properties/) as boolean } as FileFilter).collect { it.name })
    }
    log.debug 'configureTask_processResources, effectiveResources: {}', effectiveResources

    project.tasks.processResources {

      for(File f in effectiveResources.collect { new File(project.projectDir, it).canonicalFile }.findAll { it.isDirectory() }) {
        inputs.dir f
      }

      inputs.files {
        effectiveResources.collect { new File(project.projectDir, it).canonicalFile }.findAll { it.isFile() }
      }

      from project.sourceSets.main.resources.srcDirs, {
        if(effectiveConfig.filterProperties) {
          exclude '**/*.properties'
        }
        if(effectiveConfig.filterHtml) {
          exclude '**/*.html', '**/*.htm'
        }
      }

      if(effectiveConfig.filterProperties) {
        from project.sourceSets.main.resources.srcDirs, {
          include '**/*.properties'
          filter filterExpandProperties
        }
      }

      if(effectiveConfig.filterHtml) {
        from project.sourceSets.main.resources.srcDirs, {
          include '**/*.html', '**/*.htm'
          expand expandBinding
        }
      }

      for(String res in effectiveResources) {
        def f = project.file(res)
        if(f.isDirectory()) {
          from f, {
            if(effectiveConfig.filterProperties) {
              exclude '**/*.properties'
            }
            if(effectiveConfig.filterHtml) {
              exclude '**/*.html', '**/*.htm'
            }
            into res
          }
          if(effectiveConfig.filterProperties) {
            from f, {
              include '**/*.properties'
              filter filterExpandProperties
              into res
            }
          }
          if(effectiveConfig.filterHtml) {
            from f, {
              include '**/*.html', '**/*.htm'
              expand expandBinding
              into res
            }
          }
        } else
        from project.projectDir, {
          include res
          if(res.endsWith('.properties') && effectiveConfig.filterProperties) {
            filter filterExpandProperties
          }
        }
      }
    }
  }

  @Override
  protected void createConfigurations() {
    super.createConfigurations()
    if(!project.configurations.findByName('privateLib')) {
      project.configurations {
        privateLib
        compile.extendsFrom privateLib
      }
    }
  }
  
  File getManifestFile() {
    new File(project.projectDir, 'META-INF/MANIFEST.MF')
  }

  protected void prepareManifests() {
    
    File userManifestFile = PluginUtils.findUserManifestFile(project)
    if(userManifestFile) {
      userManifest = project.manifest {
        from userManifestFile
      }.effectiveManifest
    }
    else
      userManifest = null

    def bundleSymbolicName = userManifest?.attributes?.'Bundle-SymbolicName' ?: project.name
    bundleSymbolicName = bundleSymbolicName?.contains(';') ? bundleSymbolicName.split(';')[0] : bundleSymbolicName
    project.ext.bundleSymbolicName = bundleSymbolicName
    
    if(!project.effectiveWuff.generateBundleFiles) {
      if(userManifest == null) {
        log.error 'Problem in {}: wuff.generateBundleFiles=false and no user manifest is found.', project
        log.error 'Please make sure the project contains META-INF/MANIFEST.MF file.'
        throw new GradleException('No user manifest found.')
      }
    }    
  }

  @Override
  protected void createExtraFiles() {
    super.createExtraFiles()
    FileUtils.stringToFile(getPluginXmlString(), PluginUtils.getExtraPluginXmlFile(project))
    FileUtils.stringToFile(getPluginCustomizationString(), PluginUtils.getExtraPluginCustomizationFile(project))
  }

  protected void createPluginCustomization() {
    def m = [:]
    def existingFile = PluginUtils.findPluginCustomizationFile(project)
    if(existingFile) {
      def props = new PropertiesConfiguration()
      props.load(existingFile)
      for(def key in props.getKeys()) {
        m[key] = props.getProperty(key)
      }
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
    prepareManifests()
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

  protected Manifest generateManifest() {
    
    String bundleVersion = ((project.version && project.version != 'unspecified') ? project.version : '1.0.0.0').replace('-SNAPSHOT', snapshotQualifier)
    
    // workaround for OsgiManifest bug: it fails, when classesDir does not exist,
    // i.e. when the project contains no java/groovy classes (resources-only project)
    project.sourceSets.main.output.classesDir.mkdirs()

    def m = project.osgiManifest {
      setName project.ext.bundleSymbolicName
      setVersion bundleVersion
      setClassesDir project.sourceSets.main.output.classesDir
      setClasspath (project.configurations.runtime.copyRecursive() - project.configurations.privateLib.copyRecursive())
    }

    m = m.effectiveManifest

    String activator = PluginUtils.findClassInSources(project, '**/Activator.groovy', '**/Activator.java')
    if(activator) {
      m.attributes['Bundle-Activator'] = activator
      m.attributes['Bundle-ActivationPolicy'] = 'lazy'
    }

    def pluginXml = project.pluginXml
    if(pluginXml) {
      m.attributes['Bundle-SymbolicName'] = project.ext.bundleSymbolicName + '; singleton:=true'
      Map importPackages = PluginUtils.findImportPackagesInPluginConfigFile(project, pluginXml).collectEntries { [ it, '' ] }
      importPackages << ManifestUtils.parsePackages(m.attributes['Import-Package'])
      m.attributes['Import-Package'] = ManifestUtils.packagesToString(importPackages)
    }
    else {
      if(project.extensions.findByName('run')) {
        // eclipse 4 requires runnable application to be a singleton
        m.attributes['Bundle-SymbolicName'] = project.ext.bundleSymbolicName + '; singleton:=true'
      }
      else {
        m.attributes['Bundle-SymbolicName'] = project.ext.bundleSymbolicName
      }
    }

    def localizationFiles = PluginUtils.collectPluginLocalizationFiles(project)
    if(localizationFiles) {
      m.attributes['Bundle-Localization'] = 'plugin'
    }

    if(project.configurations.privateLib.copyRecursive().files) {
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
    if(pluginXml && pluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.core.expressions' }) {
      requiredBundles.add 'org.eclipse.core.expressions'
    }
    if(pluginXml && pluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.ui.views' }) {
      requiredBundles.add 'org.eclipse.ui.views'
    }
    project.configurations.compile.allDependencies.each {
      if(it.name.startsWith('org.eclipse.') && !PlatformConfig.isPlatformFragment(it) && !PlatformConfig.isLanguageFragment(it)) {
        requiredBundles.add it.name
      }
    }
    m.attributes 'Require-Bundle': requiredBundles.sort().join(',')

    def bundleClasspath = m.attributes['Bundle-ClassPath']
    if(bundleClasspath) {
      bundleClasspath = bundleClasspath.split(',\\s*').collect()
    }
    else {
      bundleClasspath = []
    }
    bundleClasspath.add(0, '.')

    project.configurations.privateLib.files.each {
      bundleClasspath.add(it.name)
    }

    bundleClasspath.unique(true)

    m.attributes['Bundle-ClassPath'] = bundleClasspath.join(',')
    return m
  } // generateManifest

  @Override
  protected String getDefaultVersion() {
    (userManifest?.attributes?.'Bundle-Version' ?: '1.0.0.0').replace('.qualifier', '-SNAPSHOT')
  }

  @Override
  protected Map getExtraFilesProperties() {
    Map result = [:]
    result.pluginXml = getPluginXmlString()
    result.pluginCustomization = getPluginCustomizationString()
    return result
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

  protected Closure mergeManifest = { ManifestMergeSpec mergeSpec ->
    mergeSpec.eachEntry { details ->
      String mergeValue
      if(project.effectiveWuff.filterManifest && details.mergeValue) {
        if(!templateEngine) {
          templateEngine = new groovy.text.SimpleTemplateEngine()
        }
        mergeValue = templateEngine.createTemplate(details.mergeValue).make(expandBinding).toString()
      } else {
        mergeValue = details.mergeValue
      }
      String newValue
      if(details.key.equalsIgnoreCase('Require-Bundle')) {
        newValue = ManifestUtils.mergeRequireBundle(details.baseValue, mergeValue)
      } else if(details.key.equalsIgnoreCase('Export-Package')) {
        newValue = ManifestUtils.mergePackageList(details.baseValue, mergeValue)
      } else if(details.key.equalsIgnoreCase('Import-Package')) {
        newValue = ManifestUtils.mergePackageList(details.baseValue, mergeValue)
        // if the user has specified specific eclipse imports, append them to the end
        if (!project.effectiveWuff.eclipseImports.isEmpty()) {
          if (newValue.isEmpty()) {
            newValue = project.effectiveWuff.eclipseImports
          } else {
            newValue = newValue + ',' + project.effectiveWuff.eclipseImports
          }
        }
      } else if(details.key.equalsIgnoreCase('Bundle-ClassPath')) {
        newValue = ManifestUtils.mergeClassPath(details.baseValue, mergeValue)
      } else {
        newValue = mergeValue ?: details.baseValue
      }
      if(newValue) {
        details.value = newValue
      }
      else {
        details.exclude()
      }
    }
  }

  protected void populatePluginCustomization(Map props) {
  }

  @Override
  protected void preConfigure() {
    // attention: call order is important here!
    readBuildProperties()
    super.preConfigure()
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
          if(valueMap == null) {
            valueMap = m[key1] = [:]
          }
          valueMap[key2] = value
        } else
        m[key] = value
      }
    }
    buildProperties = m.isEmpty() ? null : m
  }
}
