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

  protected applicationId

  EquinoxAppPluginXmlBuilder(Project project) {
    super(project)
  }

  @Override
  protected void populate(MarkupBuilder pluginXml) {
    populateApplication(pluginXml)
  }

  protected void populateApplication(MarkupBuilder pluginXml) {
    def existingApplicationDef = existingConfig?.extension?.find({ it.'@point' == 'org.eclipse.core.runtime.applications' })
    if(existingApplicationDef)
      applicationId = "${project.name}.${existingApplicationDef.'@id'}"
    else {
      String appClass = PluginUtils.findClassInSources(project, '**/*Application.groovy', '**/*Application.java')
      if(appClass)
        pluginXml.extension(id: 'application', point: 'org.eclipse.core.runtime.applications') {
          application {
            run class: appClass
          }
        }
      applicationId = "${project.name}.application"
    }
  }
}
