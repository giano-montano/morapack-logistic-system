@echo off
set JAVA_HOME=D:\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%
call "%~dp0mvnw.cmd" %*
