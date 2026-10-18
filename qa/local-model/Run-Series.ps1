# Runs held-out trial prompts unattended: fresh kit copy per trial, the agent, at most one nudge, the
# feedback prompt, an independent `check game`, Read-Trial, and one summary row per trial.
# Usage (from the repo root; the local model server must be running for -Agent opencode):
#   powershell -NoProfile -File qa/local-model/Run-Series.ps1 -Prompts qa/local-model/PROMPTS-3.md -Ids S0,S1 -Runs 2
#   ... -Agent claude -Model sonnet          (control run with Claude Code; uses the user's Claude plan)
#   ... -Context 65536                       (64k regression pass)
# Results: <Out>/<series>-<agent>/<id>-r<n>/ (the trial folder, extracted outside the repo) and summary.md beside them.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Prompts,
    [string[]]$Ids,
    [ValidateRange(1, 10)][int]$Runs = 1,
    [ValidateSet('opencode', 'claude')][string]$Agent = 'opencode',
    [string]$Model,
    [string]$Variant = 'high',
    [int]$Context = 131072,
    [int]$Output = 32768,
    [ValidateRange(5, 600)][int]$MinutesPerStep = 120,
    [ValidateRange(0, 3)][int]$Nudges = 1,
    [string]$Out,
    [string]$Zip
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$version = ([IO.File]::ReadAllText((Join-Path $repo 'kit/kit.json')) | ConvertFrom-Json).version
if (-not $Zip) { $Zip = (Get-ChildItem -Path (Join-Path $repo "releases/$version") -Filter '*.zip' | Select-Object -First 1).FullName }
if (-not $Zip -or -not [IO.File]::Exists($Zip)) { throw "No kit ZIP for ${version}: run packaging/Build-KitZip.ps1 first, or pass -Zip." }
if (-not $Model) { $Model = if ($Agent -eq 'opencode') { 'local/qwen3.8-flash-next-iq3_xxs' } else { 'sonnet' } }
$series = [IO.Path]::GetFileNameWithoutExtension($Prompts) -replace '^PROMPTS-', 'series'
if (-not $Out) { $Out = Join-Path (Split-Path -Parent $repo) 'kit-trials' }
$root = Join-Path ([IO.Path]::GetFullPath($Out)) "$series-$Agent-$version"
if ($root.StartsWith($repo, [StringComparison]::OrdinalIgnoreCase)) { throw 'Trials must run outside the repository (the agent must not find maintainer files).' }
New-Item -ItemType Directory -Force -Path $root | Out-Null

# Prompts: "## S3 - Title" sections, each with one "**Prompt:** ..." paragraph.
$text = [IO.File]::ReadAllText((Resolve-Path $Prompts))
$all = [ordered]@{}
foreach ($m in [regex]::Matches($text, '(?ms)^## (\S+) - [^\r\n]*\r?\n(.*?)(?=^## |\z)')) {
    $p = [regex]::Match($m.Groups[2].Value, '(?ms)^\*\*Prompt:\*\*\s*(.+?)\s*(?=^\*\*|\z)')
    if ($p.Success) { $all[$m.Groups[1].Value] = ($p.Groups[1].Value -replace '\s+', ' ').Trim() }
}
if (-not $Ids) { $Ids = @($all.Keys) }
foreach ($id in $Ids) { if (-not $all.Contains($id)) { throw "No prompt $id in $Prompts (have: $(@($all.Keys) -join ', '))" } }

$feedback = @'
That works, thanks. This was a test of the kit itself, which I'm still developing. Please give honest feedback on the kit, not on the mod. Don't change any files except the one below.
1. Where did you spend the most time or tokens, and why? Point to specific moments: commands, errors, files you read.
2. Where did you guess or rely on memory instead of the kit? Which of those guesses turned out wrong?
3. Was anything in the kit's docs or tool output missing, unclear, or wrong for this task? Quote the line if you can.
4. Which of your problems came from the kit, and which from your own reasoning? Say which is which.
5. What worked well and should stay as it is?
Rank the problems by what they cost you. For each one, suggest the smallest change to the kit that would have prevented it.
Write all of it to feedback.md in the project root.
'@
$nudgeText = 'Please keep going until ./agent.ps1 check game passes, or finish with a partial report as AGENTS.md step 7 describes.'
$answerText = "I don't mind either way: decide, note the decision in TASK.md, and carry on."

