# Read one pinned class/method, never a whole cache or unrelated game version.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)+$')][string]$Class,
    [ValidatePattern('^[A-Za-z_$][A-Za-z0-9_$]*$')][string]$Member,
    [switch]$Declarations,
    [switch]$Text,
    [ValidateRange(1,100000)][int]$Line=1,
    [ValidateRange(1,20)][int]$MatchIndex=1,
    [ValidateRange(1,80)][int]$MaxLines=30,
    [string]$Archive
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
try {
    if ($Member -and $PSBoundParameters.ContainsKey('Line')) { throw 'Choose Member or Line, not both.' }
    if ($Declarations -and ($Member -or $PSBoundParameters.ContainsKey('Line'))) { throw 'Choose Declarations, Member or Line.' }
    if (-not $Archive) {
        $cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
        $Archive = Join-Path $cacheRoot 'caches/minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/1.12.2-14.23.5.2859/stable/39-1.12/remapped-javadoc.jar'
    }
    if (-not [IO.File]::Exists($Archive)) { throw 'Mapped source archive missing. Run the environment check first, or provide the exact pinned -Archive path.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        $entryName = $Class.Replace('.', '/') + '.java'
        $entry = $zip.GetEntry($entryName)
        if (-not $entry) { throw "Class source absent: $entryName" }
        $reader = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::UTF8, $true)
        try { $lines = $reader.ReadToEnd() -split '\r?\n' } finally { $reader.Dispose() }
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
    if ($Member) {
        $pattern = '^\s*(public|protected|private|static|final|abstract|synchronized)\b.*\b' + [regex]::Escape($Member) + '\s*\('
        for ($i=0; $i -lt $lines.Length; $i++) { if ($lines[$i] -match $pattern) { $declarationLines += ($i + 1) } }
        if (-not $declarationLines.Count) { $declarationLines = @($discovered | Where-Object {$_.name -ceq $Member} | ForEach-Object {$_.number}) }
        if ($declarationLines.Count -lt $MatchIndex) { throw 'Member declaration/overload absent. Use -Declarations -MaxLines for bounded discovery, then a bounded Line range for unusual or multiline declarations.' }
        $Line = [Math]::Max(1, ($declarationLines[$MatchIndex-1] - 2))
    }
    if ($Line -gt $lines.Length) { throw 'Line exceeds class length.' }
    $end = [Math]::Min($lines.Length, ($Line+$MaxLines-1))
    $snippet = for ($n=$Line; $n -le $end; $n++) {
        $value=$lines[$n-1]
        if ($value.Length -gt 240) { $value=$value.Substring(0,240) }
        @{ number=$n; text=$value }
    }
    if ($Text) {
        foreach ($row in $snippet) { '{0,5}| {1}' -f $row.number, $row.text }
        if ($end -lt $lines.Length) { '      (more: -Line ' + ($end + 1) + ')' }
        exit 0
    }
    [ordered]@{schemaVersion=1; status='PASS'; archive=[IO.Path]::GetFullPath($Archive); entry=$entryName; matchingDeclarationCount=$declarationLines.Count; matchingDeclarationLines=@($declarationLines | Select-Object -First 20); fromLine=$Line; toLine=$end; classLineCount=$lines.Length; hasMoreLines=($end -lt $lines.Length); lines=@($snippet); scope='Bounded source excerpt; explicit custom archives are caller-selected and not verified as the pinned version.'} | ConvertTo-Json -Depth 5 -Compress
    exit 0
} catch {
    [ordered]@{schemaVersion=1; status='FAIL'; message=$_.Exception.Message} | ConvertTo-Json -Compress
    exit 1
}
