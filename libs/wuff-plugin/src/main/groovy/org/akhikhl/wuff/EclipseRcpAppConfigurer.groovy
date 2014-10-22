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
import org.akhikhl.unpuzzle.PlatformConfig

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

  protected void populatePluginCustomization(Map props) {
    if(!props.containsKey('org.eclipse.ui/defaultPerspectiveId')) {
      List perspectiveIds = project.pluginXml?.extension.find({ it.'@point' == 'org.eclipse.ui.perspectives' })?.perspective?.collect { it.'@id' }
      if(perspectiveIds?.size() == 1)
        props['org.eclipse.ui/defaultPerspectiveId'] = perspectiveIds[0]
    }
  }

  @Override
  protected void configureTask_processBundleFiles() {
    super.configureTask_processBundleFiles()
    project.task.processBundleFiles.dependsOn { project.task.processIntroFiles }
  }

  protected void configureTask_processIntroFiles() {

    project.task('processIntroFiles') {
      group = 'wuff'
      description = 'processes intro files'
      dependsOn { project.tasks.processPluginXml }
      inputs.property 'generateBundleFiles', { project.effectiveWuff.generateBundleFiles }
      outputs.files {
        List result = []
        if(project.effectiveWuff.generateBundleFiles) {
          result.add getIntroContentXmlFile('')
          for(File dir in PluginUtils.findUserLocalizationDirs(project))
            result.add getIntroContentXmlFile(dir.name)
        }
        result
      }
      doLast {
        if(project.effectiveWuff.generateBundleFiles) {
          generateIntroContentXml('')
          for(File dir in PluginUtils.findUserLocalizationDirs(project))
            generateIntroContentXml(dir.name)
        }
      }
    }
  }

  @Override
  protected void configureTasks() {
    super.configureTasks()
    configureTask_processIntroFiles()
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
      File introFile = PluginUtils.findPluginIntroHtmlFile(project, language)
      if(introFile) {
        String homePageId = userPluginXml?.extension?.find({ it.'@point' == 'org.eclipse.ui.intro.config' })?.config?.presentation?.'@home-page-id'?.text()
        if(homePageId && !userIntroContentXml?.page.find { it.'@id' == homePageId })
          xml.page id: homePageId, url: introFile.name
      }
    }
    File introContentXmlFile = getIntroContentXmlFile(language)
    introContentXmlFile.parentFile.mkdirs()
    introContentXmlFile.setText(writer.toString(), 'UTF-8')
  }

  protected File getIntroContentXmlFile(String language) {
    File dir = new File(project.projectDir)
    if(language)
      dir = new File(dir, 'nl/' + language)
    new File(dir, 'intro/introContent.xml')
  }

  protected File getUserIntroContentXmlFile(String language) {
    getSourceBundleDirs(project).findResult { dir ->
      if (language)
        dir = new File(dir, 'nl/' + language)
      File f = new File(dir, 'intro/introContent.xml')
      f.exists() ? f : null
    }
  }

  @Override
  protected void readUserBundleFiles() {
    super.readUserBundleFiles()
    for(File dir in getSourceBundleDirs(project))
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

