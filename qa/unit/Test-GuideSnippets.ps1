# Every ```java block in kit/docs must appear in qa/fixtures/guide-samples, which Test-KitSamples
# compiles and runs (~1 s, no Minecraft). A block is split into paragraphs at blank lines and `...`;
# each paragraph's lines must occur, in order, in one fixture file. Whitespace and comments are
# ignored, so a guide may condense formatting or skip lines but not change code.
# Guide names map to fixture names: MyMod -> RubymodMod, mymod -> rubymod.
[CmdletBinding()]
param([switch]$Detail)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$docs = Join-Path $root 'kit/docs'
$fixtures = Join-Path $root 'qa/fixtures/guide-samples/src'

function Get-CodeLines([string]$Text) {
    $Text = $Text -replace '/\*[\s\S]*?\*/', ''
    foreach ($line in $Text -split "`r?`n") {
        $code = ($line -replace '(^|\s)//.*$', '') -replace '\s+', ''
        if ($code -and $code -ne '...') { $code }
    }
}
function Find-Lines([string[]]$Lines, [string]$Source) {
    # Index of the first line that does not occur (in order) in $Source, or -1 when all do.
    $at = 0
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        $hit = $Source.IndexOf($Lines[$i], $at, [StringComparison]::Ordinal)
        if ($hit -lt 0) { return $i }
        $at = $hit + $Lines[$i].Length
    }
    return -1
}

$sources = @{}
Get-ChildItem -Recurse -File -Path $fixtures -Include *.java | ForEach-Object {
    $sources[$_.Name] = (Get-CodeLines ([IO.File]::ReadAllText($_.FullName))) -join ''
}

$failures = @(); $count = 0
foreach ($doc in Get-ChildItem -Recurse -File -Path $docs -Filter *.md) {
    $text = [IO.File]::ReadAllText($doc.FullName)
    $rel = $doc.FullName.Substring($root.Length + 1) -replace '[\\]', '/'
    foreach ($match in [regex]::Matches($text, '(?ms)^```java\r?\n(.*?)^```')) {
        $count++
        $body = $match.Groups[1].Value -creplace '\bMyMod\b', 'RubymodMod' -creplace '\bmymod\b', 'rubymod'
        foreach ($paragraph in $body -split '(?m)(?:^\s*\r?\n|^\s*(?://\s*)?\.\.\.\s*\r?\n)') {
            $lines = @(Get-CodeLines $paragraph)
            if (-not $lines.Count) { continue }
            $best = -1; $bestName = ''
            foreach ($name in $sources.Keys) {
                $miss = Find-Lines $lines $sources[$name]
                if ($miss -lt 0) { $best = $null; break }
                if ($miss -gt $best) { $best = $miss; $bestName = $name }
            }
            if ($null -eq $best) { continue }
            $at = ($text.Substring(0, $match.Index) -split "`n").Count + 1
            $failures += "${rel}:$at untested (closest $bestName): $($lines[$best])"
            if ($Detail) { $lines | Where-Object { $sources[$bestName].IndexOf($_, [StringComparison]::Ordinal) -lt 0 } | ForEach-Object { $failures += "    missing: $_" } }
            break
        }
    }
}
if ($failures.Count) {
    $failures | ForEach-Object { "FAIL $_" }
    "FAIL java snippets in kit/docs that are not in qa/fixtures/guide-samples: compile them there or cut them"
    exit 1
}
"PASS $count java snippets found in qa/fixtures/guide-samples"
exit 0
