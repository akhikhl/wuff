# swtapp

Example of non-OSGi java application using SWT and JFace.

Demonstrates using gradle dependencies and wuff products. 
See more details in "build.gradle"

Also demonstrates product localization. Classes in swtlib and swtapp use
ResourceBundle to load externalized strings from property files.
Product launcher scripts pass current language to the program
as -Duser.language=$language.
