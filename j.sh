#!/bin/bash

rm -rf x
mkdir x
find src/shared/j2se -type f -name \*.java -print0 | xargs -0 javac -g -Xlint:all -source 1.5 -target 1.5 -d x -implicit:none -sourcepath src/shared/j2se

cd x
find . -type f -name \*.class -print0 | xargs -0 jar cf ../jsr203.jar
cd ..

rm -rf x
