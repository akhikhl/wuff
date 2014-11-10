/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import groovy.text.SimpleTemplateEngine
import groovy.xml.MarkupBuilder
import org.akhikhl.unpuzzle.PlatformConfig
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.lang3.StringEscapeUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestMergeSpec
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar

/**
 *
 * @author akhikhl
 */
class OsgiBundleConfigurer extends JavaConfigurer {

  protected Map buildProperties
  protected Manifest userManifest
  protected Manifest inlineManifest
  protected Map userPluginCustomization
  protected Node userPluginXml
  protected final Map expandBinding
  protected final String snapshotQualifier

  protected SimpleTemplateEngine templateEngine = new SimpleTemplateEngine()

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
    if(userManifest)
      userManifest.attributes.'Require-Bundle'?.split(',')?.each { bundle ->
        def bundleName = bundle.contains(';') ? bundle.split(';')[0] : bundle
        dependOnBundle bundleName
      }
    if((userPluginXml && userPluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.ui.views' }) || PluginUtils.findViewClassesInSources(project))
      dependOnBundle 'org.eclipse.ui.views'
    if(userPluginXml && userPluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.core.expressions' })
      dependOnBundle 'org.eclipse.core.expressions'
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

  protected void configureTask_clean() {

    if(!project.tasks.findByName('clean'))
      project.task('clean') {
        doLast {
          project.buildDir.deleteDir()
        }
      }

    project.tasks.clean {
      doLast {
        if(effectiveConfig.generateBundleFiles) {
          File f = PluginUtils.getGeneratedBundleFile(project, 'META-INF/MANIFEST.MF')
          if(f.exists())
            f.delete()
          if(f.parentFile.exists() && !f.parentFile.listFiles())
            f.parentFile.deleteDir()
          f = PluginUtils.getGeneratedPluginXmlFile(project)
          if(f.exists())
            f.delete()
          f = PluginUtils.getGeneratedPluginCustomizationFile(project)
          if(f.exists())
            f.delete()
          PluginUtils.getGeneratedPluginLocalizationFiles(project).each {
            if(it.exists())
              it.delete()
          }
        }
      }
    }
  }

  protected void configureTask_jar() {

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

    inlineManifest = project.jar.manifest

    project.tasks.jar { thisTask ->

      dependsOn { project.tasks.processBundleFiles }

      def inputFiles = {
        [ PluginUtils.getEffectivePluginXmlFile(project),
          PluginUtils.getEffectivePluginCustomizationFile(project) ] +
          PluginUtils.getEffectivePluginLocalizationFiles(project)
      }

      inputs.file { PluginUtils.getEffectiveManifestFile(project) }
      inputs.files inputFiles

      from inputFiles
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
        File f = new File(project.projectDir, 'META-INF/MANIFEST.MF')
        from PluginUtils.getEffectiveManifestFile(project)
      }
    }
  }

  protected void configureTask_processBundleFiles() {

    project.task('processBundleFiles') {
      group = 'wuff'
      description = 'processes bundle files'
      dependsOn { [ project.tasks.processManifest, project.tasks.processPluginCustomization, project.tasks.processOsgiInf ] }
    }
  }

  protected void configureTask_processManifest() {

    project.task('processManifest') {
      group = 'wuff'
      description = 'processes manifest file'
      dependsOn { project.tasks.processPluginXml }
      onlyIf { effectiveConfig.generateBundleFiles }
      inputs.property 'projectVersion', { project.version }
      inputs.property 'effectiveBundleVersion', { getEffectiveBundleVersion() }
      inputs.files { project.configurations.runtime }
      inputs.files { PluginUtils.findUserBundleFiles(project, 'META-INF/MANIFEST.MF') }
      outputs.file { PluginUtils.getGeneratedBundleFile(project, 'META-INF/MANIFEST.MF') }
      doLast {
        generateEffectiveManifest()
      }
    }
  }

  protected void configureTask_processOsgiInf() {

    project.task('processOsgiInf', type: Copy) {
      group = 'wuff'
      description = 'processes OSGI-INF directory'
      dependsOn { project.tasks.processPluginXml }
      onlyIf { effectiveConfig.generateBundleFiles }
      into '.'
      into 'OSGI-INF', { copySpec ->
        filteredCopy(copySpec, PluginUtils.findUserOsgiInfDir(project))
      }
    }

    project.sourceSets.main.output.dir('OSGI-INF', builtBy: 'processOsgiInf')
  }

  protected void configureTask_processPluginCustomization() {

    project.task('processPluginCustomization') {
      group = 'wuff'
      description = 'processes plugin_customization.ini'
      dependsOn { project.tasks.processPluginXml }
      inputs.property 'generateBundleFiles', { effectiveConfig.generateBundleFiles }
      inputs.files { PluginUtils.getEffectivePluginXmlFile(project) }
      inputs.files {
        List result = []
        if(effectiveConfig.generateBundleFiles) {
          def f = PluginUtils.findUserPluginCustomizationFile(project)
          if(f)
            result.add(f)
        }
        result
      }
      outputs.file {
        List result = []
        if(effectiveConfig.generateBundleFiles)
          result.add PluginUtils.getGeneratedPluginCustomizationFile(project)
        result
      }
      doLast {
        if(effectiveConfig.generateBundleFiles)
          generateEffectivePluginCustomization()
      }
    }
  }

  protected void configureTask_processPluginXml() {

    project.task('processPluginXml') {
      group = 'wuff'
      description = 'processes plugin.xml'
      dependsOn { project.tasks.classes }
      inputs.property 'generateBundleFiles', { effectiveConfig.generateBundleFiles }
      inputs.files {
        List result = []
        if(effectiveConfig.generateBundleFiles) {
          def f = PluginUtils.findUserPluginXmlFile(project)
          if (f)
            result.add(f)
        }
        result
      }
      inputs.files { PluginUtils.findUserPluginLocalizationFiles(project) }
      inputs.files { project.configurations.runtime }
      outputs.files {
        List result = []
        if(effectiveConfig.generateBundleFiles) {
          def f = PluginUtils.getGeneratedPluginXmlFile(project)
          if(f)
            result.add(f)
        }
        result
      }
      doLast {
        if(effectiveConfig.generateBundleFiles) {
          generateEffectivePluginXml()
          for(def f in PluginUtils.findUserPluginLocalizationFiles(project))
            if(f.parentFile != project.projectDir)
              project.copy {
                from f
                into project.projectDir
              }
        }
      }
    }
  }

  protected void configureTask_processResources() {

    project.tasks.processResources { thisTask ->

      Set additionalResources = new LinkedHashSet()
      if(buildProperties) {
        def virtualResources = ['.', 'META-INF/']
        buildProperties?.bin?.includes?.each { relPath ->
          if(!(relPath in virtualResources))
            additionalResources.add(relPath)
        }
      } else {
        additionalResources.addAll(['splash.bmp', 'OSGI-INF/', 'intro/', 'nl/', 'Application.e4xmi'])
        additionalResources.addAll(project.projectDir.listFiles({ (it.name =~ /plugin.*\.properties/) as boolean } as FileFilter).collect { it.name })
      }

      for(File f in additionalResources.collect { project.file(it).canonicalFile }.findAll { it.isDirectory() })
        inputs.dir f

      inputs.files {
        additionalResources.collect { project.file(it).canonicalFile }.findAll { it.isFile() }
      }

      for(def dir in project.sourceSets.main.resources.srcDirs)
        filteredCopy(thisTask, dir, '.')

      for(String res in additionalResources)
        filteredCopy(thisTask, project.file(res), '.')

      if(effectiveConfig.generateBundleFiles)
        eachFile { FileCopyDetails details ->
          File bundleFile = project.file("src/main/bundle/${details.sourcePath}")
          if(bundleFile.exists()) {
            log.warn 'Found in src/main/bundle, excluding from resource processing: {}', details.path
            details.exclude()
          }
        }
    }
  }

  @Override
  protected void configureTasks() {
    super.configureTasks()
    configureTask_clean()
    configureTask_jar()
    configureTask_processBundleFiles()
    configureTask_processManifest()
    configureTask_processOsgiInf()
    configureTask_processPluginCustomization()
    configureTask_processPluginXml()
    configureTask_processResources()
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

  protected PluginXmlGenerator createPluginXmlGenerator() {
    new PluginXmlGenerator(project)
  }

  @Override
  protected void readUserFiles() {
    super.readUserFiles()
    readUserBundleFiles()
  }

  /**
   * This is a special filter for *.property files.
   * According to javadoc on java.util.Properties, such files need to be
   * encoded in ISO 8859-1 encoding.
   * Non-ASCII Unicode characters must be encoded as java unicode escapes
   * in property files. We use escapeJava for such encoding.
   */
  protected Closure filterExpandProperties = { String line ->
    StringWriter w = new StringWriter()
    templateEngine.createTemplate(new StringReader(line)).make(expandBinding).writeTo(w)
    StringEscapeUtils.escapeJava(w.toString())
  }

  protected void filteredCopy(CopySpec copy, File source) {
    if(!source)
      return
    copy.from source, {
      if(effectiveConfig.filterProperties)
        exclude '**/*.properties'
      if(effectiveConfig.filterHtml)
        exclude '**/*.html', '**/*.htm'
    }
    if(effectiveConfig.filterProperties)
      copy.from source, {
        include '**/*.properties'
        filter filterExpandProperties
      }
    if(effectiveConfig.filterHtml)
      copy.from source, {
        include '**/*.html', '**/*.htm'
        expand expandBinding
      }
  }

  protected void generateEffectiveManifest() {

    // workaround for OsgiManifest bug: it fails, when classesDir does not exist,
    // i.e. when the project contains no java/groovy classes (resources-only project)
    project.sourceSets.main.output.classesDir.mkdirs()

    def m = project.osgiManifest {
      setName project.ext.bundleSymbolicName
      setVersion getEffectiveBundleVersion()
      setClassesDir project.sourceSets.main.output.classesDir
      setClasspath(project.configurations.runtime.copyRecursive() - project.configurations.privateLib.copyRecursive())
    }

    m = m.effectiveManifest

    String activator = PluginUtils.findClassInSources(project, '**/Activator.groovy', '**/Activator.java')
    if (activator) {
      m.attributes['Bundle-Activator'] = activator
      m.attributes['Bundle-ActivationPolicy'] = 'lazy'
    }

    if (project.effectivePluginXml) {
      m.attributes['Bundle-SymbolicName'] = project.ext.bundleSymbolicName + '; singleton:=true'
      Map importPackages = PluginUtils.findImportPackagesInPluginConfigFile(project, project.effectivePluginXml).collectEntries {
        [it, '']
      }
      importPackages << ManifestUtils.parsePackages(m.attributes['Import-Package'])
      m.attributes['Import-Package'] = ManifestUtils.packagesToString(importPackages)
    } else {
      if (project.extensions.findByName('run')) {
        // eclipse 4 requires runnable application to be a singleton
        m.attributes['Bundle-SymbolicName'] = project.ext.bundleSymbolicName + '; singleton:=true'
      } else {
        m.attributes['Bundle-SymbolicName'] = project.ext.bundleSymbolicName
      }
    }

    def localizationFiles = PluginUtils.getEffectivePluginLocalizationFiles(project)
    if (localizationFiles)
      m.attributes['Bundle-Localization'] = 'plugin'

    if (project.configurations.privateLib.copyRecursive().files) {
      Map importPackages = ManifestUtils.parsePackages(m.attributes['Import-Package'])
      PluginUtils.collectPrivateLibPackages(project).each { privatePackage ->
        def packageValue = importPackages.remove(privatePackage)
        if (packageValue != null) {
          project.logger.info 'Package {} is referenced by private library, will be excluded from Import-Package.', privatePackage
          importPackages['!' + privatePackage] = packageValue
        }
      }
      m.attributes['Import-Package'] = ManifestUtils.packagesToString(importPackages)
    }

    def requiredBundles = new LinkedHashSet()
    if (project.effectivePluginXml) {
      if (project.effectivePluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.core.expressions' })
        requiredBundles.add 'org.eclipse.core.expressions'
      if (project.effectivePluginXml.extension.find { it.'@point'.startsWith 'org.eclipse.ui.views' })
        requiredBundles.add 'org.eclipse.ui.views'
    }
    project.configurations.compile.allDependencies.each {
      if (it.name.startsWith('org.eclipse.') && !PlatformConfig.isPlatformFragment(it) && !PlatformConfig.isLanguageFragment(it)) {
        requiredBundles.add it.name
      }
    }
    m.attributes 'Require-Bundle': requiredBundles.sort().join(',')

    def bundleClasspath = m.attributes['Bundle-ClassPath']
    if (bundleClasspath)
      bundleClasspath = bundleClasspath.split(',\\s*').collect()
    else
      bundleClasspath = []
    if (!bundleClasspath.contains('.'))
      bundleClasspath.add(0, '.')

    project.configurations.privateLib.files.each {
      bundleClasspath.add(it.name)
    }

    bundleClasspath.unique(true)
    m.attributes['Bundle-ClassPath'] = bundleClasspath.join(',')

    Manifest effectiveManifest = project.manifest {
      // attention: call order is important here!
      from inlineManifest, mergeManifest
      from m, mergeManifest
      if (userManifest != null)
        from userManifest, mergeManifest
    }

    StringWriter sw = new StringWriter()
    effectiveManifest.writeTo sw
    String manifestText = sw.toString()
    File file = PluginUtils.getGeneratedBundleFile(project, 'META-INF/MANIFEST.MF')
    if (file.exists() && file.getText('UTF-8') == manifestText)
      log.info 'skipped {}', file
    else {
      log.info 'writing {}', file
      file.parentFile.mkdirs()
      file.setText(manifestText, 'UTF-8')
    }
  }

  protected void generateEffectivePluginCustomization() {
    def effectivePluginCustomization = [:] + this.userPluginCustomization
    populatePluginCustomization(effectivePluginCustomization)
    File file = PluginUtils.getEffectivePluginCustomizationFile(project)
    if(effectivePluginCustomization) {
      def props = new PropertiesConfiguration()
      effectivePluginCustomization.each { String key, value ->
        props.setProperty(key, value)
      }
      StringWriter sw = new StringWriter()
      props.save(sw)
      String effectivePluginCustomizationText = sw.toString()
      if(file.exists() && file.getText('UTF-8') == effectivePluginCustomizationText)
        log.debug 'skipped {}', file
      else {
        log.debug 'writing {}', file
        file.parentFile.mkdirs()
        file.setText(effectivePluginCustomizationText, 'UTF-8')
      }
    } else
      PluginUtils.deleteGeneratedFile(project, file)
  }

  protected void generateEffectivePluginXml() {

    StringWriter sw = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(sw)
    xml.doubleQuotes = true
    xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
    xml.pi eclipse: [version: '3.2']
    xml.plugin {
      userPluginXml?.children().each {
        XmlUtils.writeNode(xml, it)
      }
      createPluginXmlGenerator().populate(xml)
    }
    String pluginXmlText = sw.toString()

    if(effectiveConfig.filterPluginXml) {
      Map binding = [ project: project,
                      current_os: PlatformConfig.current_os,
                      current_arch: PlatformConfig.current_arch,
                      current_language: PlatformConfig.current_language ]
      pluginXmlText = templateEngine.createTemplate(pluginXmlText).make(binding).toString()
    }

    project.effectivePluginXml = new XmlParser().parseText(pluginXmlText)

    File file = PluginUtils.getEffectivePluginXmlFile(project)
    if(file.exists() && !file.getText('UTF-8') != pluginXmlText)
      log.info 'skipped {}', file
    else {
      log.info 'writing {}', file
      file.parentFile.mkdirs()
      file.setText(pluginXmlText, 'UTF-8')
    }
  }

  @Override
  protected String getDefaultProjectVersion() {
    (userManifest?.attributes?.'Bundle-Version' ?: '1.0.0.0').replace('.qualifier', '-SNAPSHOT')
  }

  protected String getEffectiveBundleVersion() {
    ((project.version && project.version != 'unspecified') ? project.version : '1.0.0.0').replace('-SNAPSHOT', snapshotQualifier)
  }

  @Override
  protected List<String> getModules() {
    return super.getModules() + [ 'osgiBundle' ]
  }

  protected Closure mergeManifest = { ManifestMergeSpec mergeSpec ->
    mergeSpec.eachEntry { details ->
      String mergeValue
      if(effectiveConfig.filterManifest && details.mergeValue)
        mergeValue = templateEngine.createTemplate(details.mergeValue).make(expandBinding).toString()
      else
        mergeValue = details.mergeValue
      String newValue
      if(details.key.equalsIgnoreCase('Require-Bundle')) {
        newValue = ManifestUtils.mergeRequireBundle(details.baseValue, mergeValue)
      } else if(details.key.equalsIgnoreCase('Export-Package')) {
        newValue = ManifestUtils.mergePackageList(details.baseValue, mergeValue)
      } else if(details.key.equalsIgnoreCase('Import-Package')) {
        newValue = ManifestUtils.mergePackageList(details.baseValue, mergeValue)
        // if the user has specified specific eclipse imports, append them to the end
        if (!effectiveConfig.eclipseImports.isEmpty()) {
          if (newValue.isEmpty()) {
            newValue = effectiveConfig.eclipseImports
          } else {
            newValue = newValue + ',' + effectiveConfig.eclipseImports
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
    project.ext.bundleSymbolicName = null
    project.ext.effectivePluginXml = null
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

  protected void readUserBundleFiles() {

    File userManifestFile = PluginUtils.findUserBundleFile(project, 'META-INF/MANIFEST.MF')
    if(userManifestFile)
      userManifest = project.manifest {
        from userManifestFile
      }.effectiveManifest
    else
      userManifest = null

    def bundleSymbolicName = userManifest?.attributes?.'Bundle-SymbolicName' ?: project.name
    bundleSymbolicName = bundleSymbolicName?.contains(';') ? bundleSymbolicName.split(';')[0] : bundleSymbolicName
    project.ext.bundleSymbolicName = bundleSymbolicName

    if(!effectiveConfig.generateBundleFiles) {
      if(userManifest == null) {
        log.error 'Problem in {}: wuff.generateBundleFiles=false and no user manifest is found.', project
        log.error 'Please make sure the project contains META-INF/MANIFEST.MF file.'
        throw new GradleException('No user manifest found.')
      }
    }

    File userPluginXmlFile = PluginUtils.findUserPluginXmlFile(project)
    if(userPluginXmlFile) {
      userPluginXml = userPluginXmlFile.withInputStream {
        new XmlParser().parse(it)
      }
    }
    else
      userPluginXml = null

    File effectivePluginXmlFile = PluginUtils.getEffectivePluginXmlFile(project)
    if(effectivePluginXmlFile.exists())
      project.ext.effectivePluginXml = effectivePluginXmlFile.withInputStream {
        new XmlParser().parse(it)
      }
    else if(userPluginXmlFile)
      project.ext.effectivePluginXml = userPluginXmlFile.withInputStream {
        new XmlParser().parse(it)
      }

    // absent plugin.xml is OK

    userPluginCustomization = [:]
    def userPluginCustomizationFile = PluginUtils.findUserPluginCustomizationFile(project)
    if(userPluginCustomizationFile) {
      def props = new PropertiesConfiguration()
      props.load(userPluginCustomizationFile)
      for(def key in props.getKeys()) {
        userPluginCustomization[key] = props.getProperty(key)
      }
    }
  }
}
