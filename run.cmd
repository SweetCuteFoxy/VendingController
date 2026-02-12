@echo off
set "JAVA_HOME=C:\Users\edikk\.jdks\axiomjdk-21.0.9"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JDK: %JAVA_HOME%
java -version
echo.
echo Running VendingController...
call mvnw.cmd javafx:run %*
