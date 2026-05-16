@echo off
setlocal

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$here = Split-Path -Parent '%~f0';" ^
  "$binDir = Join-Path $here 'local-search';" ^
  "$wrapper = Join-Path $here 'search.cmd';" ^
  "Set-Content -Path $wrapper -Value ('@echo off' + [char]10 + '\"' + $binDir + '\local-search.exe\" %*') -Encoding ASCII;" ^
  "$cur = [Environment]::GetEnvironmentVariable('Path','User');" ^
  "if ($cur -notlike ('*' + $here + '*')) {" ^
  "  [Environment]::SetEnvironmentVariable('Path', $cur + ';' + $here, 'User');" ^
  "  Write-Host 'Added to PATH: ' $here" ^
  "} else { Write-Host 'PATH already contains this folder.' };" ^
  "Write-Host '';" ^
  "Write-Host 'Done! Open a NEW terminal and run:';" ^
  "Write-Host '  search ""your query""';" ^
  "Write-Host '  search ""resume"" --limit 10 --explain'"

echo.
pause
