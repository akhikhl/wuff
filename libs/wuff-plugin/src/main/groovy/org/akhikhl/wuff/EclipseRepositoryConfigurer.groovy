/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.Copy
import org.gradle.process.ExecResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *
 * @author akhikhl
 */
class EclipseRepositoryConfigurer {

  protected static final Logger log = LoggerFactory.getLogger(EclipseRepositoryConfigurer)

  protected final Project project

  EclipseRepositoryConfigurer(Project project) {
    this.project = project
  }

  void apply() {

    def configurer = new Configurer(project)
    configurer.apply()

    project.extensions.create('eclipseRepository', EclipseRepositoryExtension)

    project.configurations {
      repositoryFeature {
        transitive = false
      }
    }

    project.afterEvaluate {

      File repositoryDir = new File(project.buildDir, getRepositoryId())
      File sourceDir = new File(project.buildDir, 'repository-source')
      File pluginsDir = new File(sourceDir, 'plugins')
      File featuresDir = new File(sourceDir, 'features')

      if(!project.tasks.findByName('clean'))
        project.task('clean') {
          doLast {
            project.buildDir.deleteDir()
          }
        }

      project.task('repositoryRemoveStaleFeatures') {
        group = 'wuff'
        description = 'removes stale features from repository source'
        dependsOn {
          getDependencyFeatureProjects().collect { it.tasks.featureAssemble }
        }
        outputs.upToDateWhen {
          Set featureDirNames = (getDependencyFeatureDirs().collect { it.name }) as Set
          !featuresDir.listFiles({ it.isDirectory() } as FileFilter).find({ !featureDirNames.contains(it.name) })
        }
        doLast {
          Set featureDirNames = (getDependencyFeatureDirs().collect { it.name }) as Set
          featuresDir.listFiles({ it.isDirectory() } as FileFilter).find({ !featureDirNames.contains(it.name) }).each {
            it.deleteDir()
          }
        }
      }

      project.task('repositoryRemoveStalePlugins') {
        group = 'wuff'
        description = 'removes stale plugins from repository source'
        dependsOn {
          getDependencyFeatureProjects().collect { it.tasks.featureAssemble }
        }
        outputs.upToDateWhen {
          Set pluginFileNames = (getDependencyPluginFiles().collect { it.name }) as Set
          !pluginsDir.listFiles({ it.name.endsWith('.jar') } as FileFilter).find({ !pluginFileNames.contains(it.name) })
        }
        doLast {
          Set pluginFileNames = (getDependencyPluginFiles().collect { it.name }) as Set
          pluginsDir.listFiles({ it.name.endsWith('.jar') } as FileFilter).find({ !pluginFileNames.contains(it.name) }).each {
            it.delete()
          }
        }
      }

      project.task('repositoryCopyFeatures', type: Copy) {
        group = 'wuff'
        description = 'copies features to repository source'
        dependsOn project.tasks.repositoryRemoveStaleFeatures
        dependsOn {
          getDependencyFeatureProjects().collect { it.tasks.featureAssemble }
        }
        inputs.files { getDependencyFeatureDirs().collect { new File(it, 'feature.xml') } }
        outputs.dir featuresDir
        from { getDependencyFeatureDirs() }
        into featuresDir
      }

      project.task('repositoryCopyPlugins', type: Copy) {
        group = 'wuff'
        description = 'copies plugins to repository source'
        dependsOn project.tasks.repositoryRemoveStalePlugins
        dependsOn {
          getDependencyFeatureProjects().collect { it.tasks.featureAssemble }
        }
        inputs.files {
          getDependencyPluginFiles()
        }
        outputs.dir pluginsDir
        from { getDependencyPluginFiles() }
        into pluginsDir
      }

      project.task('repositoryAssemble') {
        group = 'wuff'
        description = 'assembles eclipse repository'
        dependsOn project.tasks.repositoryCopyFeatures
        dependsOn project.tasks.repositoryCopyPlugins
        inputs.dir sourceDir
        outputs.dir repositoryDir
        doLast {
          File baseLocation = project.effectiveUnpuzzle.eclipseUnpackDir
          def equinoxLauncherPlugin = new File(baseLocation, 'plugins').listFiles({ it.name.matches ~/^org\.eclipse\.equinox\.launcher_(.+)\.jar$/ } as FileFilter)
          if(!equinoxLauncherPlugin)
            throw new GradleException("Could not build feature: equinox launcher not found in ${new File(baseLocation, 'plugins')}")
          equinoxLauncherPlugin = equinoxLauncherPlugin[0]
          ExecResult result = project.javaexec {
            main = 'main' // org.eclipse.equinox.launcher.Main
            jvmArgs '-jar', equinoxLauncherPlugin.absolutePath
            jvmArgs '-application', 'org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher'
            jvmArgs '-metadataRepository', repositoryDir.canonicalFile.toURI().toURL().toString()
            jvmArgs '-artifactRepository', repositoryDir.canonicalFile.toURI().toURL().toString()
            jvmArgs '-source', sourceDir.absolutePath
            jvmArgs '-compress'
            jvmArgs '-publishArtifacts'
          }
          result.assertNormalExitValue()
        }
      }

      if(project.eclipseRepository.archive)
        project.task('repositoryArchive', type: Zip) {
          dependsOn project.tasks.repositoryAssemble
          destinationDir = project.buildDir
          if(project.eclipseRepository.archiveName) {
            String aname = project.eclipseRepository.archiveName
            if(!aname.endsWith('.zip'))
              aname += '.zip'
            archiveName = aname
          }
          else {
            baseName = getRepositoryId()
            if(project.version && project.version != 'unspecified')
              version = project.version
          }
          from repositoryDir
        }

      project.task('build') {
        dependsOn {
          project.eclipseRepository.archive ? project.tasks.repositoryArchive : project.tasks.repositoryAssemble
        }
      }
    }
  }

