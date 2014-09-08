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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class FeatureConfigurer {

  protected static final Logger log = LoggerFactory.getLogger(FeatureConfigurer)

  protected final Project project

  FeatureConfigurer(Project project) {
    this.project = project
  }

  void apply() {
    project.configurations {
      feature
    }

    project.task('build') {
      group = 'wuff'
      description = 'builds Eclipse feature'
      dependsOn {
        project.configurations.feature.dependencies.findResults {
          def proj = it.dependencyProject
          proj.tasks.findByName('build')
        }
      }
      inputs.files { project.configurations.feature }
      doLast {
        project.configurations.feature.dependencies.each {
          def proj = it.dependencyProject
          project.copy {
            from proj.jar.archivePath
            into new File(project.buildDir, 'plugins')
          }
          writeFeatureXml(new File(project.buildDir, "features/${project.name}/feature.xml"))
        }
      }
    }
  }

  protected void writeFeatureXml(File file) {
    def featureVersion = project.version ?: '1.0.0'
    if(featureVersion == 'unspecified')
      featureVersion = '1.0.0'
    featureVersion = featureVersion.replace('-SNAPSHOT', '.qualifier')
    file.parentFile.mkdirs()
    file.withWriter {
      def xml = new MarkupBuilder(it)
      xml.doubleQuotes = true
      xml.mkp.xmlDeclaration version: '1.0', encoding: 'UTF-8'
      xml.feature id: project.name, label: project.name, version: featureVersion, {
        project.configurations.feature.dependencies.each {
          def proj = it.dependencyProject
          plugin id: proj.name, 'download-size': '0', 'install-size': '0', version: '0.0.0', unpack: false
        }
      }
    }
  }
}

