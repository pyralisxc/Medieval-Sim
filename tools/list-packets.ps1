# Simple script to list classes under build/mod that look like Packet subclasses by filename pattern
# This is a lightweight alternative to compiling and running a reflection-based Java tool.

$buildMod = Join-Path $PSScriptRoot '..\build\mod'
if (-Not (Test-Path $buildMod)) { Write-Error "build/mod directory not found. Run ./gradlew classes first."; exit 1 }

Get-ChildItem -Path $buildMod -Recurse -Filter "*.class" | ForEach-Object {
    $relative = $_.FullName.Substring($buildMod.Length + 1) -replace '\\','.' -replace '\.class$',''
    if ($relative -match '\.packets\.') {
        Write-Host $relative
    }
}
