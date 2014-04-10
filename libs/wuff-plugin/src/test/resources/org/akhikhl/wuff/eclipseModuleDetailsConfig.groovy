wuff {
  defaultEclipseVersion = '4.3'

  eclipseVersion('4.3') {

    eclipseMavenGroup = 'eclipse-kepler'

    moduleA {
      configure {}
    }

    moduleB {
      configure {}
      postConfigure {}
    }
  }
}