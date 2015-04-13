![Wuff logo](media/logo.png "Wuff logo")

[![Build Status](https://travis-ci.org/akhikhl/wuff.png?branch=master)](https://travis-ci.org/akhikhl/wuff)
[![Release](http://img.shields.io/badge/release-0.0.12-47b31f.svg)](https://github.com/akhikhl/wuff/releases/latest)
[![Snapshot](http://img.shields.io/badge/current-0.0.13--SNAPSHOT-47b31f.svg)](https://github.com/akhikhl/wuff/tree/master)
[![License](http://img.shields.io/badge/license-MIT-47b31f.svg)](#copyright-and-license)

### Introduction

Wuff is a gradle plugin for developing and assembling OSGi/Eclipse applications and plugins *independently* of Eclipse-IDE. If you are familiar with [Eclipse Tycho](https://www.eclipse.org/tycho/), then think of Wuff as a gradle-based alternative.

#### :star: What's new

- 0.0.13: support of Eclipse 4.4.2.

- 0.0.13: new feature: generation of Eclipse Features and Repositories. See examples of code and documentation at: https://github.com/akhikhl/wuff-sandbox

- 0.0.13: Wuff configuration now supports "file:///..." URLs as sources of mavenized bundles.

- 0.0.13: Wuff now supports scaffolding and starting e4 model-driven applications.

- 0.0.13: fixed groovy-all version compatibility, upgraded to unpuzzle 0.0.18.

- From now on I'll push snapshot versions of Wuff to jfrog snapshot repository. If you want to use snapshot versions, please add the following to your build script:

```groovy
buildscript {
  repositories {
    jcenter()
    maven { url 'http://oss.jfrog.org/artifactory/oss-snapshot-local' }
  }

  dependencies {
    classpath 'org.akhikhl.wuff:wuff-plugin:0.0.13-SNAPSHOT'
  }
}

repositories {
  jcenter()
  maven { url 'http://oss.jfrog.org/artifactory/oss-snapshot-local' }
}

apply plugin: 'java'
apply plugin: 'org.akhikhl.wuff.eclipse-rcp-app'
```

See complete list of changes in [what's new list](whatsnew.md),

### Where to start?

- If you are new to Wuff, start your acquaintance with [main features](../../wiki/Main-features).

- If you want to create something from scratch quickly, take a look at the tutorials: 
[first Equinox app](../../wiki/Create-first-Equinox-app), [first RCP app](../../wiki/Create-first-RCP-app) and [first IDE app](../../wiki/Create-first-IDE-app).

- If you already have bunch of existing Eclipse plugins and apps, consider [converting them to Gradle/Wuff](../../wiki/Convert-existing-Eclipse-plugins-and-apps-to-Gradle).

- If you want to learn all about Wuff systematically, read [wiki pages](../../wiki).

- If you already use Wuff, it is always a good idea to look in [what's new](whatsnew.md) file.

### How to use Wuff?

- Maven artifacts: 'org.akhikhl.wuff:wuff-plugin:+' at jcenter and maven central.
- Source code: compile, explore, deploy.

See more information on [prerequisites and usage](../../wiki/Prerequisites-and-usage) wiki page.

### Copyright and License

Copyright 2014-2015 (c) Andrey Hihlovskiy and contributors

All versions, present and past, of Wuff are licensed under [MIT license](LICENSE).

[![Support via Gittip](https://rawgithub.com/twolfson/gittip-badge/0.2.0/dist/gittip.png)](https://www.gittip.com/akhikhl/)
