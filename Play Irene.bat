@echo off
setlocal
cd /d "%~dp0"
set JAVA=java
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not exist "lwjgl3\build\libs\Irene.jar" (
  echo Building Irene the first time, this takes a minute...
  call gradlew.bat lwjgl3:dist
  if errorlevel 1 goto fail
)
"%JAVA%" -jar "lwjgl3\build\libs\Irene.jar" %*
if errorlevel 1 goto fail
goto end
:fail
echo.
echo Something went wrong. You need Java 17 or newer (Android Studio's bundled JBR works).
pause
:end
