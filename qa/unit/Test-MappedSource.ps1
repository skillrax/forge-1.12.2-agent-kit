[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
# The reader prints UTF-8; decode child output as UTF-8 whatever the console code page is.
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
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
            $source=@('package example;','public class Food {',('    // '+[char]0x2603),'    public void feed() {',('        '+('x'*400)), '    }', '    public void feed(int amount) {', '    }', '    @Deprecated public static final int COUNT = 3;', '    @Deprecated public void annotated() {}', '    public void multiline(', '        int count) {}', '    void caller() { feed(); }', '    static class Inner {', '        int secret = 7;', '    }', '}')
            $writer.Write($source -join "`n")
        } finally { $writer.Dispose() }
    } finally { $zip.Dispose() }
    # Interface members (no modifier) in a second archive.
    $ifaceArchive=Join-Path $fixture 'iface.jar'
    $zip=[IO.Compression.ZipFile]::Open($ifaceArchive,[IO.Compression.ZipArchiveMode]::Create)
    try {
        $entry=$zip.CreateEntry('example/Feeder.java')
        $writer=New-Object IO.StreamWriter($entry.Open(),(New-Object Text.UTF8Encoding($false)))
        try { $writer.Write((@('package example;','public interface Feeder {','    int slots();','    default int twice() {','        return slots() * 2;','    }','}') -join "`n")) } finally { $writer.Dispose() }
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
    $grep=& $shell -NoProfile -File $helper -Grep 'SECRET = 7' -Text -Archive $archive
    if($LASTEXITCODE -ne 0 -or ($grep -join "`n") -notmatch 'example\.Food:15: int secret = 7;'){throw "Grep incorrect: $grep"}
    $none=& $shell -NoProfile -File $helper -Grep 'no such text' -Text -Archive $archive
    if($LASTEXITCODE -ne 0 -or ($none -join "`n") -notmatch 'no source line'){throw "Empty grep incorrect: $none"}
    $head=& $shell -NoProfile -File $helper -Class example.Food -Head -TypeName Inner -MaxLines 2 -Text -Archive $archive
    if($LASTEXITCODE -ne 0 -or $head[0] -notmatch 'lines 14-15' -or ($head -join "`n") -notmatch 'secret'){throw "Nested head incorrect: $head"}
    $iface=& $shell -NoProfile -File $helper -Class example.Feeder -Member slots -Text -Archive $ifaceArchive
    if($LASTEXITCODE -ne 0 -or ($iface -join "`n") -notmatch 'int slots\(\);' -or ($iface -join "`n") -match 'return slots'){throw "Interface member read incorrect: $iface"}
    $body=& $shell -NoProfile -File $helper -Class example.Feeder -Member twice -Text -Archive $ifaceArchive
    if($LASTEXITCODE -ne 0 -or $body[0] -notmatch 'lines 2-6'){throw "Read did not end at the closing brace: $body"}
    $both=& $shell -NoProfile -File $helper -Class example.Food -Member feed -Text -Archive $archive
    if($LASTEXITCODE -ne 0 -or ($both -join "`n") -notmatch 'feed\(int amount\)'){throw "Text read did not show the second overload: $both"}
    [ordered]@{status='PASS'; cases=@('bounded excerpt','240-character lines','Unicode','overload selection','missing class/member','annotated field/method','bounded declaration discovery/no calls','conflicting selectors','grep across sources','nested type head','interface member','read ends at closing brace','text read shows overloads'); fixture=$fixture; scope='synthetic source-reader fixture'} | ConvertTo-Json -Compress
    exit 0
} catch { @{status='FAIL';message=$_.Exception.Message;fixture=$fixture} | ConvertTo-Json -Compress; exit 1 }
