param(
    [string]$CommandName = "search"
)

$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$jarPath = Join-Path $projectRoot "dist\local-search-engine-1.0.0.jar"
$commandDir = Join-Path $env:USERPROFILE "search-bin"
$commandPath = Join-Path $commandDir ($CommandName + ".cmd")

if (-not (Test-Path $jarPath))
{
    Write-Host "Build artifact not found. Building project..."
    Push-Location $projectRoot
    try
    {
        mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0)
        {
            throw "Maven build failed."
        }
    }
    finally
    {
        Pop-Location
    }
}

New-Item -ItemType Directory -Path $commandDir -Force | Out-Null

$launcher = @"
@echo off
java -jar "$jarPath" search %*
"@

Set-Content -Path $commandPath -Value $launcher -Encoding ASCII

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ([string]::IsNullOrWhiteSpace($userPath))
{
    $userPath = ""
}

$paths = $userPath.Split(";", [System.StringSplitOptions]::RemoveEmptyEntries)
$alreadyPresent = $paths | Where-Object { $_.Trim().ToLowerInvariant() -eq $commandDir.Trim().ToLowerInvariant() }

if (-not $alreadyPresent)
{
    $newPath = if ([string]::IsNullOrWhiteSpace($userPath)) { $commandDir } else { "$userPath;$commandDir" }
    [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
    Write-Host "Added to user PATH: $commandDir"
}
else
{
    Write-Host "PATH already contains: $commandDir"
}

Write-Host ""
Write-Host "Installed command: $CommandName"
Write-Host "Launcher file: $commandPath"
Write-Host "Open a NEW terminal and run:"
Write-Host "  $CommandName ""your query"""
Write-Host "Example:"
Write-Host "  $CommandName ""resume"" --limit 10 --explain"