if ($Agent -eq 'opencode') {
    try { Invoke-RestMethod -Uri 'http://127.0.0.1:8080/v1/models' -TimeoutSec 10 | Out-Null } catch { throw 'The local model server is not answering on http://127.0.0.1:8080 (start Strata first).' }
    # Per-run limits without touching the global config; a private server (--standalone) so the variable applies.
    $modelId = ($Model -split '/', 2)[1]
    $env:OPENCODE_CONFIG_CONTENT = (@{ provider = @{ local = @{ models = @{ $modelId = @{ limit = @{ context = $Context; output = $Output } } } } } } | ConvertTo-Json -Depth 8 -Compress)
}

# One agent turn; returns @{ session; text; timedOut }.
function Invoke-AgentTurn([string]$Dir, [string]$Message, [string]$Session, [string]$Log) {
    $start = New-Object Diagnostics.ProcessStartInfo
    # OpenCode takes its project folder from PWD (which Git Bash exports), not from the working directory: set both.
    $start.EnvironmentVariables['PWD'] = $Dir
    $start.WorkingDirectory = $Dir; $start.UseShellExecute = $false; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true; $start.CreateNoWindow = $true
    $quoted = '"' + ($Message -replace '"', '\"') + '"'
    if ($Agent -eq 'opencode') {
        # The exe itself: the .cmd launcher would let cmd.exe mangle multi-line prompts.
        $start.FileName = Join-Path (Split-Path -Parent (Get-Command opencode.cmd).Source) 'node_modules\@opencode\cli\bin\opencode.exe'
        $start.Arguments = "run --standalone --auto --format json --model `"$Model#$Variant`"" + $(if ($Session) { " --session $Session" } else { " --title `"$(Split-Path -Leaf $Dir)`"" }) + " $quoted"
    } else {
        $start.FileName = (Get-Command claude).Source
        $start.Arguments = "-p --output-format json --permission-mode bypassPermissions --model $Model" + $(if ($Session) { " --resume $Session" } else { '' }) + " $quoted"
    }
    $p = [Diagnostics.Process]::Start($start)
    $stdout = $p.StandardOutput.ReadToEndAsync(); $stderr = $p.StandardError.ReadToEndAsync()
    $timedOut = -not $p.WaitForExit($MinutesPerStep * 60000)
    if ($timedOut) { & taskkill.exe /PID $p.Id /T /F *> $null; $p.WaitForExit() }
    $raw = $stdout.Result
    [IO.File]::AppendAllText($Log, $raw + "`n", (New-Object Text.UTF8Encoding($false)))
    $result = @{ session = $Session; text = ''; timedOut = $timedOut }
    if ($Agent -eq 'opencode') {
        foreach ($line in ($raw -split "`n")) {
            if (-not $line.Trim().StartsWith('{')) { continue }
            try { $e = $line | ConvertFrom-Json } catch { continue }
            if ($e.sessionID) { $result.session = $e.sessionID }
            if ($e.type -eq 'text' -and $e.part.text) { $result.text = $e.part.text }
        }
    } else {
        try { $j = $raw | ConvertFrom-Json; $result.session = $j.session_id; $result.text = [string]$j.result } catch { }
    }
    return $result
}

function Invoke-Kit([string]$Dir, [string[]]$KitArgs) {
    $ErrorActionPreference = 'Continue'
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Dir 'agent.ps1') @KitArgs 2>$null
    return @{ exit = $LASTEXITCODE; text = ($output -join "`n") }
}

