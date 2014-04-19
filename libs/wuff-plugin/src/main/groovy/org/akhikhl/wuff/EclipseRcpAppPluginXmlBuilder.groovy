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

/**
 *
 * @author akhikhl
 */
class EclipseRcpAppPluginXmlBuilder extends EquinoxAppPluginXmlBuilder {

  protected String productId

  EclipseRcpAppPluginXmlBuilder(Project project) {
    super(project)
  }

  @Override
  protected void populate(MarkupBuilder pluginXml) {
    populateApplication(pluginXml)
    populateProduct(pluginXml)
    populatePerspective(pluginXml)
    populateIntro(pluginXml)
  }

  protected void populateIntro(MarkupBuilder pluginXml) {
    File introFile = PluginUtils.findPluginIntroHtmlFile(project)
    if(introFile) {
      String introId
      def existingIntroDef = existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.ui.intro' })
      if(existingIntroDef)
        introId = existingIntroDef.intro?.'@id'?.text()
      else
        introId = "${project.name}.intro"
        pluginXml.extension(point: 'org.eclipse.ui.intro') {
          intro id: introId, 'class': 'org.eclipse.ui.intro.config.CustomizableIntroPart'
          introProductBinding introId: introId, productId: productId
        }
      if(!existingConfig?.extension.find({ it.'@point' == 'org.eclipse.ui.intro.config' })) {
        String contentPrefix = PluginUtils.getLocalizationDirs(project) ? '$nl$/' : ''
        pluginXml.extension(point: 'org.eclipse.ui.intro.config') {
          config(id: "${project.name}.introConfigId", introId: introId, content: "${contentPrefix}intro/introContent.xml") {
            presentation('home-page-id': 'homePageId', 'standby-page-id': 'homePageId') {
              implementation kind: 'html'
            }
          }
        }
      }
    }
  }

  protected void populatePerspective(MarkupBuilder pluginXml) {
    if(!existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.ui.perspectives' })) {
      String perspectiveClass = PluginUtils.findClassInSources(project, '**/*Perspective.groovy', '**/*Perspective.java')
      if(perspectiveClass)
        pluginXml.extension(point: 'org.eclipse.ui.perspectives') {
          perspective id: "${project.name}.perspective", name: project.name, 'class': perspectiveClass
        }
    }
  }

  protected void populateProduct(MarkupBuilder pluginXml) {
    def existingProductDef = existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.core.runtime.products' })
    if(existingProductDef)
      productId = "${project.name}.${existingProductDef.'@id'}"
    else {
      pluginXml.extension(id: 'product', point: 'org.eclipse.core.runtime.products') {
        product application: applicationId, name: project.name
      }
      productId = "${project.name}.product"
    }
  }
}

