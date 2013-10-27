#!/bin/bash


version=`cat "$JAVA_HOME/jre/lib/javafx.properties" | sed 's/.*=\(.*\)/\1/'`
mvn install:install-file -Dfile="$JAVA_HOME\jre\lib\jfxrt.jar" -DgroupId=com.oracle.javafx -DartifactId=javafx -Dversion=$version -Dpackaging=jar
