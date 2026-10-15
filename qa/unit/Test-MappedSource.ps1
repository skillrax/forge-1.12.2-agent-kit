[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$shell=(Get-Process -Id $PID).Path
$helper=Join-Path $root 'kit/tools/Read-MappedSource.ps1'
$fixture=Join-Path $root ('.work/source-fixtures/'+[Guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Force -Path $fixture | Out-Null
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive=Join-Path $fixture 'fixture.jar'
    $zip=[IO.Compression.ZipFile]::Open($archive,[IO.Compression.ZipArchiveMode]::Create)
    try {
        $entry=$zip.CreateEntry('example/Food.java')
        $writer=New-Object IO.StreamWriter($entry.Open(),(New-Object Text.UTF8Encoding($false)))
        try {
            $source=@('package example;','public class Food {',('    // '+[char]0x2603),'    public void feed() {',('        '+('x'*400)), '    }', '    public void feed(int amount) {', '    }', '    @Deprecated public static final int COUNT = 3;', '    @Deprecated public void annotated() {}', '    public void multiline(', '        int count) {}', '    void caller() { feed(); }', '}')
            $writer.Write($source -join "`n")
        } finally { $writer.Dispose() }
    } finally { $zip.Dispose() }
    $output=& $shell -NoProfile -File $helper -Class example.Food -Member feed -MaxLines 5 -Archive $archive
    if ($LASTEXITCODE -ne 0) { throw "Read failed: $output" }
    $result=($output -join "`n") | ConvertFrom-Json
    if ($result.lines.Count -ne 5 -or $result.matchingDeclarationCount -ne 2 -or $result.lines[1].text -notmatch ([string][char]0x2603)) { throw 'Bounded/Unicode/overload contract incorrect.' }
    if (@($result.lines | Where-Object {$_.text.Length -gt 240}).Count) { throw 'Line width exceeded bound.' }
    $second=& $shell -NoProfile -File $helper -Class example.Food -Member feed -MatchIndex 2 -MaxLines 3 -Archive $archive
    if ($LASTEXITCODE -ne 0 -or (($second -join "`n") | ConvertFrom-Json).fromLine -ne 5) { throw 'Overload selection incorrect.' }
    $missing=& $shell -NoProfile -File $helper -Class example.Missing -Archive $archive
    if ($LASTEXITCODE -ne 1 -or $missing -notmatch 'Class source absent') { throw 'Missing class not rejected.' }
    $absent=& $shell -NoProfile -File $helper -Class example.Food -Member absent -Archive $archive
    if ($LASTEXITCODE -ne 1 -or $absent -notmatch 'Member declaration') { throw 'Missing member not rejected.' }
    $field=& $shell -NoProfile -File $helper -Class example.Food -Member COUNT -MaxLines 3 -Archive $archive
    if($LASTEXITCODE -ne 0 -or (($field -join "`n") | ConvertFrom-Json).matchingDeclarationLines[0] -ne 9){throw 'Annotated field discovery failed'}
    $annotated=& $shell -NoProfile -File $helper -Class example.Food -Member annotated -MaxLines 3 -Archive $archive
    if($LASTEXITCODE -ne 0 -or (($annotated -join "`n") | ConvertFrom-Json).matchingDeclarationLines[0] -ne 10){throw 'Annotated method discovery failed'}
    $discovery=& $shell -NoProfile -File $helper -Class example.Food -Declarations -MaxLines 3 -Archive $archive
    $found=($discovery -join "`n") | ConvertFrom-Json
    if($LASTEXITCODE -ne 0 -or $found.declarations.Count -ne 3 -or -not $found.hasMoreDeclarations -or $found.declarationCount -ne 5){throw "Discovery bounds/count incorrect: $discovery"}
    $invalid=& $shell -NoProfile -File $helper -Class example.Food -Declarations -Member feed -Archive $archive
    if($LASTEXITCODE -ne 1){throw 'Conflicting selectors accepted'}
    [ordered]@{status='PASS'; cases=@('bounded excerpt','240-character lines','Unicode','overload selection','missing class/member','annotated field/method','bounded declaration discovery/no calls','conflicting selectors'); fixture=$fixture; scope='synthetic source-reader fixture'} | ConvertTo-Json -Compress
    exit 0
} catch { @{status='FAIL';message=$_.Exception.Message;fixture=$fixture} | ConvertTo-Json -Compress; exit 1 }