  protected Iterable<Project> getDependencyFeatureProjects() {
    repositoryConfiguration.dependencies.findResults {
      if(!(it instanceof ProjectDependency))
        return null
      def proj = it.dependencyProject
      proj.tasks.findByName('featureAssemble') ? proj : null
    }
  }

  protected Set<File> getDependencyFeatureDirs() {
    Set<File> result = new LinkedHashSet()
    result.addAll getDependencyFeatureProjects().collectMany({ proj ->
      (new File(proj.buildDir, 'output/features').listFiles({ it.isDirectory() && new File(it, 'feature.xml').exists() } as FileFilter) ?: []) as List
    })
    result.addAll getFileDependencies().collectMany({ file ->
      (new File(file, 'features').listFiles({ it.isDirectory() && new File(it, 'feature.xml').exists() } as FileFilter) ?: []) as List
    })
    result
  }

  protected Set<File> getDependencyPluginFiles() {
    Set<File> result = new LinkedHashSet()
    result.addAll getDependencyFeatureProjects().collectMany({ proj ->
      (new File(proj.buildDir, 'output/plugins').listFiles({ it.name.endsWith('.jar') } as FileFilter) ?: []) as List
    })
    result.addAll getFileDependencies().collectMany({ file ->
      (new File(file, 'plugins').listFiles({ it.name.endsWith('.jar') || (it.isDirectory() && new File(it, 'MANIFEST.MF').exists()) } as FileFilter) ?: []) as List
    })
    result
  }

  protected Set<File> getFileDependencies() {
    repositoryConfiguration.dependencies.collectMany {
      if(!(it instanceof FileCollectionDependency))
        return []
      it.resolve()
    }
  }

  protected Configuration getRepositoryConfiguration() {
    project.configurations[getRepositoryConfigurationName()]
  }

  protected String getRepositoryConfigurationName() {
    project.eclipseRepository.configuration ?: 'repositoryFeature'
  }

  protected String getRepositoryId() {
    project.extensions.eclipseRepository.id ?: project.name.replace('-', '.')
  }
}
