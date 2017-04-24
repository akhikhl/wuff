wuff {

  // select you mirror
  // def eclipseMirror = 'http://mirror.switch.ch'
  
  def eclipseMirror = 'http://www.eclipse.org/downloads/download.php?file='
  
  def eclipseArchiveMirror = 'http://www.eclipse.org/downloads/download.php?file='
  // old access path:
  // def eclipseArchiveMirror = 'http://archive.eclipse.org'

  wuffDir = new File(System.getProperty('user.home'), '.wuff')

  localMavenRepositoryDir = new File(wuffDir, 'm2_repository')

  selectedEclipseVersion = '4.4.2'

  def suffix_os = [ 'linux': 'linux-gtk', 'macosx': 'macosx-cocoa', 'windows': 'win32' ]
  def suffix_arch = [ 'x86_32': '', 'x86_64': '-x86_64' ]
  def fileExt_os = [ 'linux': 'tar.gz', 'macosx': 'tar.gz', 'windows': 'zip' ]
    
  eclipseVersion('3.7.1') {

    eclipseMavenGroup = 'eclipse-indigo-sr1'

    sources {

      source "${eclipseArchiveMirror}/technology/epp/downloads/release/indigo/SR1/eclipse-jee-indigo-SR1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse/downloads/drops/R-3.7.1-201109091335/eclipse-SDK-3.7.1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "${eclipseArchiveMirror}/eclipse/downloads/drops/R-3.7.1-201109091335/eclipse-3.7.1-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/indigo/BabelLanguagePack-eclipse-${language}_3.7.0.v20131123061707.zip'
      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/indigo/BabelLanguagePack-rt.equinox-${language}_3.7.0.v20131123061707.zip'
    }

    swtlib {
      project.dependencies {
        compile "${eclipseMavenGroup}:org.eclipse.jface:+"
        compile "${eclipseMavenGroup}:org.eclipse.swt:+"
        compile "${eclipseMavenGroup}:org.eclipse.swt.${current_os_suffix}${current_arch_suffix}:+"
      }
    }

    swtapp {

      supported_oses.each { platform ->
        supported_archs.each { arch ->

          String productConfigName = "product_swtapp_${platform}_${arch}"
          project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.swt.${map_os_to_suffix[platform]}${map_arch_to_suffix[platform + '-' + arch]}:+"

          supported_languages.each { language ->

            String localizedProductConfigName = "product_swtapp_${platform}_${arch}_${language}"
            project.dependencies.add localizedProductConfigName, "${eclipseMavenGroup}:org.eclipse.jface.nl_${language}:+"
            project.dependencies.add localizedProductConfigName, "${eclipseMavenGroup}:org.eclipse.swt.nl_${language}:+"
            project.dependencies.add localizedProductConfigName, "${eclipseMavenGroup}:org.eclipse.swt.${map_os_to_suffix[platform]}${map_arch_to_suffix[platform + '-' + arch]}.nl_${language}:+"
          }
        }
      }
    }

    osgiBundle {
    }

    eclipseBundle {

      project.dependencies {
        compile "${eclipseMavenGroup}:org.eclipse.jface:+"
        compile "${eclipseMavenGroup}:org.eclipse.swt:+"
        compile "${eclipseMavenGroup}:org.eclipse.swt.${current_os_suffix}${current_arch_suffix}:+"
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
        compile "${eclipseMavenGroup}:org.eclipse.equinox.launcher.${current_os_suffix}${current_arch_suffix}:+"
        runtime "${eclipseMavenGroup}:org.eclipse.equinox.util:+"
        runtime "${eclipseMavenGroup}:org.eclipse.osgi.services:+"
        runtime "${eclipseMavenGroup}:com.ibm.icu:+"
        runtime "${eclipseMavenGroup}:javax.xml:+"
      }

      supported_oses.each { platform ->
        supported_archs.each { arch ->

          String productConfigName = "product_equinox_${platform}_${arch}"
          project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.equinox.launcher.${map_os_to_suffix[platform]}${map_arch_to_suffix[platform + '-' + arch]}:+"

          supported_languages.each { language ->
            String localizedProductConfigName = "product_equinox_${platform}_${arch}_${language}"
            project.dependencies.add localizedProductConfigName, "${eclipseMavenGroup}:org.eclipse.equinox.launcher.${map_os_to_suffix[platform]}${map_arch_to_suffix[platform + '-' + arch]}.nl_${language}:+"
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
        compile "${eclipseMavenGroup}:org.eclipse.swt.${current_os_suffix}${current_arch_suffix}:+"
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
          if(platform != 'macosx' || arch != 'x86_64')
            project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.core.filesystem.${map_os_to_filesystem_suffix[platform]}${map_arch_to_suffix[platform + '-' + arch]}:+"
          if(platform != 'macosx')
            project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.core.net.${map_os_to_filesystem_suffix[platform]}${map_arch_to_suffix[platform + '-' + arch]}:+"
          project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.swt.${map_os_to_suffix[platform]}${map_arch_to_suffix[platform + '-' + arch]}:+"

          supported_languages.each { language ->

            String localizedConfigName = "product_rcp_${platform}_${arch}_${language}"
            if(platform != 'macosx')
              project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.core.net.${map_os_to_filesystem_suffix[platform]}${map_arch_to_suffix[platform + '-' + arch]}.nl_${language}:+"
            project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.jface.nl_${language}:+"
            project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.swt.nl_${language}:+"
            project.dependencies.add localizedConfigName, "${eclipseMavenGroup}:org.eclipse.swt.${map_os_to_suffix[platform]}${map_arch_to_suffix[platform + '-' + arch]}.nl_${language}:+"
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

    sources {

      source "${eclipseMirror}/eclipse//technology/epp/downloads/release/indigo/SR2/eclipse-jee-indigo-SR2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse/downloads/drops/R-3.7.2-201202080800/eclipse-SDK-3.7.2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "${eclipseArchiveMirror}/eclipse/downloads/drops/R-3.7.2-201202080800/eclipse-3.7.2-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/indigo/BabelLanguagePack-eclipse-${language}_3.7.0.v20131123061707.zip'
      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/indigo/BabelLanguagePack-rt.equinox-${language}_3.7.0.v20131123061707.zip'
    }
  }
  
  eclipseVersion('3.8.0') {
		
			extendsFrom '3.7.2'

			eclipseMavenGroup = 'eclipse-juno'
					
			sources {
				source "${eclipseArchiveMirror}/technology/epp/downloads/release/juno/R/eclipse-jee-juno-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"				
				source "${eclipseArchiveMirror}/eclipse/downloads/drops/R-3.8-201206081200/eclipse-SDK-3.8-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
				source "${eclipseArchiveMirror}/eclipse/downloads/drops/R-3.8-201206081200/eclipse-3.8-delta-pack.zip"

				//languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/indigo/BabelLanguagePack-eclipse-${language}_3.7.0.v20131123061707.zip'
				//languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/indigo/BabelLanguagePack-rt.equinox-${language}_3.7.0.v20131123061707.zip'
			}
		}

  eclipseVersion('4.2.1') {

    extendsFrom '3.7.2'

    eclipseMavenGroup = 'eclipse-juno-sr1'

    sources {

      source "${eclipseArchiveMirror}/technology/epp/downloads/release/juno/SR1/eclipse-jee-juno-SR1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.2.1-201209141800/eclipse-SDK-4.2.1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.2.1-201209141800/eclipse-4.2.1-delta-pack.zip"

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
        compile "${eclipseMavenGroup}:org.eclipse.e4.ui.model.workbench:+"
        compile "${eclipseMavenGroup}:org.eclipse.e4.ui.services:+"
        compile "${eclipseMavenGroup}:org.eclipse.e4.ui.workbench:+"
        compile "${eclipseMavenGroup}:org.eclipse.e4.core.di:+"
        compile "${eclipseMavenGroup}:org.eclipse.e4.ui.di:+"
        compile "${eclipseMavenGroup}:org.eclipse.e4.core.contexts:+"
        runtime "${eclipseMavenGroup}:org.eclipse.e4.ui.workbench.renderers.swt:+"
      }

      project.tasks.jar.manifest {
        instruction 'Require-Bundle', 'org.eclipse.e4.ui.model.workbench'
        instruction 'Require-Bundle', 'org.eclipse.e4.ui.services'
        instruction 'Require-Bundle', 'org.eclipse.e4.ui.workbench'
        instruction 'Require-Bundle', 'org.eclipse.e4.core.di'
        instruction 'Require-Bundle', 'org.eclipse.e4.ui.di'
        instruction 'Require-Bundle', 'org.eclipse.e4.core.contexts'
      }
    }
  }

  eclipseVersion('4.2.2') {

    extendsFrom '4.2.1'

    eclipseMavenGroup = 'eclipse-juno-sr2'

    sources {

      source "${eclipseMirror}/eclipse//technology/epp/downloads/release/juno/SR2/eclipse-jee-juno-SR2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.2.2-201302041200/eclipse-SDK-4.2.2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.2.2-201302041200/eclipse-4.2.2-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/juno/BabelLanguagePack-eclipse-${language}_4.2.0.v20131123041006.zip'
      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/juno/BabelLanguagePack-rt.equinox-${language}_4.2.0.v20131123041006.zip'
    }
  }

  eclipseVersion('4.3.1') {

    extendsFrom '4.2.2'

    eclipseMavenGroup = 'eclipse-kepler-sr1'

    sources {

      source "${eclipseArchiveMirror}/technology/epp/downloads/release/kepler/SR1/eclipse-jee-kepler-SR1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.3.1-201309111000/eclipse-SDK-4.3.1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.3.1-201309111000/eclipse-4.3.1-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/kepler/BabelLanguagePack-eclipse-${language}_4.3.0.v20131123020001.zip'
    }
  }

  eclipseVersion('4.3.2') {

    extendsFrom '4.3.1'

    eclipseMavenGroup = 'eclipse-kepler-sr2'

    sources {

      source "$eclipseMirror/eclipse//technology/epp/downloads/release/kepler/SR2/eclipse-jee-kepler-SR2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse//eclipse/downloads/drops4/R-4.3.2-201402211700/eclipse-SDK-4.3.2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "${eclipseArchiveMirror}//eclipse/downloads/drops4/R-4.3.2-201402211700/eclipse-4.3.2-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.11.1/kepler/BabelLanguagePack-eclipse-${language}_4.3.0.v20131123020001.zip'
    }
  }

  eclipseVersion('4.4') {

    extendsFrom '4.3.2'

    eclipseMavenGroup = 'eclipse-luna'

    sources {

      source "$eclipseMirror/eclipse//technology/epp/downloads/release/luna/R/eclipse-jee-luna-R-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.4-201406061215/eclipse-SDK-4.4-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.4-201406061215/eclipse-4.4-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.12.1/luna/BabelLanguagePack-eclipse-${language}_4.4.0.v20141223043836.zip'
    }

    rcpApp {

      project.dependencies {
        runtime "${eclipseMavenGroup}:javax.servlet:+"
        runtime "${eclipseMavenGroup}:org.eclipse.ant.core:+"
        runtime "${eclipseMavenGroup}:org.eclipse.core.variables:+"
        runtime "${eclipseMavenGroup}:org.eclipse.equinox.bidi:+"
        runtime "${eclipseMavenGroup}:org.eclipse.osgi.compatibility.state:+"
        runtime "${eclipseMavenGroup}:org.w3c.dom.events:+"
        runtime "${eclipseMavenGroup}:org.w3c.dom.smil:+"
        runtime "${eclipseMavenGroup}:org.w3c.dom.svg:+"
      }
    }
  }

  eclipseVersion('4.4.1') {
    extendsFrom '4.4'

    eclipseMavenGroup = 'eclipse-luna-sr1'

    sources {

      source "${eclipseMirror}/eclipse//technology/epp/downloads/release/luna/SR1/eclipse-jee-luna-SR1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.4.1-201409250400/eclipse-SDK-4.4.1-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.4.1-201409250400/eclipse-4.4.1-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.12.1/luna/BabelLanguagePack-eclipse-${language}_4.4.0.v20141223043836.zip'
    }
  }
  
  eclipseVersion('4.4.2') {
    extendsFrom '4.4'

    eclipseMavenGroup = 'eclipse-luna-sr2'

	// not available on all mirrors, but here:
	eclipseMirror = 'http://mirror.switch.ch'
	
    sources {

      source "${eclipseMirror}/eclipse/technology/epp/downloads/release/luna/SR2/eclipse-jee-luna-SR2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.4.2-201502041700/eclipse-SDK-4.4.2-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.4.2-201502041700/eclipse-4.4.2-delta-pack.zip"

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.12.1/luna/BabelLanguagePack-eclipse-${language}_4.4.0.v20141223043836.zip'
    }
  }
  
  eclipseVersion('4.5') {
    
    extendsFrom '4.4.2'

    eclipseMavenGroup = 'eclipse-mars'

    sources {

      source "${eclipseMirror}/eclipse//technology/epp/downloads/release/mars/R/eclipse-jee-mars-R-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}"
      source "${eclipseArchiveMirror}/eclipse/downloads/drops4/R-4.5-201506032000/eclipse-SDK-4.5-${suffix_os[current_os]}${suffix_arch[current_arch]}.${fileExt_os[current_os]}", sourcesOnly: true

      languagePackTemplate '${eclipseMirror}/eclipse//technology/babel/babel_language_packs/R0.13.0/mars/BabelLanguagePack-eclipse-${language}_4.5.0.v20150804081228.zip'
    }
  }  

  eclipseVersion('efxclipse-1.2') {
      eclipseMavenGroup = 'efxclipse-1_2'
      sources {
          source "http://download.eclipse.org/efxclipse/runtime-released/1.2.0/site_assembly.zip"
      }

      osgiBundle {
          project.dependencies {
              compile "${eclipseMavenGroup}:javax.inject:+"

              compile "${eclipseMavenGroup}:com.ibm.icu:+"

              compile "${eclipseMavenGroup}:org.eclipse.core.commands:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.contenttype:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.databinding:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.databinding.beans:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.databinding.observable:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.databinding.property:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.expressions:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.filesystem:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.jobs:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.resources:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.core.commands:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.core.contexts:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.core.di:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.core.di.extensions:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.core.services:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.ui.di:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.ui.model.workbench:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.ui.services:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.ui.workbench:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.javafx:+"

              compile "${eclipseMavenGroup}:org.eclipse.fx.core:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.core.databinding:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.core.di:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.core.fxml:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.emf.databinding:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.emf.edit.ui:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.javafx:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.osgi.util:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.controls:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.databinding:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.di:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.dialogs:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.keybindings:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.keybindings.e4:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.keybindings.generic:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.panes:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.services:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.theme:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.workbench.base:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.workbench.fx:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.workbench.renderers.base:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.workbench.renderers.fx:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.workbench.services:+"

              compile "${eclipseMavenGroup}:org.eclipse.emf.common:+"
              compile "${eclipseMavenGroup}:org.eclipse.emf.ecore:+"
              compile "${eclipseMavenGroup}:org.eclipse.emf.ecore.change:+"
              compile "${eclipseMavenGroup}:org.eclipse.emf.ecore.xmi:+"
              compile "${eclipseMavenGroup}:org.eclipse.emf.edit:+"

          }

      }

      efxclipseApp {
          project.ext.osgiExecutionEnvironment = 'JavaSE-1.6,J2SE-1.6,J2SE-1.5,J2SE-1.4,J2SE-1.3,J2SE-1.2,JRE-1.1,CDC-1.1/Foundation-1.1,CDC-1.0/Foundation-1.0,OSGi/Minimum-1.2,OSGi/Minimum-1.1,OSGi/Minimum-1.0'

          //Used only for the javax.annotation fix
          project.repositories {
              maven {
                  url 'http://dl.bintray.com/mcmil/maven'
              }
          }

          project.products.nativeLauncher = false

          project.dependencies {
              osgiExtension "${eclipseMavenGroup}:org.eclipse.fx.osgi:+"
              osgiExtension "pl.cmil.wuff.bundles:javax.annotation-osgi-extension:1.0"

              compile "${eclipseMavenGroup}:org.eclipse.equinox.app:+"
              runtime "${eclipseMavenGroup}:org.eclipse.equinox.ds:+"
              runtime "${eclipseMavenGroup}:org.eclipse.equinox.event:+"
              runtime "${eclipseMavenGroup}:org.eclipse.equinox.launcher:+"
              runtime "${eclipseMavenGroup}:org.eclipse.equinox.util:+"
              runtime "${eclipseMavenGroup}:org.eclipse.osgi.services:+"
              runtime "${eclipseMavenGroup}:com.ibm.icu:+"
              runtime "${eclipseMavenGroup}:javax.xml:+"

              compile "${eclipseMavenGroup}:com.ibm.icu:+"
              compile "${eclipseMavenGroup}:javax.inject:+"
              compile "${eclipseMavenGroup}:javax.servlet:+"
              compile "${eclipseMavenGroup}:javax.xml:+"

              compile "${eclipseMavenGroup}:org.apache.commons.lang:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.commands:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.contenttype:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.databinding:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.databinding.beans:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.databinding.observable:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.databinding.property:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.expressions:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.filesystem:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.jobs:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.resources:+"
              compile "${eclipseMavenGroup}:org.eclipse.core.runtime:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.core.commands:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.core.contexts:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.core.di:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.core.di.extensions:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.core.services:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.ui.di:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.ui.model.workbench:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.ui.services:+"
              compile "${eclipseMavenGroup}:org.eclipse.e4.ui.workbench:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.javafx:+"


              compile "${eclipseMavenGroup}:org.eclipse.fx.core:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.core.databinding:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.core.di:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.core.fxml:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.emf.databinding:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.emf.edit.ui:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.javafx:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.osgi.util:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.controls:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.databinding:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.di:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.dialogs:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.keybindings:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.keybindings.e4:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.keybindings.generic:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.panes:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.services:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.theme:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.workbench.base:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.workbench.fx:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.workbench.renderers.base:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.workbench.renderers.fx:+"
              compile "${eclipseMavenGroup}:org.eclipse.fx.ui.workbench.services:+"

              compile "${eclipseMavenGroup}:org.eclipse.emf.common:+"
              compile "${eclipseMavenGroup}:org.eclipse.emf.ecore:+"
              compile "${eclipseMavenGroup}:org.eclipse.emf.ecore.change:+"
              compile "${eclipseMavenGroup}:org.eclipse.emf.ecore.xmi:+"
              compile "${eclipseMavenGroup}:org.eclipse.emf.edit:+"

              runtime "${eclipseMavenGroup}:org.eclipse.fx.osgi:+"
              runtime "${eclipseMavenGroup}:org.eclipse.equinox.console:+"

              runtime "${eclipseMavenGroup}:org.apache.felix.gogo.runtime:+"
              runtime "${eclipseMavenGroup}:org.apache.felix.gogo.shell:+"
              runtime "${eclipseMavenGroup}:org.apache.felix.gogo.command:+"

              runtime "pl.cmil.wuff.bundles:javax.annotation-osgi-extension:1.0"
          }

          supported_oses.each { platform ->
              supported_archs.each { arch ->
                  String productConfigName = "product_equinox_${platform}_${arch}"
                  project.dependencies.add productConfigName, "${eclipseMavenGroup}:org.eclipse.equinox.launcher:+"

              }
          }


          project.tasks.jar.manifest {
              instruction 'Require-Bundle', 'org.eclipse.core.runtime'
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
