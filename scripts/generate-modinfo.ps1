Param(
    [string]$id = "medieval.sim",
    [string]$name = "Medieval Sim",
    [string]$version = "1.0",
    [string]$gameVersion = "1.0.1",
    [string]$description = "",
    [string]$author = "Pyralis",
    [switch]$UseCreateModInfoClass
)

# This helper runs the existing Gradle JavaExec task createModInfoFile, or directly runs CreateModInfoFile.main if -UseCreateModInfoClass is provided and Necesse.jar is available.

if ($UseCreateModInfoClass) {
    Write-Host "Direct invocation of CreateModInfoFile via java requires Necesse.jar on the classpath. Ensure build.gradle gameDirectory is correct."
    $gameDir = (Get-Content "build.gradle" -Raw) -match "def gameDirectory = \"([^"]+)\"" | Out-Null
    if ($Matches[1]) { $gd = $Matches[1] } else { $gd = "C:/Program Files (x86)/Steam/steamapps/common/Necesse" }
    $necJar = Join-Path $gd "Necesse.jar"
    if (-Not (Test-Path $necJar)) { Write-Error "Necesse.jar not found at $necJar"; exit 1 }
    $args = @(
        "-file", "${PWD}\build\mod\mod.info",
        "-id", $id,
        "-name", $name,
        "-version", $version,
        "-gameVersion", $gameVersion,
        "-description", $description,
        "-author", $author,
        "-clientside", "${false}"
    )
    & java -cp $necJar CreateModInfoFile @args
} else {
    Write-Host "Calling Gradle task createModInfoFile"
    & .\gradlew.bat createModInfoFile -PmodID=$id -PmodName=$name -PmodVersion=$version
}
