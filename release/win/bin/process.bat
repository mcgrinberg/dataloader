@echo off
if not [%1]==[] goto run
echo.
echo Usage: process ^<configuration directory^> ^[batch process bean id^]
echo.
echo      configuration directory -- required -- directory that contains configuration files,
echo          i.e. config.properties, process-conf.xml, database-conf.xml
echo.
echo      batch process bean id -- optional -- id of a batch process bean in process-conf.xml,
echo          for example:
echo.
echo              process ../myconfigdir AccountInsert
echo.
echo          If process bean id is not specified, the value of the property process.name in config.properties
echo          will be used to run the process instead of process-conf.xml,
echo          for example:
echo.
echo              process ../myconfigdir
echo.

goto end

:run
set EXE_PATH=%~dp0
set DATALOADER_VERSION=@@FULL_VERSION@@

set BATCH_PROCESS_BEAN_ID_OPTION=
if not [%2]==[] set BATCH_PROCESS_BEAN_ID_OPTION=process.name=%2

CALL ..\dataloader.bat -skipbanner run.mode=batch salesforce.config.dir=%1 %BATCH_PROCESS_BEAN_ID_OPTION%

:end
exit /b %errorlevel%
