# Pictet
Pictet is a moon crater near Tycho, but much smaller than Tycho, and the two don't overlap.

# Goals
'''set-compile-path'''
Runs on phase <i>generate-sources</i>.
Reads compile-time dependencies from the pom.xml, tries to resolve them from the local Maven repository, and sets the project property <i>java.compile.classpath</i> accordingly.
