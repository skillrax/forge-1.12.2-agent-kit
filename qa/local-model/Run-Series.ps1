# Runs held-out trial prompts unattended: fresh kit copy per trial, the agent, at most one nudge, the
# feedback prompt, an independent `check game`, Read-Trial, the hidden acceptance tests (qa/acceptance; the agent
# never sees them) and one summary row per trial.
# Usage (from the repo root; the local model server must be running for -Agent opencode):
#   powershell -NoProfile -File qa/local-model/Run-Series.ps1 -Prompts qa/local-model/PROMPTS-3.md -Ids S0,S1 -Runs 2
#   ... -Agent claude -Model sonnet          (control run with Claude Code; uses the user's Claude plan)
#   ... -Context 65536 -Buffer 20000         (64k regression pass)
# Results: <Out>/<series>-<agent>/<id>-r<n>/ (the trial folder, extracted outside the repo) and summary.md beside them;
# <id>-r<n>.acceptance.json has each acceptance test's result.
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
    [int]$Buffer = 32768,                               # room left for the compaction summary (thinking included)
    [ValidateRange(5, 600)][int]$TrialMinutes = 90,     # all work turns of one trial together (feedback has its own)
    [ValidateRange(5, 120)][int]$IdleMinutes = 20,      # no agent.ps1 call and no changed file for this long: stop
    [ValidateRange(2, 60)][int]$FeedbackMinutes = 15,
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
    # Compaction summarizes the whole context in what is left of it: with a small buffer a thinking model runs out
    # mid-summary ("Compaction summary reached the output token limit") and every later turn fails the same way.
    # Text-only input: the server has no vision, and an image read (a texture) fails the request.
    $env:OPENCODE_CONFIG_CONTENT = (@{
        compaction = @{ auto = $true; keep = @{ tokens = 12000 }; buffer = $Buffer }
        provider = @{ local = @{ models = @{ $modelId = @{ limit = @{ context = $Context; output = $Output }; modalities = @{ input = @('text'); output = @('text') } } } } }
    } | ConvertTo-Json -Depth 8 -Compress)
}

# Last sign of work in a trial folder: the kit's call log or any file the agent wrote (build output and reports excluded).
function Get-LastActivity([string]$Dir) {
    $last = [DateTime]::MinValue
    $log = Join-Path $Dir 'reports/agent-log.jsonl'
    if ([IO.File]::Exists($log)) { $last = [IO.File]::GetLastWriteTimeUtc($log) }
    foreach ($sub in @('src', 'TASK.md', 'plan.txt')) {
        $path = Join-Path $Dir $sub
        if ([IO.File]::Exists($path)) { $t = [IO.File]::GetLastWriteTimeUtc($path); if ($t -gt $last) { $last = $t } }
        elseif ([IO.Directory]::Exists($path)) {
            foreach ($f in [IO.Directory]::EnumerateFiles($path, '*', [IO.SearchOption]::AllDirectories)) { $t = [IO.File]::GetLastWriteTimeUtc($f); if ($t -gt $last) { $last = $t } }
        }
    }
    return $last
}

