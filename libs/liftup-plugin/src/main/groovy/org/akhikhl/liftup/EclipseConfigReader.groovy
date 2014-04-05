/*
 * liftup
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.liftup

import org.codehaus.groovy.control.CompilerConfiguration

/**
 * Reads plugin configuration from the specified groovy script.
 * @author akhikhl
 */
class EclipseConfigReader {

  EclipseConfig readFromResource(String resourceName) {
    EclipseConfig config = new EclipseConfig()
    Binding binding = new Binding()
    binding.eclipse = { Closure closure ->
      closure.resolveStrategy = Closure.DELEGATE_FIRST
      closure.delegate = config
      closure()
    }
    GroovyShell shell = new GroovyShell(binding)
    this.getClass().getClassLoader().getResourceAsStream(resourceName).withReader('UTF-8') {
      shell.evaluate(it)
    }
    return config
  }
}

