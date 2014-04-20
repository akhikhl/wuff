# EquinoxAppMinimal

Example of the minimal equinox application.

Please note, that it is console application (has no GUI).

When you run this application, it prints to stdout:

*Hello, world! I am equinox application!*

Here is how you create such application from scratch, in isolated environment:

1. Create file "build.gradle", insert code into it:

  ```groovy
  buildscript {
    repositories {
      mavenLocal()
      jcenter()
    }
    
    dependencies {
      classpath 'org.akhikhl.wuff:wuff-plugin:0.0.1'
    }
  }

  apply plugin: 'java'
  apply plugin: 'eclipse-equinox-app'

  repositories {
    mavenLocal()
    jcenter()
  }
  ```

  The script describes that we are using wuff-plugin library
  and that we apply "eclipse-equinox-app" plugin to this project.

2. Invoke on command line:

  ```shell
  gradle scaffold
  ```

  Scaffold task creates Application class required by equinox library.

3. Invoke on command line:

  ```shell
  gradle build
  ```

  Build task generates product in "build/output" folder.

You can run the program either by invoking launch script within the product
or by invoking "gradle run" in project's directory.
