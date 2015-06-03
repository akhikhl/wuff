/*
 * wuff
 *
 * Copyright 2014-2015 Andrey Hihlovskiy and contributors.
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
  protected void createExtraFiles() {
    super.createExtraFiles()
    FileUtils.stringToFile(getIntroXmlString(), PluginUtils.getExtraIntroXmlFile(project))
    for(File dir in PluginUtils.getLocalizationDirs(project))
      FileUtils.stringToFile(getIntroXmlString(dir.name), PluginUtils.getExtraIntroXmlFile(project, dir.name))
  }

  private void createIntroXml() {
    createIntroXml(null)
    for(File dir in PluginUtils.getLocalizationDirs(project))
      createIntroXml(dir.name)
  }

  private void createIntroXml(String language) {
    def existingConfig = PluginUtils.findPluginIntroXmlFile(project, language)?.withReader('UTF-8') {
      new XmlParser().parse(it)
    }
    StringWriter writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.doubleQuotes = true
    xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
    xml.introContent {
      existingConfig?.children().each {
        XmlUtils.writeNode(xml, it)
      }
      populatePluginIntroXml(xml, existingConfig, language)
    }
    def introXml = new XmlParser().parseText(writer.toString())
    String key = language == null ? 'introXml' : "introXml_$language"
    project.ext[key] = (introXml.iterator() as boolean) ? introXml : null
  }

  @Override
  protected PluginXmlBuilder createPluginXmlBuilder() {
    new EclipseRcpAppPluginXmlBuilder(project)
  }

  @Override
  protected void createVirtualConfigurations() {
    super.createVirtualConfigurations()
    createIntroXml()
  }

  @Override
  protected boolean extraFilesUpToDate() {
    if(!FileUtils.stringToFileUpToDate(getIntroXmlString(), PluginUtils.getExtraIntroXmlFile(project))) {
      log.debug '{}: intro-xml is not up-to-date', project.name
      return false
    }
    for(File dir in PluginUtils.getLocalizationDirs(project))
      if(!FileUtils.stringToFileUpToDate(getIntroXmlString(dir.name), PluginUtils.getExtraIntroXmlFile(project, dir.name))) {
        log.debug '{}: intro-xml for {} is not up-to-date', project.name, dir.name
        return false
      }
    return super.extraFilesUpToDate()
  }

  @Override
  protected Map getExtraFilesProperties() {
    Map result = super.getExtraFilesProperties()
    result.introXml = getIntroXmlString()
    for(File dir in PluginUtils.getLocalizationDirs(project))
      result['introXml_' + dir.name] = getIntroXmlString(dir.name)
    return result
  }

  protected final String getIntroXmlString(String language = null) {
    String key = language == null ? 'introXml' : "introXml_$language"
    if(project.hasProperty(key) && project[key] != null) {
      def writer = new StringWriter()
      new XmlNodePrinter(new PrintWriter(writer)).print(project[key])
      return writer.toString()
    }
    return null
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

  protected void populatePluginIntroXml(MarkupBuilder pluginIntroXml, Node existingPluginIntroXml, String language) {
    File introFile = PluginUtils.findPluginIntroHtmlFile(project, language)
    if(introFile) {
      String homePageId = project.pluginXml?.extension?.find({ it.'@point' == 'org.eclipse.ui.intro.config' })?.config?.presentation?.'@home-page-id'?.text()
      if(homePageId && !existingPluginIntroXml?.page.find { it.'@id' == homePageId })
        pluginIntroXml.page id: homePageId, url: introFile.name
    }
  }

  protected void populatePluginCustomization(Map props) {
    if(!props.containsKey('org.eclipse.ui/defaultPerspectiveId')) {
      List perspectiveIds = project.pluginXml?.extension.find({ it.'@point' == 'org.eclipse.ui.perspectives' })?.perspective?.collect { it.'@id' }
      if(perspectiveIds?.size() == 1)
        props['org.eclipse.ui/defaultPerspectiveId'] = perspectiveIds[0]
    }
  }
}

