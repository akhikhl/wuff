wuff {

  localMavenRepositoryDir = new File(System.getProperty('user.home'), '.m2/repository')

  wuffDir = new File(System.getProperty('user.home'), '.wuff')

  selectedEclipseVersion = '4.3.2'

  def suffix_os = [ 'linux': 'linux-gtk', 'windows': 'win32' ]
  def suffix_arch = [ 'x86_32': '', 'x86_64': '-x86_64' ]
  def fileExt_os = [ 'linux': 'tar.gz', 'windows': 'zip' ]

  eclipseVersion('3.7.1') {

    eclipseMavenGroup = 'eclipse-indigo-sr1'

    eclipseMirror = 'http://mirror.netcologne.de'

    eclipseArchiveMirror = 'http://archive.eclipse.org'

    sources {

      source "$eclipseArchiveMirror/technology/epp/downloads/release/indigo/SR1/eclipse-jee-indigo-SR1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "$eclipseArchiveMirror/eclipse/downloads/drops/R-3.7.1-201109091335/eclipse-SDK-3.7.1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "$eclipseArchiveMirror/eclipse/downloads/drops/R-3.7.1-201109091335/eclipse-3.7.1-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/indigo/BabelLanguagePack-eclipse-${language}_3.7.0.v20131123061707.zip'
      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/indigo/BabelLanguagePack-rt.equinox-${language}_3.7.0.v20131123061707.zip'
    }

    swtlib {
      project.dependencies {
        compile "${eclipseMavenGroup}:org.eclipse.jface:+"
        compile "${eclipseMavenGroup}:org.eclipse.swt:+"
        provided "${eclipseMavenGroup}:org.eclipse.swt.${current_os_suffix}.${current_arch_suffix}:+"
      }
    }

    swtapp {

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

    osgiBundle {

      project.dependencies {
        compile "${eclipseMavenGroup}:org.eclipse.osgi:+"
      }

      project.tasks.jar.manifest {
        instruction 'Require-Bundle', 'org.eclipse.osgi'
      }
    }

    eclipseBundle {

      project.dependencies {
        compile "${eclipseMavenGroup}:org.eclipse.jface:+"
        compile "${eclipseMavenGroup}:org.eclipse.swt:+"
        provided "${eclipseMavenGroup}:org.eclipse.swt.${current_os_suffix}.${current_arch_suffix}:+"
        compile "${eclipseMavenGroup}:org.eclipse.ui:+"
      }

      project.tasks.jar.manifest {
        instruction 'Require-Bundle', 'org.eclipse.core.runtime'
        instruction 'Require-Bundle', 'org.eclipse.swt'
        instruction 'Require-Bundle', 'org.eclipse.jface'
        instruction 'Require-Bundle', 'org.eclipse.ui'
        instruction 'Require-Bundle', 'org.eclipse.core.expressions'
      }
    }

    equinoxApp {

      project.ext.osgiExecutionEnvironment = 'JavaSE-1.6,J2SE-1.6,J2SE-1.5,J2SE-1.4,J2SE-1.3,J2SE-1.2,JRE-1.1,CDC-1.1/Foundation-1.1,CDC-1.0/Foundation-1.0,OSGi/Minimum-1.2,OSGi/Minimum-1.1,OSGi/Minimum-1.0'

      project.dependencies {
        compile "${eclipseMavenGroup}:org.eclipse.core.runtime:+"
        runtime "${eclipseMavenGroup}:org.eclipse.core.runtime.compatibility.registry:+"
        compile "${eclipseMavenGroup}:org.eclipse.equinox.app:+"
        runtime "${eclipseMavenGroup}:org.eclipse.equinox.ds:+"
        runtime "${eclipseMavenGroup}:org.eclipse.equinox.event:+"
        runtime "${eclipseMavenGroup}:org.eclipse.equinox.launcher:+"
        provided "${eclipseMavenGroup}:org.eclipse.equinox.launcher.${current_os_suffix}.${current_arch_suffix}:+"
        runtime "${eclipseMavenGroup}:org.eclipse.equinox.util:+"
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
          }
        }
      }

      project.tasks.jar.manifest {
        instruction 'Require-Bundle', 'org.eclipse.core.runtime'
      }
    }

    rcpApp {

      boolean hasIntro = PluginUtils.getEclipseIntroId(project)

      project.dependencies {
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
        instruction 'Require-Bundle', 'org.eclipse.core.expressions'
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

    eclipseIdeBundle {

      project.dependencies {
        compile "${eclipseMavenGroup}:org.eclipse.ui.ide:+"
      }

      project.tasks.jar.manifest {
        instruction 'Require-Bundle', 'org.eclipse.ui.ide'
      }
    }

    eclipseIdeApp {

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

  eclipseVersion('3.7.2') {

    extendsFrom '3.7.1'

    eclipseMavenGroup = 'eclipse-indigo-sr2'

    eclipseMirror = 'http://mirror.netcologne.de'

    eclipseArchiveMirror = 'http://archive.eclipse.org'

    sources {

      source "$eclipseMirror/eclipse//technology/epp/downloads/release/indigo/SR2/eclipse-jee-indigo-SR2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "$eclipseArchiveMirror/eclipse/downloads/drops/R-3.7.2-201202080800/eclipse-SDK-3.7.2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "$eclipseArchiveMirror/eclipse/downloads/drops/R-3.7.2-201202080800/eclipse-3.7.2-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/indigo/BabelLanguagePack-eclipse-${language}_3.7.0.v20131123061707.zip'
      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/indigo/BabelLanguagePack-rt.equinox-${language}_3.7.0.v20131123061707.zip'
    }
  }

  eclipseVersion('4.2.1') {

    extendsFrom '3.7.2'

    eclipseMavenGroup = 'eclipse-juno-sr1'

    eclipseMirror = 'http://mirror.netcologne.de'

    eclipseArchiveMirror = 'http://archive.eclipse.org'

    sources {

      source "$eclipseArchiveMirror/technology/epp/downloads/release/juno/SR1/eclipse-jee-juno-SR1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "$eclipseArchiveMirror/eclipse/downloads/drops4/R-4.2.1-201209141800/eclipse-SDK-4.2.1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "$eclipseArchiveMirror/eclipse/downloads/drops4/R-4.2.1-201209141800/eclipse-4.2.1-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/juno/BabelLanguagePack-eclipse-${language}_4.2.0.v20131123041006.zip'
      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/juno/BabelLanguagePack-rt.equinox-${language}_4.2.0.v20131123041006.zip'
    }

    eclipseBundle {

      project.dependencies {
        compile "${eclipseMavenGroup}:javax.annotation:+"
        compile "${eclipseMavenGroup}:javax.inject:+"
      }

      project.tasks.jar.manifest {
        instruction 'Require-Bundle', 'org.eclipse.core.resources'
      }
    }

    rcpApp {

      project.dependencies {
        compile "${eclipseMavenGroup}:javax.annotation:+"
        compile "${eclipseMavenGroup}:javax.inject:+"
      }
    }
  }

  eclipseVersion('4.2.2') {

    extendsFrom '4.2.1'

    eclipseMavenGroup = 'eclipse-juno-sr2'

    eclipseMirror = 'http://mirror.netcologne.de'

    eclipseArchiveMirror = 'http://archive.eclipse.org'

    sources {

      source "$eclipseMirror/eclipse//technology/epp/downloads/release/juno/SR2/eclipse-jee-juno-SR2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "$eclipseMirror/eclipse//eclipse/downloads/drops4/R-4.2.2-201302041200/eclipse-SDK-4.2.2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "$eclipseMirror/eclipse//eclipse/downloads/drops4/R-4.2.2-201302041200/eclipse-4.2.2-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/juno/BabelLanguagePack-eclipse-${language}_4.2.0.v20131123041006.zip'
      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/juno/BabelLanguagePack-rt.equinox-${language}_4.2.0.v20131123041006.zip'
    }
  }

  eclipseVersion('4.3.1') {

    extendsFrom '4.2.2'

    eclipseMavenGroup = 'eclipse-kepler-sr1'

    eclipseMirror = 'http://mirror.netcologne.de'

    eclipseArchiveMirror = 'http://archive.eclipse.org'

    sources {

      source "$eclipseMirror/eclipse//technology/epp/downloads/release/kepler/SR1/eclipse-jee-kepler-SR1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "$eclipseMirror/eclipse//eclipse/downloads/drops4/R-4.3.1-201309111000/eclipse-SDK-4.3.1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "$eclipseMirror/eclipse//eclipse/downloads/drops4/R-4.3.1-201309111000/eclipse-4.3.1-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/kepler/BabelLanguagePack-eclipse-${language}_4.3.0.v20131123020001.zip'
    }
  }

  eclipseVersion('4.3.2') {

    extendsFrom '4.3.1'

    eclipseMavenGroup = 'eclipse-kepler-sr2'

    eclipseMirror = 'http://mirror.netcologne.de'

    eclipseArchiveMirror = 'http://archive.eclipse.org'

    sources {

      source "$eclipseMirror/eclipse//technology/epp/downloads/release/kepler/SR2/eclipse-jee-kepler-SR2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "$eclipseMirror/eclipse//eclipse/downloads/drops4/R-4.3.2-201402211700/eclipse-SDK-4.3.2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "$eclipseMirror/eclipse//eclipse/downloads/drops4/R-4.3.2-201402211700/eclipse-4.3.2-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/kepler/BabelLanguagePack-eclipse-${language}_4.3.0.v20131123020001.zip'
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
