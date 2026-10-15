# Verify bundled skill assets in a new fixture; never install them in the main mod.
[CmdletBinding()]
param([ValidateRange(30, 1800)][int]$TimeoutSeconds = 180)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$authoringRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$root = Join-Path $authoringRoot 'kit'
$fixture = Join-Path $authoringRoot ('.work/skill-validation/' + [Guid]::NewGuid().ToString('N'))
$skill = Join-Path $root '.agents/skills/forge-1122-mod'
$cases = New-Object 'System.Collections.Generic.List[object]'
$shell = (Get-Process -Id $PID).Path
function Invoke-Check {
    param([string]$Profile)
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $shell
    $start.Arguments = '-NoProfile -File "' + (Join-Path $fixture 'agent.ps1') + '" -Action check -Profile ' + $Profile + ' -TimeoutSeconds ' + $TimeoutSeconds
    $start.WorkingDirectory = $fixture
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $start
    try {
        if (-not $process.Start()) { throw 'Could not start fixture validation.' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $text = $stdout.GetAwaiter().GetResult()
        $errors = $stderr.GetAwaiter().GetResult()
        if (-not $text.Trim()) { throw "Fixture returned no JSON: $errors" }
        $result = $text | ConvertFrom-Json
        return $result
    } finally { $process.Dispose() }
}
try {
    New-Item -ItemType Directory -Force -Path $fixture | Out-Null
    foreach ($name in @('build.gradle', 'settings.gradle', 'gradlew', 'gradlew.bat', 'agent.ps1', 'gradle', 'testing')) {
        Copy-Item -LiteralPath (Join-Path $root $name) -Destination $fixture -Recurse
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'src'), (Join-Path $fixture 'tools') | Out-Null
    foreach ($tool in @('Record-Evidence.ps1','Export-Release.ps1')) { Copy-Item -LiteralPath (Join-Path $root ('tools/' + $tool)) -Destination (Join-Path $fixture 'tools') }
    Copy-Item -LiteralPath (Join-Path $root 'src/environmentCheck') -Destination (Join-Path $fixture 'src') -Recurse
    Copy-Item -LiteralPath (Join-Path $skill 'assets/starter/src/main') -Destination (Join-Path $fixture 'src') -Recurse
    $checksDir = Join-Path $fixture 'src/evaluation/java/com/example/skillprobechecks'
    New-Item -ItemType Directory -Force -Path $checksDir | Out-Null
    Copy-Item -LiteralPath (Join-Path $authoringRoot 'qa/fixtures/forge-skill/validation/SkillProbeChecks.java') -Destination $checksDir
    Set-Content -LiteralPath (Join-Path $fixture 'TASK.md') -Value 'Isolated bundled Forge skill example validation.'
    @'

sourceSets {
    evaluation {
        compileClasspath = sourceSets.main.output + sourceSets.main.compileClasspath
        runtimeClasspath = output + compileClasspath
    }
}
tasks.withType(JavaExec).configureEach {
    if (name == 'runServer') {
        dependsOn tasks.named('evaluationClasses')
        classpath += sourceSets.evaluation.output
        jvmArgs '-Dskillprobe.checks=true'
    }
}
'@ | Add-Content -LiteralPath (Join-Path $fixture 'build.gradle')
    $mod = Invoke-Check -Profile mod
    if ($mod.status -ne 'PASS' -or $mod.jarClassCount -lt 3) { throw "Example build failed: $($mod.message); log: $($mod.log)" }
    $cases.Add([ordered]@{ name = 'compile and reobfuscate example'; status = 'PASS'; runId = $mod.runId })
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jar = Get-ChildItem -LiteralPath (Join-Path $fixture 'build/libs') -Filter '*-srg.jar' | Select-Object -First 1
    $zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        foreach ($name in @('mcmod.info', 'pack.mcmeta', 'assets/skillprobe/lang/en_us.lang', 'assets/skillprobe/models/item/probe_token.json', 'assets/skillprobe/recipes/probe_token.json')) {
            $entry = $zip.GetEntry($name)
            if ($zip.Entries.FullName -match 'skillprobechecks|EnvironmentProbe') { throw 'Validation classes leaked into production JAR.' }
            if ($null -eq $entry) { throw "Missing packaged resource: $name" }
            if ($name.EndsWith('.json')) {
                $reader = New-Object IO.StreamReader($entry.Open())
                try { $reader.ReadToEnd() | ConvertFrom-Json | Out-Null } finally { $reader.Dispose() }
            }
        }
    } finally { $zip.Dispose() }
    $cases.Add([ordered]@{ name = 'model language and recipe packaged'; status = 'PASS' })
    $server = Invoke-Check -Profile server
    $serverLog = if ($server.log) { [IO.File]::ReadAllText($server.log) } else { '' }
    if ($server.status -ne 'PASS' -or $serverLog -notmatch 'SKILLPROBE_ASSERTIONS_PASS') {
        throw "Runtime assertions failed: $($server.message); log: $($server.log)"
    }
    $cases.Add([ordered]@{ name = 'runtime item stack limit and recipe assertions'; status = 'PASS'; runId = $server.runId })
    # Deliberately break the recipe, proving the checks detect a behavior regression.
    $recipePath = Join-Path $fixture 'src/main/resources/assets/skillprobe/recipes/probe_token.json'
    $recipe = [IO.File]::ReadAllText($recipePath) | ConvertFrom-Json
    $recipe.ingredients[1].item = 'minecraft:gold_ingot'
    $recipe | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $recipePath -Encoding ASCII
    $broken = Invoke-Check -Profile server
    $brokenLog = if ($broken.log) { [IO.File]::ReadAllText($broken.log) } else { '' }
    if ($broken.status -ne 'FAIL' -or $broken.exitCode -eq 0) { throw 'Broken recipe did not produce failed validation.' }
    if ($brokenLog -notmatch 'SKILLPROBE_ASSERTIONS_FAIL: Valid recipe inputs rejected' -or $brokenLog -match 'SKILLPROBE_ASSERTIONS_PASS') {
        throw "Broken-recipe test did not detect the expected regression; log: $($broken.log)"
    }
    $cases.Add([ordered]@{ name = 'broken recipe rejected by runtime assertion'; status = 'PASS'; runId = $broken.runId; serverProfileStatus = $broken.status })
    $diagnosticOutput = & $shell -NoProfile -File (Join-Path $fixture 'agent.ps1') -Action errors -RunId $broken.runId
    $diagnosticResult = ($diagnosticOutput -join "`n") | ConvertFrom-Json
    if (($diagnosticResult.diagnostics -join "`n") -notmatch 'ASSERTIONS_FAIL: Valid recipe inputs rejected') { throw 'Recipe failure omitted from bounded diagnostics.' }
    $cases.Add(@{ name='broken recipe cause returned'; status='PASS' })
    # Repair the exact failure and produce a final clean, deployable artifact.
    $recipe.ingredients[1].item = 'minecraft:diamond'
    $recipe | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $recipePath -Encoding ASCII
    $repairedMod = Invoke-Check -Profile mod
    if ($repairedMod.status -ne 'PASS' -or $repairedMod.exitCode -ne 0) { throw "Repaired mod failed: $($repairedMod.log)" }
    $repairedServer = Invoke-Check -Profile server
    if ($repairedServer.status -ne 'PASS' -or [IO.File]::ReadAllText($repairedServer.log) -notmatch 'SKILLPROBE_ASSERTIONS_PASS') { throw "Repaired behavior failed: $($repairedServer.log)" }
    $record = & $shell -NoProfile -File (Join-Path $fixture 'tools/Record-Evidence.ps1') -Capability behavior -Outcome PASS -Method log-marker -Observation 'Item registration, stack16, output, shapeless orders and invalid input assertions' -EvidenceFiles $repairedServer.log -Artifact $repairedMod.artifact.path -Marker 'SKILLPROBE_ASSERTIONS_PASS'
    if ($LASTEXITCODE -ne 0) { throw "Behavior recording failed: $record" }
    $releaseText = & $shell -NoProfile -File (Join-Path $fixture 'tools/Export-Release.ps1')
    if ($LASTEXITCODE -ne 0) { throw "Release export failed: $releaseText" }
    $release = ($releaseText -join "`n") | ConvertFrom-Json
    $cases.Add(@{ name='fresh behavior recording and release snapshot'; status='PASS'; release=$release.artifact })
    $cases.Add(@{ name='repair build and behavior'; status='PASS'; modRun=$repairedMod.runId; serverRun=$repairedServer.runId })
    $result = [ordered]@{ schemaVersion = 1; status = 'PASS'; cases = $cases; fixture = $fixture; artifact=$repairedMod.artifact; graphicalClientTested = $false }

    $exitCode = 0
} catch {
    $result = [ordered]@{ schemaVersion = 1; status = 'FAIL'; message = $_.Exception.Message; cases = $cases; fixture = $fixture }
    $exitCode = 1
}
$json = $result | ConvertTo-Json -Depth 6 -Compress
[IO.File]::WriteAllText((Join-Path $fixture 'skill-validation.json'), $json, (New-Object Text.UTF8Encoding($false)))
Write-Output $json
exit $exitCode
