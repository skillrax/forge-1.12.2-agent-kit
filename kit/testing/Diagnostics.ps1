# Return useful causes before Gradle wrappers and incidental legacy dependency noise.
function Get-BoundedDiagnostics {
    param([string]$LogPath)
    if (-not $LogPath -or -not [IO.File]::Exists($LogPath)) { return @() }
    $lines = [IO.File]::ReadAllLines($LogPath)
    $selected = New-Object 'System.Collections.Generic.List[int]'
    $seen = New-Object 'System.Collections.Generic.HashSet[string]'
    $patterns = @(
        'ASSERTIONS_FAIL:|AssertionError:',
        'Caused by:|Task .+ not found|JAVA_HOME .*(invalid|not set)|No matching toolchains|No locally installed toolchains',
        'error:|cannot find symbol|symbol:|location:',
        '^\* What went wrong:|^Execution failed for task|Exception in server tick loop|Encountered an unexpected exception|Caught exception from',
        ' FAILED|^> (?!Task |Configure |Run with|Get more)'
    )
    foreach ($pattern in $patterns) {
        if ($pattern -eq $patterns[-1] -and $selected.Count -gt 0) { break }
        for ($i = 0; $i -lt $lines.Length -and $selected.Count -lt 10; $i++) {
            if ($lines[$i] -match $pattern -and -not $selected.Contains($i) -and $seen.Add($lines[$i])) {
                $selected.Add($i)
                if ($lines[$i] -match 'ASSERTIONS_FAIL:|Task .+ not found|error:|JAVA_HOME') {
                    for ($j=$i+1; $j -le $i+2 -and $j -lt $lines.Length -and $selected.Count -lt 10; $j++) {
                        if ($lines[$j].Trim() -and $lines[$j] -notmatch 'Cache:|Cache Only:|Offline:|JDK Cache:|Configure project|Minecraft Mavenizer|\[.*WARN\]' -and -not $selected.Contains($j) -and $seen.Add($lines[$j])) { $selected.Add($j) }
                    }
                }
            }
        }
    }
    foreach ($i in $selected) {
        $line = $lines[$i]
        if ($line.Length -gt 300) { $line.Substring(0, 300) } else { $line }
    }
}
