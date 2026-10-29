@echo off
where py >nul 2>nul
if errorlevel 1 goto nopy
py -3 "%~dp0..\swarm.py" %*
exit /b %errorlevel%
:nopy
python "%~dp0..\swarm.py" %*
exit /b %errorlevel%
