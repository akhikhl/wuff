/*
 * wuff
 *
 * Copyright 2014  Andrey Hihlovskiy.
 *
 * See the file "LICENSE" for copying and usage permission.
 */
package org.akhikhl.wuff

/**
 *
 * @author akhikhl
 */
class EclipseFeatureExtension {

	private final String id
  String version
  String label
  String providerName
  String copyright
  String licenseUrl
  String licenseText
  String configuration
  
	final EclipseFeatureExtension defaultConfig
  
  EclipseFeatureExtension(String id, EclipseFeatureExtension defaultConfig) {
    this.id = id
    this.defaultConfig = defaultConfig
  }

  String getConfiguration() {
    configuration ?: defaultConfig?.configuration
  }

  String getId() {
    id ?: defaultConfig?.id
  }

  String getLabel() {
    label ?: defaultConfig?.label
  }
  
  String getVersion() {
    version ?: defaultConfig?.version
  }
  
  void setConfiguration(String newValue) {
    configuration = newValue
  }
  
  void setLabel(String newValue) {
    label = newValue
  }
  
  void setVersion(String newValue) {
    version = newValue
  }
}
