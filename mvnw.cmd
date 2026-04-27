@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM -----------------
@REM   JAVA_HOME - location of a JDK home dir, required when type is not JRE
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "BASE_DIR=%~dp0") ELSE SET "BASE_DIR=%__MVNW_ARG0_NAME__%"

@SET MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
@IF NOT "%MAVEN_PROJECTBASEDIR%"=="" GOTO endDetectBaseDir

@SET EXEC_DIR=%CD%
@SET WDIR=%EXEC_DIR%
:findBaseDir
@IF EXIST "%WDIR%\.mvn" SET "MAVEN_PROJECTBASEDIR=%WDIR%" & GOTO endDetectBaseDir
@cd ..
@IF "%WDIR%"=="%CD%" GOTO baseDirNotFound
@SET "WDIR=%CD%"
@GOTO findBaseDir

:baseDirNotFound
@SET "MAVEN_PROJECTBASEDIR=%EXEC_DIR%"
@cd "%EXEC_DIR%"
@GOTO endDetectBaseDir

:endDetectBaseDir
@cd "%BASE_DIR%"

@IF NOT EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
  @SET DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar

  @FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") DO (
    @IF "%%A"=="wrapperUrl" SET "DOWNLOAD_URL=%%B"
  )

  @ECHO Downloading Maven Wrapper JAR from %DOWNLOAD_URL%

  @IF "%MVNW_VERBOSE%"=="true" (
    @ECHO Destination: %MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar
  )

  @powershell -Command "&{"^
    "$webclient = new-object System.Net.WebClient;"^
    "if (-not ([string]::IsNullOrEmpty('%MVNW_USERNAME%') -and [string]::IsNullOrEmpty('%MVNW_PASSWORD%'))) {"^
    "$webclient.Credentials = new-object System.Net.NetworkCredential('%MVNW_USERNAME%', '%MVNW_PASSWORD%');"^
    "}"^
    "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;"^
    "$webclient.DownloadFile('%DOWNLOAD_URL%', '%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar')"^
    "}"
  IF "%ERRORLEVEL%"=="0" GOTO downloadSuccess
  DEL "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" >NUL 2>&1
  @ECHO ERROR: Failed to download maven-wrapper.jar >&2
  EXIT /B 1
  :downloadSuccess
)

@IF NOT EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
  @ECHO ERROR: Cannot find %MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar >&2
  EXIT /B 1
)

@SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
@IF NOT DEFINED JAVA_HOME (
  @SET JAVA_EXE=java.exe
) ELSE (
  @IF NOT EXIST "%JAVA_EXE%" (
    @ECHO ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% >&2
    @ECHO Please set the JAVA_HOME variable to match your Java installation. >&2
    EXIT /B 1
  )
)

@SET MAVEN_JAVA_EXE="%JAVA_EXE%"
@SET MAVEN_OPTS_EXTRA=
@IF EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\jvm.config" (
  @SET /P MAVEN_OPTS_EXTRA=<"%MAVEN_PROJECTBASEDIR%\.mvn\jvm.config"
)

%MAVEN_JAVA_EXE% %MAVEN_OPTS_EXTRA% %MAVEN_OPTS% %MAVEN_DEBUG_OPTS% ^
  -classpath "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  org.apache.maven.wrapper.MavenWrapperMain %*

IF ERRORLEVEL 1 EXIT /B 1
