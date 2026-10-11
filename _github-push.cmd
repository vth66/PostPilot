@echo off
setlocal
rem ---------------------------------------------------------------------------
rem  PostPilot -> GitHub
rem
rem  Voraussetzung: Auf https://github.com/new ein LEERES Repository "PostPilot"
rem  anlegen (privat, ohne README, ohne .gitignore, ohne Lizenz). Leer ist
rem  wichtig: Sonst weist GitHub den ersten Push ab, weil beide Seiten eine
rem  eigene Historie haetten.
rem
rem  Dann diese Datei doppelklicken. Beim ersten Push fragt Git nach dem
rem  GitHub-Login (Browser-Fenster oder Benutzername + Personal Access Token).
rem ---------------------------------------------------------------------------

cd /d "%~dp0"

where git >nul 2>&1
if errorlevel 1 (
  echo FEHLER: Git wurde nicht gefunden. Git fuer Windows installieren:
  echo         https://git-scm.com/download/win
  goto :ende
)

if not exist ".git" (
  echo === Repository anlegen ===
  git init -b main || goto :fehler
) else (
  echo === Repository ist schon da, weiter ===
)

git config user.name  "Volker Thomas"
git config user.email "volker.thomas@gmx.net"

echo.
echo === Dateien aufnehmen ===
git add -A || goto :fehler

rem Nur committen, wenn es etwas zu committen gibt.
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "PostPilot: Arbeitsstand bleibt erhalten, Text wandert mit in den Export" || goto :fehler
) else (
  echo nichts Neues zu committen
)

echo.
echo === Gegenstelle setzen ===
git remote remove origin 2>nul
git remote add origin https://github.com/vth66/PostPilot.git || goto :fehler

echo.
echo === Hochladen ===
git push -u origin main || goto :fehler

echo.
echo FERTIG. https://github.com/vth66/PostPilot
goto :ende

:fehler
echo.
echo ABGEBROCHEN - die Meldung darueber sagt, woran es lag.

:ende
echo.
pause
