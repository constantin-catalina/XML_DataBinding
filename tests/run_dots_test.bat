@echo off
echo --- Compiling SchemaToClassGenerator.java ---
javac -Xlint:-options -d out -source 8 -target 8 src\SchemaToClassGenerator.java
if errorlevel 1 exit /b

echo --- Running SchemaToClassGenerator on books.xsd ---
java -cp out SchemaToClassGenerator input\dots.xsd
if errorlevel 1 exit /b

echo --- Compiling generated classes and MyXMLDataBinder.java ---
javac -Xlint:none -d bin -source 8 -target 8 output/*.java src/MyXMLDataBinder.java
if errorlevel 1 exit /b

echo --- Compiling TestDots.java ---
javac -cp bin -Xlint:none -d bin -source 8 -target 8 tests\TestDots.java
if errorlevel 1 exit /b

echo --- Running TestDots ---
java -cp bin TestDots