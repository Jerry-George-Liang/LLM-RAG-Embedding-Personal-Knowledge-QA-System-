@echo off
set "JAVA_HOME=C:\Program Files\Common Files\Oracle\Java\javapath_target_546734140"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
echo Using Java:
java -version
echo.
echo Starting Spring Boot (Heap: 1024MB)...
set "JAVA_TOOL_OPTIONS=-Xmx1024m -Xms512m"
call "E:\idea\Maven\apache-maven-3.6.1-bin\apache-maven-3.6.1\bin\mvn.cmd" spring-boot:run
pause
