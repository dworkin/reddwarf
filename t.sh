#!/bin/bash

rm -rf xt
mkdir xt
find test/shared/j2se -type f -name \*.java -print0 | xargs -0 javac -g -Xlint:all -source 1.5 -target 1.5 -d xt -implicit:none -sourcepath test/shared/j2se -cp jsr203.jar

