wuff {
  defaultEclipseVersion = '4.3'

  eclipseVersion('4.3') {

    eclipseGroup = 'eclipse-kepler'

    moduleA {
      configure {}
    }

    moduleB {
      configure {}
      postConfigure {}
    }
  }
}