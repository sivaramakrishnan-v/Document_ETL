$ErrorActionPreference = 'Stop'

$source = Join-Path $PSScriptRoot 'dist\document-etl-ui\browser'
$target = Resolve-Path (Join-Path $PSScriptRoot '..\src\main\resources\static')

if (-not (Test-Path $source)) {
    throw "Angular browser build output not found: $source"
}

Copy-Item -Path (Join-Path $source '*') -Destination $target -Recurse -Force
Write-Host "Copied Angular browser build to $target"