if ($Agent -eq 'opencode') {
    # Pre-flight: a one-word turn from a scratch folder must be recorded in that folder, or nothing runs.
    $probe = Join-Path $root '.probe'
    New-Item -ItemType Directory -Force -Path $probe | Out-Null
    $answer = Invoke-AgentTurn $probe 'Reply with the single word OK.' $null (Join-Path $probe 'probe.jsonl')
    $where = (& python -I (Join-Path $PSScriptRoot 'Read-OpenCode.py') --session-dir $answer.session x) -join ''
    if (-not $answer.session -or [IO.Path]::GetFullPath($where.Trim()) -ne [IO.Path]::GetFullPath($probe)) { throw "Pre-flight: OpenCode worked in '$where', not in $probe. Nothing was run." }
    Write-Host "pre-flight ok: OpenCode works in the trial folder"
}

$summary = Join-Path $root 'summary.md'
if (-not [IO.File]::Exists($summary)) {
    [IO.File]::WriteAllText($summary, "# $series, $Agent ($Model), kit $version, context $Context`n`n| Trial | check game | Agent min | Nudges | Questions | Timed out | Folder |`n|---|---|---|---|---|---|---|`n", (New-Object Text.UTF8Encoding($false)))
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($id in $Ids) {
    for ($n = 1; $n -le $Runs; $n++) {
        $name = "$id-r$n"; $dir = Join-Path $root $name
        if ([IO.Directory]::Exists($dir)) { Write-Host "skip $name (exists)"; continue }
        Write-Host "== ${name}: extracting $(Split-Path -Leaf $Zip)"
        $tmp = Join-Path $root ".extract-$name"
        [IO.Compression.ZipFile]::ExtractToDirectory($Zip, $tmp)
        Move-Item -LiteralPath (Get-ChildItem -LiteralPath $tmp -Directory | Select-Object -First 1).FullName -Destination $dir
        Remove-Item -LiteralPath $tmp -Recurse -Force
        $log = Join-Path $root "$name.agent.jsonl"
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $questions = 0; $nudged = 0; $timedOut = $false
        Write-Host "   prompt sent ($Agent $Model)"
        $turn = Invoke-AgentTurn $dir $all[$id] $null $log
        $timedOut = $turn.timedOut
        if ($Agent -eq 'opencode') {
            # Never let a trial agent work in another folder (the repo holds the held-out prompts).
            $where = (& python -I (Join-Path $PSScriptRoot 'Read-OpenCode.py') --session-dir $turn.session x) -join ''
            if ([IO.Path]::GetFullPath($where.Trim()) -ne [IO.Path]::GetFullPath($dir)) { throw "Trial $name ran in '$where', not in its trial folder: series stopped. Check that folder for changes." }
        }
        while (-not $timedOut) {
            # A question gets the standard answer; a run without a passing game check gets one nudge.
            if ($turn.text.TrimEnd().EndsWith('?') -and $questions -lt 2) { $questions++; $turn = Invoke-AgentTurn $dir $answerText $turn.session $log; $timedOut = $turn.timedOut; continue }
            if ($nudged -lt $Nudges -and (Invoke-Kit $dir @('check', 'game')).exit -ne 0) { $nudged++; $turn = Invoke-AgentTurn $dir $nudgeText $turn.session $log; $timedOut = $turn.timedOut; continue }
            break
        }
        $minutes = [int]$watch.Elapsed.TotalMinutes
        if ($turn.session -and -not $timedOut) { Write-Host '   feedback'; Invoke-AgentTurn $dir $feedback $turn.session $log | Out-Null }
        Write-Host '   independent check game'
        $check = Invoke-Kit $dir @('check', 'game')
        $verdict = (($check.text -split "`n") | Select-Object -First 1)
        $trial = & powershell -NoProfile -File (Join-Path $PSScriptRoot 'Read-Trial.ps1') -Path $dir 2>&1
        [IO.File]::WriteAllText((Join-Path $root "$name.trial.txt"), "check game: $verdict`n`n" + ($trial -join "`n") + "`n", (New-Object Text.UTF8Encoding($false)))
        $row = "| $name | $verdict | $minutes | $nudged | $questions | $(if ($timedOut) { 'yes' } else { 'no' }) | $name |`n"
        [IO.File]::AppendAllText($summary, $row, (New-Object Text.UTF8Encoding($false)))
        Write-Host "   $verdict ($minutes min)"
    }
}
Write-Host "summary: $summary"