# One agent turn until it ends, the deadline passes or the trial shows no activity for IdleMinutes.
# Returns @{ session; text; error (last OpenCode error type); stopped = '' | 'time budget' | 'no progress' }.
function Invoke-AgentTurn([string]$Dir, [string]$Message, [string]$Session, [string]$Log, [DateTime]$Deadline) {
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
    $stopped = ''
    $turnStart = [DateTime]::UtcNow
    while (-not $p.WaitForExit(30000)) {
        $now = [DateTime]::UtcNow
        $active = Get-LastActivity $Dir
        if ($active -lt $turnStart) { $active = $turnStart }
        if ($now -gt $Deadline.ToUniversalTime()) { $stopped = 'time budget' }
        elseif (($now - $active).TotalMinutes -gt $IdleMinutes) { $stopped = 'no progress' }
        if ($stopped) { & taskkill.exe /PID $p.Id /T /F *> $null; $p.WaitForExit(); break }
    }
    $raw = $stdout.Result
    [IO.File]::AppendAllText($Log, $raw + "`n", (New-Object Text.UTF8Encoding($false)))
    $result = @{ session = $Session; text = ''; error = ''; stopped = $stopped }
    if ($Agent -eq 'opencode') {
        foreach ($line in ($raw -split "`n")) {
            if (-not $line.Trim().StartsWith('{')) { continue }
            try { $e = $line | ConvertFrom-Json } catch { continue }
            if ($e.sessionID) { $result.session = $e.sessionID }
            if ($e.type -eq 'text' -and $e.part.text) { $result.text = $e.part.text; $result.error = '' }
            if ($e.type -eq 'error' -and $e.error) { $result.error = [string]$e.error.type }
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
    $answer = Invoke-AgentTurn $probe 'Reply with the single word OK.' $null (Join-Path $probe 'probe.jsonl') ([DateTime]::Now.AddMinutes(10))
    $where = (& python -I (Join-Path $PSScriptRoot 'Read-OpenCode.py') --session-dir $answer.session x) -join ''
    if (-not $answer.session -or [IO.Path]::GetFullPath($where.Trim()) -ne [IO.Path]::GetFullPath($probe)) { throw "Pre-flight: OpenCode worked in '$where', not in $probe. Nothing was run." }
    Write-Host "pre-flight ok: OpenCode works in the trial folder"
}

$summary = Join-Path $root 'summary.md'
if (-not [IO.File]::Exists($summary)) {
    [IO.File]::WriteAllText($summary, "# $series, $Agent ($Model), kit $version, context $Context`n`n| Trial | check game | Acceptance | Agent min | Nudges | Questions | Stopped | Folder |`n|---|---|---|---|---|---|---|---|`n", (New-Object Text.UTF8Encoding($false)))
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
        $repoBefore = (& git -C $repo status --porcelain) -join "`n"
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $questions = 0; $nudged = 0; $fresh = 0
        $deadline = [DateTime]::Now.AddMinutes($TrialMinutes)
        Write-Host "   prompt sent ($Agent $Model)"
        $turn = Invoke-AgentTurn $dir $all[$id] $null $log $deadline
        if ($Agent -eq 'opencode') {
            # Never let a trial agent work in another folder (the repo holds the held-out prompts).
            $where = (& python -I (Join-Path $PSScriptRoot 'Read-OpenCode.py') --session-dir $turn.session x) -join ''
            if ([IO.Path]::GetFullPath($where.Trim()) -ne [IO.Path]::GetFullPath($dir)) { throw "Trial $name ran in '$where', not in its trial folder: series stopped. Check that folder for changes." }
        }
        while (-not $turn.stopped) {
            # A failed compaction leaves the session unusable: carry on once in a new session from what is on disk.
            if ($turn.error -eq 'compaction.failed' -and $fresh -lt 1) {
                $fresh++; Write-Host '   context full: new session'
                $turn = Invoke-AgentTurn $dir ("Your previous session in this folder ran out of context and was lost. Its work is on disk: read TASK.md, run ./agent.ps1 status and look at src/, then finish the request below.`n`n" + $all[$id]) $null $log $deadline
                continue
            }
            # A question gets the standard answer; a run without a passing game check gets one nudge.
            if ($turn.text.TrimEnd().EndsWith('?') -and $questions -lt 2) { $questions++; $turn = Invoke-AgentTurn $dir $answerText $turn.session $log $deadline; continue }
            if ($nudged -lt $Nudges -and (Invoke-Kit $dir @('check', 'game')).exit -ne 0) { $nudged++; $turn = Invoke-AgentTurn $dir $nudgeText $turn.session $log $deadline; continue }
            break
        }
        if (((& git -C $repo status --porcelain) -join "`n") -ne $repoBefore) { throw "Trial $name changed files in the repository (git status differs): series stopped." }
        $minutes = [int]$watch.Elapsed.TotalMinutes
        if ($turn.stopped) { Write-Host "   stopped: $($turn.stopped)" }
        if ($turn.error -eq 'compaction.failed') { $turn.session = $null }
        if ($turn.session -or $Agent -eq 'opencode') {
            # A stopped run still gives feedback: where it went round in circles is the most useful part.
            $ask = if ($turn.stopped) { "The run was stopped ($($turn.stopped) after $([int]$watch.Elapsed.TotalMinutes) minutes). Leave the mod as it is.`n" + $feedback } else { $feedback }
            if (-not $turn.session) { $ask = "An agent session in this folder ran out of context; answer from reports/agent-log.jsonl, TASK.md and the code.`n" + $ask }
            Write-Host '   feedback'
            Invoke-AgentTurn $dir $ask $turn.session $log ([DateTime]::Now.AddMinutes($FeedbackMinutes)) | Out-Null
        }
        Write-Host '   independent check game'
        $check = Invoke-Kit $dir @('check', 'game')
        $verdict = (($check.text -split "`n") | Select-Object -First 1)
        $trial = & powershell -NoProfile -File (Join-Path $PSScriptRoot 'Read-Trial.ps1') -Path $dir 2>&1
        [IO.File]::WriteAllText((Join-Path $root "$name.trial.txt"), "check game: $verdict`n`n" + ($trial -join "`n") + "`n", (New-Object Text.UTF8Encoding($false)))
        # Hidden acceptance tests, written from the prompt only: copied in, run, removed again (the trial's own history is restored).
        Write-Host '   acceptance tests'
        try {
            $acc = & (Join-Path $repo 'qa/acceptance/Invoke-Acceptance.ps1') -Trial $dir -Id $id -Series $series -Out (Join-Path $root "$name.acceptance.json")
            $acceptance = if (-not $acc.total) { 'none' } elseif ($acc.note) { "0/$($acc.total) (not run)" } else { "$($acc.passed)/$($acc.total)" }
        } catch { $acceptance = 'error'; Write-Host "   acceptance error: $_" }
        $stop = @($(if ($turn.stopped) { $turn.stopped }), $(if ($fresh) { "$fresh new session" })) | Where-Object { $_ }
        $row = "| $name | $verdict | $acceptance | $minutes | $nudged | $questions | $(if ($stop) { $stop -join ', ' } else { 'no' }) | $name |`n"
        [IO.File]::AppendAllText($summary, $row, (New-Object Text.UTF8Encoding($false)))
        Write-Host "   $verdict, acceptance $acceptance ($minutes min)"
    }
}
Write-Host "summary: $summary"
