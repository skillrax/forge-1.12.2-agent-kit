# Maintainer regression with real Minecraft (~2 min): every guide snippet is in the guide samples;
# generator output and the guide samples compile, lint clean and pass their game tests in fresh checkouts of kit/.
# -Client also runs `check client` on the guide samples (a Minecraft window for ~1 min), then again with a deliberately
# desynced container (client slots differ from the server's), which must FAIL.
[CmdletBinding()]
param([string]$Work, [switch]$Client)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$kit = Join-Path $repo 'kit'
if (-not $Work) { $Work = Join-Path $repo ('.work/kit-samples/' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmss')) }
$shell = (Get-Process -Id $PID).Path

function Invoke-Kit([string]$Dir, [string[]]$Arguments) {
    $ErrorActionPreference = 'Continue'
    $output = & $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Dir 'agent.ps1') @Arguments 2>$null
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw "agent.ps1 $($Arguments -join ' ') failed in $Dir`n$($output -join "`n")" }
    return ($output -join "`n")
}
function New-KitCheckout([string]$Dir) {
    $ErrorActionPreference = 'Continue'
    $output = & $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $kit 'tools/New-Checkout.ps1') -Destination $Dir 2>$null
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw "checkout failed: $output" }
}

try {
    # 0. Every java block in kit/docs must be in the guide samples compiled below.
    $snippets = & $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repo 'qa/unit/Test-GuideSnippets.ps1')
    if ($LASTEXITCODE -ne 0) { throw ($snippets -join "`n") }

    # 1. Generators replayed from scratch; their own GeneratedContentTest plus GeneratorTests must pass.
    $gen = Join-Path $Work 'generators'
    New-KitCheckout $gen
    foreach ($command in @(
        @('init', 'rubymod', 'Ruby Mod'),
        @('new', 'item', 'ruby'),
        @('new', 'ore', 'ruby_ore', '-Drops', 'ruby', '-Count', '1-2', '-Xp', '3-7', '-Level', '2', '-Height', '5-32'),
        @('new', 'ore', 'ruby_nether_ore', '-Drops', 'ruby', '-Dimension', '-1', '-Height', '10-100'),
        @('new', 'block', 'ruby_block', '-Material', 'IRON', '-Hardness', '5', '-Level', '2'),
        @('new', 'food', 'ruby_apple', '-Hunger', '6', '-Saturation', '1.2', '-AlwaysEdible', '-Effect', 'regeneration:100:1'),
        @('new', 'tools', 'ruby', '-Only', 'all', '-Level', '2', '-Durability', '1200', '-Speed', '8', '-Damage', '3', '-Repair', 'ruby', '-Recipes'),
        @('new', 'armor', 'ruby', '-Only', 'all', '-Like', 'iron', '-Durability', 'diamond', '-Protection', '3,8,6,3', '-Toughness', '2', '-Repair', 'ruby', '-Recipes'),
        @('new', 'recipe', 'ruby_block', '-Shape', '###,###,###', '-Key', '#=ruby', '-Result', 'ruby_block'),
        @('new', 'recipe', 'ruby_from_block', '-Ingredients', 'ruby_block', '-Result', 'ruby', '-Count', '9'),
        @('new', 'smelting', 'ruby', '-From', 'ruby_ore', '-Result', 'ruby', '-Xp', '1.0'),
        @('new', 'tile', 'ruby_box', '-Ticking', '-Inventory', '9'),
        @('new', 'tile', 'ruby_marker'),
        @('new', 'mob', 'ruby_golem', '-Like', 'zombie', '-Health', '40', '-Attack', '5', '-Drops', 'ruby', '-Count', '1-2', '-BiomeTypes', 'plains', '-Egg', 'aa2233,331111'),
        @('new', 'mob', 'ruby_cow', '-Like', 'cow', '-Biomes', 'plains,savanna', '-Weight', '6'))) { Invoke-Kit $gen $command | Out-Null }
    $testDir = Join-Path $gen 'src/gametest/java/com/example/rubymod'
    New-Item -ItemType Directory -Force -Path $testDir | Out-Null
    Copy-Item -LiteralPath (Join-Path $repo 'qa/fixtures/generators/GeneratorTests.java') -Destination $testDir
    $generators = Invoke-Kit $gen @('check', 'game')
    if ($generators -match '(?m)^(WARN|ERROR) ') { throw "generated content has lint findings:`n$generators" }

    # 2. Guide samples: a complete mod using every guide's code.
    $samples = Join-Path $Work 'samples'
    New-KitCheckout $samples
    Copy-Item -Recurse -Force -Path (Join-Path $repo 'qa/fixtures/guide-samples/src/*') -Destination (Join-Path $samples 'src')
    $build = Join-Path $samples 'build.gradle'
    $text = [IO.File]::ReadAllText($build) -replace "(?m)^version = '[^']*'", "version = '1.1.0'" -replace "(?m)^group = '[^']*'", "group = 'com.example.rubymod'"
    [IO.File]::WriteAllText($build, $text + "`nbase { archivesName = 'rubymod' }`n", (New-Object Text.UTF8Encoding($false)))
    $guide = Invoke-Kit $samples @('check', 'game')
    if ($guide -match '(?m)^(WARN|ERROR) ') { throw "guide samples have lint findings:`n$guide" }

    if ($Client) {
        $clientRun = Invoke-Kit $samples @('check', 'client')
        if ($clientRun -match '(?m)^(WARN|ERROR) ') { throw "guide samples have client findings:`n$clientRun" }
        if ($clientRun -notmatch '(?m)^PASS test gui block rubymod:ruby_box ') { throw "the container sync check did not test the ruby box GUI:`n$clientRun" }
        # Deliberate desync: on the client only, the box's main-inventory slots stand for the hotbar.
        $container = Join-Path $samples 'src/main/java/com/example/rubymod/RubyBoxContainer.java'
        $source = [IO.File]::ReadAllText($container)
        $broken = $source.Replace('col + row * 9 + 9, 8 + col * 18, 84', 'col + row * 9 + (playerInventory.player.world.isRemote ? 0 : 9), 8 + col * 18, 84')
        if ($broken -eq $source) { throw 'desync patch did not apply to RubyBoxContainer.java' }
        [IO.File]::WriteAllText($container, $broken, (New-Object Text.UTF8Encoding($false)))
        $ErrorActionPreference = 'Continue'
        $desync = (& $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $samples 'agent.ps1') check client 2>$null) -join "`n"
        $desyncExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        [IO.File]::WriteAllText($container, $source, (New-Object Text.UTF8Encoding($false)))
        if ($desyncExit -eq 0 -or $desync -notmatch '(?m)^FAIL test gui block rubymod:ruby_box at after opening: slot 9 is player inventory #0') { throw "desynced container was not caught:`n$desync" }
    }

    "$snippets"
    "PASS generators: $(($generators -split "`n")[0])"
    "PASS guide samples: $(($guide -split "`n")[0])"
    if ($Client) {
        "PASS guide samples client: $(($clientRun -split "`n")[0])"
        "PASS desynced container caught: $(($desync -split "`n" | Where-Object { $_ -like 'FAIL test gui*' } | Select-Object -First 1))"
    }
    exit 0
} catch {
    "FAIL: $($_.Exception.Message)"
    "work: $Work"
    exit 1
}
