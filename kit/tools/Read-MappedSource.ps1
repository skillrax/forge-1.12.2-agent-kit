# Bounded reads of the pinned 1.12.2 mapped Minecraft/Forge source (used by `agent.ps1 api`).
#   -Class X -Member m        a member's declaration and body
#   -Class X -Head [-TypeName T]  a type's declaration and first fields (T for nested types)
#   -Grep text                every source line containing text (fields, constants, literals)
[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+$')][string]$Class,
    [ValidatePattern('^[A-Za-z_$][A-Za-z0-9_$]*$')][string]$Member,
    [switch]$Declarations,
    [switch]$Head,
    [ValidatePattern('^[A-Za-z_$][A-Za-z0-9_$]*$')][string]$TypeName,
    [string]$Grep,
    [switch]$Text,
    [ValidateRange(1,100000)][int]$Line=1,
    [ValidateRange(1,20)][int]$MatchIndex=1,
    [ValidateRange(1,200)][int]$MaxLines=30,
    [string]$Archive
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
function Read-Entry($Zip, [string]$Name) {
    $entry = $Zip.GetEntry($Name)
    if (-not $entry) { return $null }
    $reader = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::UTF8, $true)
    try { return ($reader.ReadToEnd() -split '\r?\n') } finally { $reader.Dispose() }
}
try {
    if (-not $Class -and -not $Grep) { throw 'Give -Class (with -Member, -Head, -Declarations or -Line) or -Grep.' }
    if ($Member -and $PSBoundParameters.ContainsKey('Line')) { throw 'Choose Member or Line, not both.' }
    if ($Declarations -and ($Member -or $PSBoundParameters.ContainsKey('Line'))) { throw 'Choose Declarations, Member or Line.' }
    if (-not $Archive) {
        $cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
        $Archive = Join-Path $cacheRoot 'caches/minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/1.12.2-14.23.5.2859/stable/39-1.12/remapped-javadoc.jar'
    }
    if (-not [IO.File]::Exists($Archive)) { throw 'Mapped source archive missing. Run agent.ps1 check environment first.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        if ($Grep) {
            # Plain substring search over every source file; reports file:line, most specific first.
            $hits = New-Object 'System.Collections.Generic.List[string]'
            $total = 0
            foreach ($entry in $zip.Entries) {
                if (-not $entry.FullName.EndsWith('.java')) { continue }
                $reader = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::UTF8, $true)
                try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
                if ($content.IndexOf($Grep, [StringComparison]::OrdinalIgnoreCase) -lt 0) { continue }
                $fileLines = $content -split '\r?\n'
                for ($i = 0; $i -lt $fileLines.Length; $i++) {
                    if ($fileLines[$i].IndexOf($Grep, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                        $total++
                        if ($hits.Count -lt $MaxLines) {
                            $code = $fileLines[$i].Trim(); if ($code.Length -gt 180) { $code = $code.Substring(0, 180) }
                            $hits.Add(('{0}:{1}: {2}' -f $entry.FullName.Replace('/', '.').Replace('.java', ''), ($i + 1), $code))
                        }
                    }
                }
            }
            if ($Text) {
                if (-not $total) { "no source line in 1.12.2 Minecraft/Forge contains '$Grep'" }
                $hits
                if ($total -gt $hits.Count) { '... {0} more matches; search something more specific' -f ($total - $hits.Count) }
            } else {
                [ordered]@{ schemaVersion = 1; status = 'PASS'; text = $Grep; total = $total; hits = @($hits) } | ConvertTo-Json -Depth 4 -Compress
            }
            exit 0
        }
        $entryName = $Class.Replace('.', '/') + '.java'
        $lines = Read-Entry $zip $entryName
        if (-not $lines) { throw "Class source absent: $entryName" }
    } finally { $zip.Dispose() }
    # Conservative declaration discovery, not a Java parser. Never return bodies or calls.
    $discovered = @()
    for ($i=0; $i -lt $lines.Length; $i++) {
        $candidate = $lines[$i] -replace '^\s*(@[\w.]+(\([^)]*\))?\s*)+', ''
        if ($candidate -match '^\s*(public|protected|private)\s+(?:(?:static|final|abstract|synchronized|native|default|transient|volatile)\s+)*(?:[\w.$<>?,\[\]]+\s+)*([\w$]+)\s*(\(|=|;)' -and $candidate -notmatch '\b(class|interface|enum)\b') {
            $discovered += @{number=($i+1); name=$Matches[2]; text=$lines[$i].Substring(0,[Math]::Min(240,$lines[$i].Length))}
        }
    }
    if ($Declarations) {
        [ordered]@{schemaVersion=1; status='PASS'; archive=[IO.Path]::GetFullPath($Archive); entry=$entryName; declarationCount=$discovered.Count; hasMoreDeclarations=($discovered.Count -gt $MaxLines); declarations=@($discovered | Select-Object -First $MaxLines); scope='Bounded conservative public/protected/private declaration discovery; multiline/package-private declarations may require a bounded Line read. Custom archive identity is caller-selected.'} | ConvertTo-Json -Depth 5 -Compress
        exit 0
    }
    $declarationLines = @()
    if ($Head) {
        $type = if ($TypeName) { $TypeName } else { $Class.Substring($Class.LastIndexOf('.') + 1) }
        for ($i=0; $i -lt $lines.Length; $i++) { if ($lines[$i] -match ('\b(class|interface|enum)\s+' + [regex]::Escape($type) + '\b')) { $Line = $i + 1; break } }
    }
    if ($Member) {
        $pattern = '^\s*(public|protected|private|static|final|abstract|synchronized)\b.*\b' + [regex]::Escape($Member) + '\s*\('
        for ($i=0; $i -lt $lines.Length; $i++) { if ($lines[$i] -match $pattern) { $declarationLines += ($i + 1) } }
        if (-not $declarationLines.Count) { $declarationLines = @($discovered | Where-Object {$_.name -ceq $Member} | ForEach-Object {$_.number}) }
        if (-not $declarationLines.Count) {
            # Interface methods carry no modifier: "int getSlots();". Calls and assignments do not match (no bare type before the name).
            $bare = '^\s*(?!(return|new|throw|else|case)\b)(@[\w.]+\s+)*[\w<>\[\],.?]+(\s+[\w<>\[\],.?]+)*\s+' + [regex]::Escape($Member) + '\s*\('
            for ($i=0; $i -lt $lines.Length; $i++) { if ($lines[$i] -match $bare) { $declarationLines += ($i + 1) } }
        }
        if ($declarationLines.Count -lt $MatchIndex) { throw 'Member declaration/overload absent. Use -Declarations -MaxLines for bounded discovery, then a bounded Line range for unusual or multiline declarations.' }
        $Line = [Math]::Max(1, ($declarationLines[$MatchIndex-1] - 2))
    }
    if ($Line -gt $lines.Length) { throw 'Line exceeds class length.' }
    $end = [Math]::Min($lines.Length, ($Line+$MaxLines-1))
    # A member read ends at the member's closing brace (within MaxLines): side effects often sit at the end of a body.
    function Get-MemberEnd([int]$Declaration, [int]$Limit) {
        $depth = 0; $opened = $false
        for ($n = $Declaration; $n -le [Math]::Min($lines.Length, $Limit); $n++) {
            $code = ($lines[$n-1] -replace '"(\\.|[^"\\])*"', '""') -replace '//.*$', ''
            if (-not $opened -and $code -match ';\s*$' -and $code -notmatch '\{') { return $n }
            foreach ($ch in $code.ToCharArray()) { if ($ch -eq '{') { $depth++; $opened = $true } elseif ($ch -eq '}') { $depth-- } }
            if ($opened -and $depth -le 0) { return $n }
        }
        return $null
    }
    if ($Member) { $memberEnd = Get-MemberEnd $declarationLines[$MatchIndex-1] ($Line + $MaxLines - 1); if ($memberEnd) { $end = $memberEnd } }
    $snippet = for ($n=$Line; $n -le $end; $n++) {
        $value=$lines[$n-1]
        if ($value.Length -gt 240) { $value=$value.Substring(0,240) }
        @{ number=$n; text=$value }
    }
    if ($Text) {
        'source: {0} lines {1}-{2} (pinned 1.12.2 mapped source)' -f $entryName, $Line, $end
        foreach ($row in $snippet) { '{0,5}| {1}' -f $row.number, $row.text }
        # Text reads show the following overloads too while the MaxLines budget lasts; the one with the side effect is often not the first.
        $shown = $end - $Line + 1
        if ($Member) {
            foreach ($declaration in @($declarationLines | Select-Object -Skip $MatchIndex)) {
                $from = [Math]::Max($end + 1, $declaration - 2)
                $to = Get-MemberEnd $declaration ($from + $MaxLines - 1)
                if (-not $to -or ($shown + $to - $from + 1) -gt $MaxLines) { '      (overload at line ' + $declaration + ' not shown: tools/Read-MappedSource.ps1 -Class ' + $Class + ' -Member ' + $Member + ' -MatchIndex ' + ([array]::IndexOf($declarationLines, $declaration) + 1) + ' -Text)'; continue }
                '  ...'
                for ($n = $from; $n -le $to; $n++) { $value = $lines[$n-1]; if ($value.Length -gt 240) { $value = $value.Substring(0,240) }; '{0,5}| {1}' -f $n, $value }
                $shown += $to - $from + 1; $end = $to
            }
        }
        if ((-not $Member -or -not $memberEnd) -and $end -lt $lines.Length) { '      (more: agent.ps1 api ... -Full, or tools/Read-MappedSource.ps1 -Class ' + $Class + ' -Line ' + ($end + 1) + ' -Text)' }
        exit 0
    }
    [ordered]@{schemaVersion=1; status='PASS'; archive=[IO.Path]::GetFullPath($Archive); entry=$entryName; matchingDeclarationCount=$declarationLines.Count; matchingDeclarationLines=@($declarationLines | Select-Object -First 20); fromLine=$Line; toLine=$end; classLineCount=$lines.Length; hasMoreLines=($end -lt $lines.Length); lines=@($snippet); scope='Bounded source excerpt; explicit custom archives are caller-selected and not verified as the pinned version.'} | ConvertTo-Json -Depth 5 -Compress
    exit 0
} catch {
    if ($Text) { 'FAIL: ' + $_.Exception.Message } else { [ordered]@{schemaVersion=1; status='FAIL'; message=$_.Exception.Message} | ConvertTo-Json -Compress }
    exit 1
}
