# What's new in Wuff

### Version 0.0.4

Fixed bugs:
- added missing dependency of equinox apps on org.eclipse.osgi.services.
- now writing property org.osgi.framework.executionenvironment to config.ini in order to fix loading of groovy-all 2.3.0.

### Version 0.0.3

- Fixed bug: generated OSGi-manifests of wrapper libraries did not contain proper Export-Package instruction.

### Version 0.0.2

- Wuff now uses Eclipse-specific file "build.properties" and user-supplied MANIFEST.MF to adjust source directories, dependencies and other info.
  Added tutorial on [converting existing Eclipse plugins and apps to Gradle](../../wiki/Convert-existing-Eclipse-plugins-and-apps-to-Gradle).

### Version 0.0.1

- Everything is new in this version! Please read [wiki pages](../../wiki) to discover what you can do with Wuff.
