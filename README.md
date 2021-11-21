# Pictet
Near Tycho, but does not overlap Tycho ;-)

# Goals
'''set-compile-path'''
Runs on phase <i>generate-sources</i>.
Reads compile-time dependencies from the pom.xml, tries to resolve them from the local Maven repository, and sets the project property <i>java.compile.classpath</i> accordingly.
