eclipse {

  defaultVersion = '4.3'

  version('4.3') {

    eclipseGroup = 'eclipse-kepler'

    swtlib {
      postConfigure { project ->
        project.dependencies {
          compile "${eclipseGroup}:org.eclipse.jface:+"
          compile "${eclipseGroup}:org.eclipse.swt:+"
          compile "${eclipseGroup}:org.eclipse.swt.${current_os_suffix}.${current_arch_suffix}:+"
        }
      }
    }

    swtapp {
      configure { project ->

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            String productConfigName = "product_swtapp_${platform}_${arch}"
            project.configurations.create(productConfigName)

            supported_languages.each { language ->

              String localizedProductConfigName = "product_swtapp_${platform}_${arch}_${language}"
              def localizedConfig = project.configurations.create(localizedProductConfigName)
              localizedConfig.extendsFrom project.configurations[productConfigName]
            }
          }
        }
      }

      postConfigure { project ->

        project.dependencies {
          compile "${eclipseGroup}:org.eclipse.swt:+"
          compile "${eclipseGroup}:org.eclipse.jface:+"
        }

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            String productConfigName = "product_swtapp_${platform}_${arch}"
            project.dependencies.add productConfigName, "${eclipseGroup}:org.eclipse.swt.${map_os_to_suffix[platform]}.${map_arch_to_suffix[arch]}:+"

            supported_languages.each { language ->

              String localizedProductConfigName = "product_swtapp_${platform}_${arch}_${language}"
              project.dependencies.add localizedProductConfigName, "${eclipseGroup}:org.eclipse.jface.nl_${language}:+"
            }
          }
        }
      }
    }

    osgiBundle {

      postConfigure { project ->

        project.dependencies {
          compile "${eclipseGroup}:org.eclipse.osgi:+"
        }
      }
    }

    eclipseBundle {

      postConfigure { project ->

        project.dependencies {
          compile "${eclipseGroup}:javax.annotation:+"
          compile "${eclipseGroup}:javax.inject:+"
          compile "${eclipseGroup}:org.eclipse.jface:+"
          compile "${eclipseGroup}:org.eclipse.swt:+"
          compile "${eclipseGroup}:org.eclipse.swt.${current_os_suffix}.${current_arch_suffix}:+"
          compile "${eclipseGroup}:org.eclipse.ui:+"
          compile "${eclipseGroup}:org.eclipse.osgi:+"
        }

        project.tasks.jar.manifest {
          instruction 'Require-Bundle', 'org.eclipse.jface'
          instruction 'Require-Bundle', 'org.eclipse.swt'
          instruction 'Require-Bundle', 'org.eclipse.ui'
        }
      }
    }

    equinoxApp {

      configure { project ->

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            String productConfigName = "product_equinox_${platform}_${arch}"
            project.configurations.create(productConfigName)

            supported_languages.each { language ->
              def localizedConfig = project.configurations.create("product_equinox_${platform}_${arch}_${language}")
              localizedConfig.extendsFrom project.configurations[productConfigName]
            }
          }
        }
      } // configure

      postConfigure { project ->

        project.dependencies {
          compile "${eclipseGroup}:org.eclipse.core.runtime:+"
          runtime "${eclipseGroup}:org.eclipse.core.runtime.compatibility.registry:+"
          compile "${eclipseGroup}:org.eclipse.equinox.app:+"
          runtime "${eclipseGroup}:org.eclipse.equinox.ds:+"
          runtime "${eclipseGroup}:org.eclipse.equinox.event:+"
          runtime "${eclipseGroup}:org.eclipse.equinox.launcher:+"
          runtime "${eclipseGroup}:org.eclipse.equinox.launcher.${current_os_suffix}.${current_arch_suffix}:+"
          runtime "${eclipseGroup}:org.eclipse.equinox.util:+"
          compile "${eclipseGroup}:org.eclipse.osgi:+"
          runtime "${eclipseGroup}:org.eclipse.osgi.services:+"
          runtime "${eclipseGroup}:com.ibm.icu:+"
          runtime "${eclipseGroup}:javax.xml:+"
        }

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            String productConfigName = "product_equinox_${platform}_${arch}"
            project.dependencies.add productConfigName, "${eclipseGroup}:org.eclipse.equinox.launcher.${map_os_to_suffix[platform]}.${map_arch_to_suffix[arch]}:+"

            supported_languages.each { language ->
              String localizedProductConfigName = "product_equinox_${platform}_${arch}_${language}"
              project.dependencies.add localizedProductConfigName, "${eclipseGroup}:org.eclipse.equinox.launcher.${current_os_suffix}.${current_arch_suffix}.nl_${language}:+"
              project.dependencies.add localizedProductConfigName, "${eclipseGroup}:org.eclipse.osgi.nl_${language}:+"
              project.dependencies.add localizedProductConfigName, "${eclipseGroup}:org.eclipse.osgi.services.nl_${language}:+"
            }
          }
        }

        project.tasks.jar.manifest {
          instruction 'Require-Bundle', 'org.eclipse.core.runtime'
        }
      }
    }
  }
}
