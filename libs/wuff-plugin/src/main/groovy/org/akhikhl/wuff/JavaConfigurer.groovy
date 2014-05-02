/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.file.FileCopyDetails
import org.gradle.api.Project

/**
 *
 * @author akhikhl
 */
class JavaConfigurer extends Configurer {

  JavaConfigurer(Project project) {
    super(project)
  }

  @Override
  protected void createConfigurations() {
    super.createConfigurations()
    if(!project.configurations.findByName('provided'))
      project.configurations {
        provided
        compile.extendsFrom provided
      }
  }

  @Override
  protected void configureTasks() {
    super.configureTasks()
    configureTask_Jar()
    configureTask_processResources()
  }

  protected void configureProducts() {
    // by default there are no products
  }

  protected void configureTask_Jar() {
  }

  protected void configureTask_processResources() {
    project.tasks.processResources {
      dependsOn project.tasks.createExtraFiles
      from PluginUtils.getExtraDir(project)
      // Here we exclude any resources/classes that are present in project,
      // but overridden by extra-files.
      // Typical example would be "plugin.xml": this file may be present (or not) in project,
      // so we always generate extra-file "plugin.xml" which should be processed
      // as a resource instead of original "plugin.xml".
      File extraDir = PluginUtils.getExtraDir(project)
      mainSpec.eachFile { FileCopyDetails details ->
        if(!details.file.absolutePath.startsWith(extraDir.absolutePath)) {
          ([project.projectDir] + project.sourceSets.main.allSource.srcDirs).each { dir ->
            if(details.file.absolutePath.startsWith(dir.absolutePath)) {
              String relPath = dir.toPath().relativize(details.file.toPath()).toString()
              File extraFile = new File(extraDir, relPath)
              if(extraFile.exists()) {
                log.debug 'excluding {}', details.file
                log.debug 'including {}', extraFile
                details.exclude()
              }
            }
          }
        }
      }
    }
  }

  protected void createSourceSets() {
  }

  @Override
  protected List<String> getModules() {
    return super.getModules() + [ 'java' ]
  }

  @Override
  protected void postConfigure() {
    super.postConfigure()
    configureProducts()
  }

  @Override
  protected void preConfigure() {
    super.preConfigure()
    createSourceSets()
  }
}
