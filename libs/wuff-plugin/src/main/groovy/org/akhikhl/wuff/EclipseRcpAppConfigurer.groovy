/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff
import groovy.xml.MarkupBuilder
import org.akhikhl.unpuzzle.PlatformConfig
import org.gradle.api.Project
/**
 *
 * @author akhikhl
 */
class EclipseRcpAppConfigurer extends EquinoxAppConfigurer {

  // key is language or empty string, value is Node
  protected Map userIntroContentXmlMap = [:]

  EclipseRcpAppConfigurer(Project project) {
    super(project)
  }

  @Override
  protected void configureTask_clean() {
    super.configureTask_clean()
    project.tasks.clean {
      doLast {
        if (effectiveConfig.generateBundleFiles) {
          PluginUtils.deleteGeneratedFile(project, PluginUtils.getGeneratedIntroDir(project, ''))
          for(File dir in PluginUtils.findUserLocalizationDirs(project))
            PluginUtils.deleteGeneratedFile(project, PluginUtils.getGeneratedIntroDir(project, dir.name))
        }
      }
    }
  }

  @Override
  protected void configureTask_jar() {
    super.configureTask_jar()
    project.tasks.jar {
      from PluginUtils.getGeneratedIntroDir(project, ''), {
        into 'intro'
      }
      for(File dir in PluginUtils.findUserLocalizationDirs(project))
        from PluginUtils.getGeneratedIntroDir(project, dir.name), {
          into "nl/${dir.name}/intro"
        }
    }
  }

  @Override
  protected void configureTask_processBundleFiles() {
    super.configureTask_processBundleFiles()
    project.tasks.processBundleFiles.dependsOn { project.tasks.processIntroFiles }
  }

  protected void configureTask_processIntroFiles() {

    project.task('processIntroFiles') {
      group = 'wuff'
      description = 'processes intro files'
      dependsOn { project.tasks.processPluginXml }
      inputs.property 'generateBundleFiles', { effectiveConfig.generateBundleFiles }
      inputs.files {
        List result = []
        if(effectiveConfig.generateBundleFiles) {
          def userIntroDir = PluginUtils.findUserIntroDir(project, '')
          if(userIntroDir && userIntroDir.exists())
            userIntroDir.eachFileRecurse(groovy.io.FileType.FILES) {
              result.add it
            }
          for(File dir in PluginUtils.findUserLocalizationDirs(project)) {
            userIntroDir = PluginUtils.findUserIntroDir(project, dir.name)
            if(userIntroDir && userIntroDir.exists())
              userIntroDir.eachFileRecurse(groovy.io.FileType.FILES) {
                result.add it
              }
          }
        }
        result
      }
      outputs.files {
        List result = []
        if(effectiveConfig.generateBundleFiles) {
          result.add PluginUtils.getGeneratedIntroContentXmlFile(project, '')
          def userIntroDir = PluginUtils.findUserIntroDir(project, '')
          def generatedIntroDir = PluginUtils.getGeneratedIntroDir(project, '')
          if(userIntroDir && userIntroDir.exists())
            userIntroDir.eachFileRecurse(groovy.io.FileType.FILES) {
              result.add new File(generatedIntroDir, it.absolutePath - userIntroDir.absolutePath - '/')
            }
          for(File dir in PluginUtils.findUserLocalizationDirs(project)) {
            String language = dir.name
            result.add PluginUtils.getGeneratedIntroContentXmlFile(project, language)
            userIntroDir = PluginUtils.findUserIntroDir(project, language)
            generatedIntroDir = PluginUtils.getGeneratedIntroDir(project, language)
            if(userIntroDir && userIntroDir.exists())
              userIntroDir.eachFileRecurse(groovy.io.FileType.FILES) {
                result.add new File(generatedIntroDir, it.absolutePath - userIntroDir.absolutePath - '/')
              }
          }
        }
        result
      }
      doLast {
        if(effectiveConfig.generateBundleFiles) {
          generateIntroContentXml('')
          def userIntroDir = PluginUtils.findUserIntroDir(project, '')
          if(userIntroDir && userIntroDir.exists()) {
            def generatedIntroDir = PluginUtils.getGeneratedIntroDir(project, '')
            project.copy {
              from userIntroDir
              into generatedIntroDir
            }
          }
          for(File dir in PluginUtils.findUserLocalizationDirs(project)) {
            String language = dir.name
            generateIntroContentXml(language)
            userIntroDir = PluginUtils.findUserIntroDir(project, language)
            if(userIntroDir && userIntroDir.exists()) {
              def generatedIntroDir = PluginUtils.getGeneratedIntroDir(project, language)
              project.copy {
                from userIntroDir
                into generatedIntroDir
              }
            }
          }
        }
      }
    }
  }

