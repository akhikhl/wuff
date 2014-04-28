wuff {

  defaultEclipseVersion = '4.3'

  eclipseVersion('4.3') {

    eclipseMavenGroup = 'eclipse-kepler'

    swtlib {
      postConfigure { project ->
        project.dependencies {
          compile "${eclipseMavenGroup}:org.eclipse.jface:+"
          compile "${eclipseMavenGroup}:org.eclipse.swt:+"
          provided "${eclipseMavenGroup}:org.eclipse.swt.${current_os_suffix}.${current_arch_suffix}:+"
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

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            String productConfigName = "product_swtapp_${platform}_${arch}"
            project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.swt.${map_os_to_suffix[platform]}.${map_arch_to_suffix[arch]}:+"

            supported_languages.each { language ->

              String localizedProductConfigName = "product_swtapp_${platform}_${arch}_${language}"
              project.dependencies.add localizedProductConfigName, "${eclipseMavenGroup}:org.eclipse.jface.nl_${language}:+"
              project.dependencies.add localizedProductConfigName, "${eclipseMavenGroup}:org.eclipse.swt.nl_${language}:+"
              project.dependencies.add localizedProductConfigName, "${eclipseMavenGroup}:org.eclipse.swt.${map_os_to_suffix[platform]}.${map_arch_to_suffix[arch]}.nl_${language}:+"
            }
          }
        }
      }
    }

    osgiBundle {

      postConfigure { project ->

        project.dependencies {
          compile "${eclipseMavenGroup}:org.eclipse.osgi:+"
        }

        project.tasks.jar.manifest {
          instruction 'Require-Bundle', 'org.eclipse.osgi'
        }
      }
    }

    eclipseBundle {

      postConfigure { project ->

        project.dependencies {
          compile "${eclipseMavenGroup}:javax.annotation:+"
          compile "${eclipseMavenGroup}:javax.inject:+"
          compile "${eclipseMavenGroup}:org.eclipse.jface:+"
          compile "${eclipseMavenGroup}:org.eclipse.swt:+"
          provided "${eclipseMavenGroup}:org.eclipse.swt.${current_os_suffix}.${current_arch_suffix}:+"
          compile "${eclipseMavenGroup}:org.eclipse.ui:+"
        }

        project.tasks.jar.manifest {
          instruction 'Require-Bundle', 'org.eclipse.core.runtime'
          instruction 'Require-Bundle', 'org.eclipse.core.resources'
          instruction 'Require-Bundle', 'org.eclipse.swt'
          instruction 'Require-Bundle', 'org.eclipse.jface'
          instruction 'Require-Bundle', 'org.eclipse.ui'
        }
      }
    }

    equinoxApp {

      configure { project ->

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            def productConfig = project.configurations.create("product_equinox_${platform}_${arch}")

            supported_languages.each { language ->
              def localizedConfig = project.configurations.create("product_equinox_${platform}_${arch}_${language}")
              localizedConfig.extendsFrom productConfig
            }
          }
        }

        project.products.productList.each { product ->
          if(product.name && !product.configName) {
            String platform = product.platform ?: current_os
            String arch = product.arch ?: current_arch
            String language = product.language ?: ''
            String languageSuffix = language ? "_${language}" : ''
            def productSpecificConfig = project.configurations.create("product_equinox_${product.name}")
            def productAndPlatformSpecificConfig = project.configurations.create("product_equinox_${product.name}_${platform}_${arch}${languageSuffix}")
            productAndPlatformSpecificConfig.extendsFrom project.configurations["product_equinox_${platform}_${arch}${languageSuffix}"]
            productAndPlatformSpecificConfig.extendsFrom productSpecificConfig
          }
        }
      } // configure

      postConfigure { project ->

        project.dependencies {
          compile "${eclipseMavenGroup}:org.eclipse.core.runtime:+"
          runtime "${eclipseMavenGroup}:org.eclipse.core.runtime.compatibility.registry:+"
          compile "${eclipseMavenGroup}:org.eclipse.equinox.app:+"
          runtime "${eclipseMavenGroup}:org.eclipse.equinox.ds:+"
          runtime "${eclipseMavenGroup}:org.eclipse.equinox.event:+"
          runtime "${eclipseMavenGroup}:org.eclipse.equinox.launcher:+"
          provided "${eclipseMavenGroup}:org.eclipse.equinox.launcher.${current_os_suffix}.${current_arch_suffix}:+"
          runtime "${eclipseMavenGroup}:org.eclipse.equinox.util:+"
          compile "${eclipseMavenGroup}:org.eclipse.osgi:+"
          runtime "${eclipseMavenGroup}:org.eclipse.osgi.services:+"
          runtime "${eclipseMavenGroup}:com.ibm.icu:+"
          runtime "${eclipseMavenGroup}:javax.xml:+"
        }

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            String productConfigName = "product_equinox_${platform}_${arch}"
            project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.equinox.launcher.${map_os_to_suffix[platform]}.${map_arch_to_suffix[arch]}:+"

            supported_languages.each { language ->
              String localizedProductConfigName = "product_equinox_${platform}_${arch}_${language}"
              project.dependencies.add localizedProductConfigName, "${eclipseMavenGroup}:org.eclipse.equinox.launcher.${map_os_to_suffix[platform]}.${map_arch_to_suffix[arch]}.nl_${language}:+"
              project.dependencies.add localizedProductConfigName, "${eclipseMavenGroup}:org.eclipse.osgi.nl_${language}:+"
              project.dependencies.add localizedProductConfigName, "${eclipseMavenGroup}:org.eclipse.osgi.services.nl_${language}:+"
            }
          }
        }

        project.tasks.jar.manifest {
          instruction 'Require-Bundle', 'org.eclipse.core.runtime'
        }
      }
    }

    rcpApp {

      configure { project ->

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            def productConfig = project.configurations.create("product_rcp_${platform}_${arch}")
            productConfig.extendsFrom project.configurations.findByName("product_equinox_${platform}_${arch}")

            supported_languages.each { language ->
              def localizedConfig = project.configurations.create("product_rcp_${platform}_${arch}_${language}")
              localizedConfig.extendsFrom productConfig
              localizedConfig.extendsFrom project.configurations.findByName("product_equinox_${platform}_${arch}_${language}")
            }
          }
        }

        project.products.productList.each { product ->
          if(product.name && !product.configName) {
            String platform = product.platform ?: current_os
            String arch = product.arch ?: current_arch
            String language = product.language ?: ''
            String languageSuffix = language ? "_${language}" : ''
            def productSpecificConfig = project.configurations.create("product_rcp_${product.name}")
            productSpecificConfig.extendsFrom project.configurations["product_equinox_${product.name}"]
            def productAndPlatformSpecificConfig = project.configurations.create("product_rcp_${product.name}_${platform}_${arch}${languageSuffix}")
            productAndPlatformSpecificConfig.extendsFrom project.configurations["product_rcp_${platform}_${arch}${languageSuffix}"]
            productAndPlatformSpecificConfig.extendsFrom productSpecificConfig
          }
        }
      }

      postConfigure { project ->

        boolean hasIntro = PluginUtils.getEclipseIntroId(project)

        project.dependencies {
          compile "${eclipseMavenGroup}:javax.annotation:+"
          compile "${eclipseMavenGroup}:javax.inject:+"
          runtime "${eclipseMavenGroup}:org.eclipse.core.filesystem:+"
          runtime "${eclipseMavenGroup}:org.eclipse.core.net:+"
          compile "${eclipseMavenGroup}:org.eclipse.jface:+"
          compile "${eclipseMavenGroup}:org.eclipse.swt:+"
          provided "${eclipseMavenGroup}:org.eclipse.swt.${current_os_suffix}.${current_arch_suffix}:+"
          compile "${eclipseMavenGroup}:org.eclipse.ui:+"
          if(hasIntro)
            runtime "${eclipseMavenGroup}:org.eclipse.ui.intro:+"
        }

        project.tasks.jar.manifest {
          instruction 'Require-Bundle', 'org.eclipse.core.filesystem'
          instruction 'Require-Bundle', 'org.eclipse.core.net'
          instruction 'Require-Bundle', 'org.eclipse.jface'
          instruction 'Require-Bundle', 'org.eclipse.swt'
          instruction 'Require-Bundle', 'org.eclipse.ui'
          if(hasIntro)
            instruction 'Require-Bundle', 'org.eclipse.ui.intro'
        }

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            String productConfigName = "product_rcp_${platform}_${arch}"
            project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.core.filesystem.${map_os_to_filesystem_suffix[platform]}.${map_arch_to_suffix[arch]}:+"
            project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.core.net.${map_os_to_filesystem_suffix[platform]}.${map_arch_to_suffix[arch]}:+"
            project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.swt.${map_os_to_suffix[platform]}.${map_arch_to_suffix[arch]}:+"

            supported_languages.each { language ->

              String localizedConfigName = "product_rcp_${platform}_${arch}_${language}"
              project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.core.net.${map_os_to_filesystem_suffix[platform]}.${map_arch_to_suffix[arch]}.nl_${language}:+"
              project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.jface.nl_${language}:+"
              project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.swt.nl_${language}:+"
              project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.swt.${map_os_to_suffix[platform]}.${map_arch_to_suffix[arch]}.nl_${language}:+"
              project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.ui.nl_${language}:+"
              if(hasIntro)
                project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.ui.intro.nl_${language}:+"
            }
          }
        }
      }
    }

    eclipseIdeBundle {

      postConfigure { project ->
        project.dependencies {
          compile "${eclipseMavenGroup}:org.eclipse.ui.ide:+"
        }

        project.tasks.jar.manifest {
          instruction 'Require-Bundle', 'org.eclipse.ui.ide'
        }
      }
    }

    eclipseIdeApp {

      configure { project ->

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            def productConfig = project.configurations.create("product_eclipseIde_${platform}_${arch}")
            productConfig.extendsFrom project.configurations.findByName("product_rcp_${platform}_${arch}")

            supported_languages.each { language ->
              def localizedConfig = project.configurations.create("product_eclipseIde_${platform}_${arch}_${language}")
              localizedConfig.extendsFrom productConfig
              localizedConfig.extendsFrom project.configurations.findByName("product_rcp_${platform}_${arch}_${language}")
            }
          }
        }

        project.products.productList.each { product ->
          if(product.name && !product.configName) {
            String platform = product.platform ?: current_os
            String arch = product.arch ?: current_arch
            String language = product.language ?: ''
            String languageSuffix = language ? "_${language}" : ''
            def productSpecificConfig = project.configurations.create("product_eclipseIde_${product.name}")
            productSpecificConfig.extendsFrom project.configurations["product_rcp_${product.name}"]
            def productAndPlatformSpecificConfig = project.configurations.create("product_eclipseIde_${product.name}_${platform}_${arch}${languageSuffix}")
            productAndPlatformSpecificConfig.extendsFrom project.configurations["product_eclipseIde_${platform}_${arch}${languageSuffix}"]
            productAndPlatformSpecificConfig.extendsFrom productSpecificConfig
          }
        }
      }

      postConfigure { project ->

        project.dependencies {
          runtime "${eclipseMavenGroup}:org.eclipse.ui.ide.application:+"
          runtime "${eclipseMavenGroup}:org.eclipse.equinox.p2.engine:+"
          runtime "${eclipseMavenGroup}:org.eclipse.equinox.p2.core:+"
          runtime "${eclipseMavenGroup}:org.eclipse.equinox.p2.engine:+"
          runtime "${eclipseMavenGroup}:org.eclipse.equinox.p2.metadata:+"
          runtime "${eclipseMavenGroup}:org.eclipse.equinox.p2.metadata.repository:+"
          runtime "${eclipseMavenGroup}:org.eclipse.equinox.p2.repository:+"
        }

        project.tasks.jar.manifest {
          instruction 'Require-Bundle', 'org.eclipse.ui.ide.application'
        }

        supported_oses.each { platform ->
          supported_archs.each { arch ->

            String productConfigName = "product_eclipseIde_${platform}_${arch}"

            supported_languages.each { language ->

              String localizedConfigName = "product_eclipseIde_${platform}_${arch}_${language}"
              project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.ui.ide.nl_${language}:+"
              project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.ui.ide.application.nl_${language}:+"
            }
          }
        }
      }
    }
  }

  wrappedLibs {
    /^ant-optional/ {
      excludeImport ~/^COM\.ibm\.netrexx\.process/
    }

    /^avalon-framework/ {
      excludeImport ~/^org\.apache\.log/
      excludeImport ~/^org\.apache\.avalon\.framework\.parameters/
    }

    /^batik-js$/ {
      excludeImport ~/^org\.apache\.xmlbeans/
    }

    /^batik-script$/ {
      excludeImport ~/^org\.mozilla\.javascript/
    }

    /^commons-logging/ {
      excludeImport ~/^org\.apache\.log/
      excludeImport ~/^org\.apache\.avalon\.framework\.logger/
    }

    /^commons-jxpath/ {
      excludeImport ~/^ant-optional/
      excludeImport ~/^javax\.servlet/
    }

    /^fop$/ {
      excludeImport ~/^javax\.media\.jai/
      excludeImport ~/^org\.apache\.tools\.ant/
    }

    /^jaxb-impl/ {
      excludeImport ~/^com\.sun\.xml\.fastinfoset/
      excludeImport ~/^org\.jvnet\.fastinfoset/
      excludeImport ~/^org\.jvnet\.staxex/
    }

    /^jdom$|^jdom-b/ {
      excludeImport ~/^oracle\.xml\.parser/
      excludeImport ~/^org\.apache\.xerces/
      excludeImport ~/^org\.jaxen/
      excludeImport ~/^org.saxpath/
    }

    /^jdom2$/ {
      excludeImport ~/^oracle\.xml\.parser/
      excludeImport ~/^org\.apache\.xerces/
    }

    /^ojdbc/ {
      excludeImport ~/^javax\.resource$/
      excludeImport ~/^javax\.resource\.spi$/
      excludeImport ~/^javax\.resource\.spi\.endpoint$/
      excludeImport ~/^javax\.resource\.spi\.security$/
      excludeImport ~/^oracle\.i18n\.text\.converter$/
      excludeImport ~/^oracle\.ons$/
      excludeImport ~/^oracle\.security\.pki$/
    }

    /^saxon/ {
      excludeImport ~/^com\.saxonica\.validate$/
    }

    /^svnkit/ {
      excludeImport ~/^org\.tmatesoft\.sqljet/
      excludeImport ~/^org.tigris.subversion.javahl$/
    }

    /^xalan$/ {
      excludeImport ~/^sun\.io$/
    }

    /^xmlgraphics-commons$/ {
      excludeImport ~/^com\.sun\.image\.codec/
    }

    /^jaxen$/ {
      excludeImport ~/^nu.xom$/
      excludeImport ~/^org\.jdom/
      excludeImport ~/^org\.dom4j/
    }

    /^xercesImpl$/ {
      excludeImport ~/^sun.io$/
    }
  }
}
