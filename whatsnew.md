# What's new in Wuff


### Version 0.0.16

- Now Wuff supports Eclipse Mars 4.5. This is the default Eclipse version used by Wuff. You can always switch to older version of Eclipse by setting a property in your build.gradle:

```groovy
wuff {
selectedEclipseVersion = '4.4.2'
}
```

### Version 0.0.14

- New feature: support of native launchers. Wuff automatically generates native launcher as soon as you build Eclipse-RCP or Eclipse-IDE product.

- New feature: E(fx)clipse support.

Resolved issue #66: Remove 'Require-Bundle: org.eclipse.osgi'

Resolved issue #56: Invalid product and application in configuration ini file when Manifest provide custom Bundle-SymbolicName

### Version 0.0.13

- support of Eclipse 4.4.2.

- new feature: generation of Eclipse Features and Repositories. See examples of code and documentation at: https://github.com/akhikhl/wuff-sandbox

- Wuff configuration now supports "file:///..." URLs as sources of mavenized bundles.

- Wuff now supports scaffolding and starting e4 model-driven applications.

- fixed groovy-all version compatibility, upgraded to unpuzzle 0.0.18.

### Version 0.0.12

- Fixed compatibility with JDK6 (thanks to @jstarry for contribution).

### Version 0.0.11

- Introduced "white-listed" eclipse bundles when translating MANIFEST.MF bundles to gradle dependencies (contribution by @jstarry).

### Version 0.0.10

- Wuff supports Eclipse 4.4 Luna (it is even the default platform).
- Wuff supports generating products for Mac OS X.

### Version 0.0.7

Improvements:
- implemented Mac OSX support
- moved wuff-specific maven repository to $HOME/.wuff/m2_repository

### Version 0.0.6

Bug fixes:
- fixed Windows-specific bug ""Could not normalize path" in OsgiBundleConfigurer.

### Version 0.0.5

Improvements:
- new task "uninstallAllEclipseVersions" was implemented.

Bug fixes:
- task "cleanEclipse" was renamed to "purgeEclipse" to avoid conflict with other similarly named task from another gradle plugin.

### Version 0.0.4

Improvements:
- org.osgi.framework.executionenvironment could be set in wuff config via project.ext.osgiExecutionEnvironment in concrete module (equinoxApp, for example).

Bug fixes:
- added missing dependency of equinox apps on org.eclipse.osgi.services.
- now writing property org.osgi.framework.executionenvironment to config.ini in order to fix loading of groovy-all 2.3.0.
- fixed incompatibility between eclipse 3.7.x and eclipse 4.x: rcp/ide application would not start because of the missing bundle org.eclipse.ui.views, 
  if some bundle implements org.eclipse.ui.views extension point. Fix: org.eclipse.ui.views bundle is now automatically added to Required-Bundle and dependencies as needed.

### Version 0.0.3

- Fixed bug: generated OSGi-manifests of wrapper libraries did not contain proper Export-Package instruction.

### Version 0.0.2

- Wuff now uses Eclipse-specific file "build.properties" and user-supplied MANIFEST.MF to adjust source directories, dependencies and other info.
  Added tutorial on [converting existing Eclipse plugins and apps to Gradle](../../wiki/Convert-existing-Eclipse-plugins-and-apps-to-Gradle).

### Version 0.0.1

- Everything is new in this version! Please read [wiki pages](../../wiki) to discover what you can do with Wuff.
