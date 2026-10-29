@echo off
setlocal
set "PYCMD="
where py >nul 2>nul
if errorlevel 1 goto trypython
py -3 -c "import sys; sys.exit(0 if sys.version_info >= (3,10) else 1)" >nul 2>nul
if errorlevel 1 goto trypython
set "PYCMD=py -3"
goto run
:trypython
where python >nul 2>nul
if errorlevel 1 goto nopython
python -c "import sys; sys.exit(0 if sys.version_info >= (3,10) else 1)" >nul 2>nul
if errorlevel 1 goto nopython
set "PYCMD=python"
goto run
:nopython
echo Python 3.10 or newer is required. Install it from https://www.python.org/downloads/ and run setup.cmd again.
exit /b 1
:run
%PYCMD% "%~dp0swarm.py" setup %*
exit /b %errorlevel%
