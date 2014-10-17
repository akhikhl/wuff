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
class EquinoxAppPluginXmlBuilder extends PluginXmlBuilder {

  protected List applicationIds = []

  EquinoxAppPluginXmlBuilder(Project project) {
    super(project)
  }

  protected boolean mustDefineApplicationExtensionPoint() {
    true
  }

  @Override
  protected void populate(MarkupBuilder pluginXmlBuilder) {
    populateApplications(pluginXmlBuilder)
  }

  protected void populateApplications(MarkupBuilder pluginXmlBuilder) {
    List simpleAppIds = []
    existingConfig?.extension?.findAll({ it.'@point' == 'org.eclipse.core.runtime.applications' })?.each {
      String appId = it.'@id'
      String appClass = it.application?.run?.'@class'.text()
      log.info 'found existing extension-point "org.eclipse.core.runtime.applications", id={}, class={}', appId, appClass
      if(!simpleAppIds.contains(appId))
        simpleAppIds.add(appId)
    }    
    if(project.sourceSets.main.allSource.srcDirs.findAll { it.exists() }.size()) {
      List appClasses = PluginUtils.findClassesInSources(project, '**/Application.groovy', '**/Application.java')
      for(String appClass in appClasses)
        if(!existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.core.runtime.applications' && it.application?.run?.'@class'?.text() == appClass })) {
          int dotPos = appClass.lastIndexOf('.')
          String simpleClassName = dotPos >= 0 ? appClass.substring(dotPos + 1) : appClass
          String appId = simpleClassName
          int i = 1
          while(simpleAppIds.contains(appId))
            appId = "${simpleClassName}.${i}"
          log.info 'generating extension-point "org.eclipse.core.runtime.applications", id={}, class={}', appId, appClass
          pluginXmlBuilder.extension(id: appId, point: 'org.eclipse.core.runtime.applications') {
            application {
              run 'class': appClass
            }
          }
          simpleAppIds.add(appId)
        }
    }
    applicationIds = simpleAppIds.collect { "${project.name}.${it}" }
    if(applicationIds.isEmpty() && mustDefineApplicationExtensionPoint()) {
      log.error 'Error in equinox application configuration for project {}:', project.name
      log.error 'Could not generate extension-point "org.eclipse.core.runtime.applications" and there are no user-provided extension-points of this type.'
      log.error 'Reason: project sources do not contain Application.java or Application.groovy.'
    }
  }
}