  @Override
  protected void configureTasks() {
    super.configureTasks()
    configureTask_processIntroFiles()
  }

  @Override
  protected void createConfigurations() {

    super.createConfigurations()

    PlatformConfig.supported_oses.each { platform ->
      PlatformConfig.supported_archs.each { arch ->

        def productConfig = project.configurations.create("product_rcp_${platform}_${arch}")
        productConfig.extendsFrom project.configurations.findByName("product_equinox_${platform}_${arch}")

        PlatformConfig.supported_languages.each { language ->
          def localizedConfig = project.configurations.create("product_rcp_${platform}_${arch}_${language}")
          localizedConfig.extendsFrom productConfig
          localizedConfig.extendsFrom project.configurations.findByName("product_equinox_${platform}_${arch}_${language}")
        }
      }
    }
  }

  @Override
  protected PluginXmlGenerator createPluginXmlGenerator() {
    new EclipseRcpAppPluginXmlGenerator(project)
  }

  protected void generateIntroContentXml(String language) {
    def userIntroContentXml = getUserIntroContentXmlFile(language)?.withReader('UTF-8') {
      new XmlParser().parse(it)
    }
    StringWriter writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.doubleQuotes = true
    xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
    xml.introContent {
      userIntroContentXml?.children().each {
        XmlUtils.writeNode(xml, it)
      }
      File introFile = PluginUtils.findUserIntroHtmlFile(project, language)
      if(introFile) {
        String homePageId = project.effectivePluginXml?.extension?.find({ it.'@point' == 'org.eclipse.ui.intro.config' })?.config?.presentation?.'@home-page-id'?.text()
        if(homePageId && !userIntroContentXml?.page.find { it.'@id' == homePageId })
          xml.page id: homePageId, url: introFile.name
      }
    }
    def introXmlText = writer.toString()
    def introXml = new XmlParser().parse(new StringReader(introXmlText))
    File introContentXmlFile = PluginUtils.getGeneratedIntroContentXmlFile(project, language)
    if(introXml.iterator()) { // non-empty intro?
      introContentXmlFile.parentFile.mkdirs()
      introContentXmlFile.setText(introXmlText, 'UTF-8')
    } else
      PluginUtils.deleteGeneratedFile(project, introContentXmlFile)
  }

  @Override
  protected List<String> getModules() {
    super.getModules() + [ 'rcpApp' ]
  }

  protected String getProductConfigPrefix() {
    'product_rcp_'
  }

  @Override
  protected String getScaffoldResourceDir() {
    if(project.effectiveWuff.supportsE4())
      'scaffold/eclipse-rcp-app-e4/'
    else
      'scaffold/eclipse-rcp-app/'
  }

  protected File getUserIntroContentXmlFile(String language) {
    PluginUtils.getSourceBundleDirs(project).findResult { dir ->
      if (language)
        dir = new File(dir, 'nl/' + language)
      File f = new File(dir, 'intro/introContent.xml')
      f.exists() ? f : null
    }
  }

  protected void populatePluginCustomization(Map props) {
    if(!props.containsKey('org.eclipse.ui/defaultPerspectiveId')) {
      List perspectiveIds = project.effectivePluginXml?.extension.find({ it.'@point' == 'org.eclipse.ui.perspectives' })?.perspective?.collect { it.'@id' }
      if(perspectiveIds?.size() == 1)
        props['org.eclipse.ui/defaultPerspectiveId'] = perspectiveIds[0]
    }
  }

  @Override
  protected void readUserBundleFiles() {
    super.readUserBundleFiles()
    for(File dir in PluginUtils.getSourceBundleDirs(project))
      readUserIntroContentXml(dir, '')
    for(File dir in PluginUtils.findUserLocalizationDirs(project))
      readUserIntroContentXml(dir, dir.name)
  }

  protected void readUserIntroContentXml(File dir, String language) {
    File introContentXmlFile = new File(dir, 'intro/introContent.xml')
    if(introContentXmlFile.exists())
      userIntroContentXmlMap[language] = introContentXmlFile.withReader('UTF-8') {
        new XmlParser().parse(it)
      }
  }
}

