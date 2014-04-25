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
class EclipseBundlePluginXmlBuilder extends PluginXmlBuilder {

  protected List perspectiveIds = []

  EclipseBundlePluginXmlBuilder(Project project) {
    super(project)
  }

  @Override
  protected void populate(MarkupBuilder pluginXml) {
    populatePerspectives(pluginXml)
    populateViews(pluginXml)
  }

  protected void populatePerspectives(MarkupBuilder pluginXml) {
    List perspectiveClasses = PluginUtils.findClassesInSources(project, '**/*Perspective.groovy', '**/*Perspective.java', '**/Perspective*.groovy', '**/Perspective*.java')
    for(String perspectiveClass in perspectiveClasses) {
      def existingPerspectiveDef = existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.ui.perspectives' && it.perspective?.'@class'?.text() == perspectiveClass })
      String perspectiveId
      if(existingPerspectiveDef) {
        perspectiveId = existingPerspectiveDef.perspective.'@id'?.text()
        log.debug 'perspective class: {}, found existing perspective {}', perspectiveClass, perspectiveId
      }
      else {
        int dotPos = perspectiveClass.lastIndexOf('.')
        String simpleClassName = dotPos >= 0 ? perspectiveClass.substring(dotPos + 1) : perspectiveClass
        perspectiveId = "${project.name}.${simpleClassName}"
        log.debug 'perspective class: {}, no existing perspective found, inserting new perspective {}', perspectiveClass, perspectiveId
        pluginXml.extension(point: 'org.eclipse.ui.perspectives') {
          perspective id: perspectiveId, name: "${project.name} ${simpleClassName}", 'class': perspectiveClass
        }
      }
      perspectiveIds.add(perspectiveId)
    }
  }

  protected void populateViews(MarkupBuilder pluginXml) {
    List<String> viewClasses = PluginUtils.findClassesInSources(project, '**/*View.groovy', '**/*View.java', '**/View*.groovy', '**/View*.java')
    Map viewClassToViewId = [:]
    for(String viewClass in viewClasses) {
      def existingViewDef = existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.ui.views' && it.view?.'@class' == viewClass })
      String viewId
      if(existingViewDef)
        viewId = existingViewDef.view.'@id'?.text()
      else {
        int dotPos = viewClass.lastIndexOf('.')
        String simpleClassName = dotPos >= 0 ? viewClass.substring(dotPos + 1) : viewClass
        viewId = "${project.name}.${simpleClassName}"
        pluginXml.extension(point: 'org.eclipse.ui.views') {
          view id: viewId, name: "${project.name} ${simpleClassName}", 'class': viewClass
        }
      }
      viewClassToViewId[viewClass] = viewId
    }
    if(perspectiveIds.size() == 1 && viewClasses.size() == 1) {
      String viewId = viewClassToViewId[viewClasses[0]]
      def existingPerspectiveExtension = existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.ui.perspectiveExtensions' && it.perspectiveExtension?.view?.'@id' == viewId })
      if(!existingPerspectiveExtension)
        pluginXml.extension(point: 'org.eclipse.ui.perspectiveExtensions') {
          perspectiveExtension(targetID: perspectiveIds[0]) {
            view id: viewId, standalone: true, minimized: false, relative: 'org.eclipse.ui.editorss', relationship: 'left'
          }
        }
    }
  }
}

