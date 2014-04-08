eclipse {

  defaultVersion = '4.3'

  version('4.3') {

    eclipseGroup = 'eclipse-kepler'

    swtlib { project ->
      project.dependencies {
        compile "${eclipseGroup}:org.eclipse.jface:+"
        compile "${eclipseGroup}:org.eclipse.swt:+"
        compile "${eclipseGroup}:org.eclipse.swt.${current_os_suffix}.${current_arch_suffix}:+"
      }
    }

    swtapp configure: { project ->
      project.dependencies {
        compile "${eclipseGroup}:org.eclipse.jface:+"
        compile "${eclipseGroup}:org.eclipse.swt:+"
        compile "${eclipseGroup}:org.eclipse.swt.${current_os_suffix}.${current_arch_suffix}:+"
      }
    },
    platformSpecific: { project, platform, arch ->
      String configName = "product_swt_${platform}_${arch}"
      project.configurations.create(configName)
      project.dependencies.add configName, "${eclipseGroup}:org.eclipse.swt.${map_os_to_suffix[platform]}.${map_arch_to_suffix[arch]}:+"
    },
    platformAndLanguageSpecific: { project, platform, arch, language ->
      String localizedConfigName = "product_swt_${platform}_${arch}_${language}"
      def localizedConfig = project.configurations.create(localizedConfigName)
      localizedConfig.extendsFrom project.configurations["product_swt_${platform}_${arch}"]
      project.dependencies.add localizedConfigName, "${eclipseGroup}:org.eclipse.jface.nl_${language}:+"
    }

    eclipseBundle { project ->
      project.dependencies {
        compile "${eclipseGroup}:javax.annotation:+"
        compile "${eclipseGroup}:javax.inject:+"
        compile "${eclipseGroup}:org.eclipse.jface:+"
        compile "${eclipseGroup}:org.eclipse.swt:+"
        compile "${eclipseGroup}:org.eclipse.swt.${current_os_suffix}.${current_arch_suffix}:+"
        compile "${eclipseGroup}:org.eclipse.ui:+"
      }
      project.tasks.jar.manifest {
        instruction 'Require-Bundle', 'org.eclipse.jface'
        instruction 'Require-Bundle', 'org.eclipse.swt'
        instruction 'Require-Bundle', 'org.eclipse.ui'
      }
    }
  }
}
