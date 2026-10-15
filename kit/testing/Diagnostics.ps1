# Turns a check log into a few actionable lines: compiler errors with hints, crash causes, log warnings.

# Names from other Minecraft versions/mappings that tutorials and models commonly produce.
$script:KitRenames = @{
    'setUnlocalizedName' = 'setTranslationKey(String)'
    'getUnlocalizedName' = 'getTranslationKey()'
    'getUnlocalizedNameInefficiently' = 'getTranslationKey(ItemStack)'
    'ItemGroup' = 'CreativeTabs (net.minecraft.creativetab.CreativeTabs, e.g. CreativeTabs.MATERIALS)'
    'CreativeModTab' = 'CreativeTabs.MATERIALS (net.minecraft.creativetab.CreativeTabs)'
    'tabMaterials' = 'CreativeTabs.MATERIALS'; 'tabBlock' = 'CreativeTabs.BUILDING_BLOCKS'; 'tabTools' = 'CreativeTabs.TOOLS'
    'tabCombat' = 'CreativeTabs.COMBAT'; 'tabFood' = 'CreativeTabs.FOOD'; 'tabMisc' = 'CreativeTabs.MISC'
    'DeferredRegister' = 'none in 1.12: register in a @SubscribeEvent RegistryEvent.Register<Item/Block> handler'
    'RegistryObject' = 'none in 1.12: keep a static field holding the instance you register'
    'PlayerEntity' = 'EntityPlayer'; 'ServerPlayerEntity' = 'EntityPlayerMP'; 'LivingEntity' = 'EntityLivingBase'
    'MobEntity' = 'EntityLiving'; 'ItemEntity' = 'EntityItem'; 'Hand' = 'EnumHand'; 'Direction' = 'EnumFacing'
    'ActionResultType' = 'EnumActionResult'; 'CompoundNBT' = 'NBTTagCompound'; 'ListNBT' = 'NBTTagList'
    'BlockState' = 'IBlockState'; 'Effects' = 'MobEffects'; 'EffectInstance' = 'PotionEffect'; 'Effect' = 'Potion'
    'StringTextComponent' = 'TextComponentString'; 'TranslationTextComponent' = 'TextComponentTranslation'
    'ContainerScreen' = 'GuiContainer'; 'Screen' = 'GuiScreen'; 'IItemTier' = 'Item.ToolMaterial'; 'IArmorMaterial' = 'ItemArmor.ArmorMaterial'
    'isClientSide' = 'world.isRemote'; 'spawnEntityInWorld' = 'spawnEntity'; 'addChatMessage' = 'sendMessage(ITextComponent) or sendStatusMessage'
    'stackSize' = 'getCount()/setCount()/grow()/shrink()'; 'getItemStackLimit' = 'getItemStackLimit(ItemStack)'
    'registerItem' = 'event.getRegistry().register(item) inside RegistryEvent.Register<Item>'
    'tick' = 'update() from net.minecraft.util.ITickable (tile entities)'
    'getBlockLayer' = 'getRenderLayer() (client-only: annotate the override @SideOnly(Side.CLIENT))'
    'getTabIconItem' = 'createIcon() (client-only: annotate the override @SideOnly(Side.CLIENT))'
    'getIconItemStack' = 'getIcon()'
    'floor_double' = 'MathHelper.floor(double)'; 'floor_float' = 'MathHelper.floor(float)'; 'ceiling_double_int' = 'MathHelper.ceil(double)'
    'sqrt_double' = 'MathHelper.sqrt(double)'; 'getRandomIntegerInRange' = 'MathHelper.getInt(Random, int, int)'
    'clamp_int' = 'MathHelper.clamp(int, int, int)'; 'clamp_float' = 'MathHelper.clamp(float, float, float)'
    'getChunkFromChunkCoords' = 'world.getChunk(chunkX, chunkZ)'; 'getChunkFromBlockCoords' = 'world.getChunk(BlockPos)'
    'getEntityWorld' = 'entity.world (field) or getEntityWorld()'
    'isFullBlock' = 'isFullCube(IBlockState)'
}

function Get-KitApiIndex {
    param([string]$Root)
    if ($script:KitApi) { return $script:KitApi }
    $path = Join-Path $Root '.kit/api-index.tsv'
    if (-not [IO.File]::Exists($path)) { return $null }
    $classes = @{}; $bySimple = @{}; $members = @{}
    foreach ($line in [IO.File]::ReadAllLines($path)) {
        $f = $line.Split("`t")
        if ($f[0] -eq 'C') {
            $classes[$f[1]] = $f
            $simple = $f[1].Substring($f[1].LastIndexOf('.') + 1)
            if (-not $bySimple.ContainsKey($simple)) { $bySimple[$simple] = New-Object 'System.Collections.Generic.List[string]' }
            $bySimple[$simple].Add($f[1])
        } else {
            if (-not $members.ContainsKey($f[1])) { $members[$f[1]] = New-Object 'System.Collections.Generic.List[object]' }
            $members[$f[1]].Add($f)
        }
    }
    $script:KitApi = @{ classes = $classes; bySimple = $bySimple; members = $members }
    return $script:KitApi
}

# Class name without its package: net.minecraft.item.Item.ToolMaterial -> Item.ToolMaterial
function Get-KitShortName([string]$Name) {
    $parts = $Name.Split('.'); $i = 0
    while ($i -lt $parts.Length - 1 -and $parts[$i] -cmatch '^[a-z]') { $i++ }
    return ($parts[$i..($parts.Length - 1)] -join '.')
}

# Full class names matching a full, simple or Outer.Inner name; top-level and net.minecraft classes first.
function Resolve-KitClass {
    param($Api, [string]$Name)
    if ($Api.classes.ContainsKey($Name)) { return $Name }
    $found = @()
    if ($Api.bySimple.ContainsKey($Name)) { $found = @($Api.bySimple[$Name]) }
    elseif ($Name.Contains('.')) { $last = $Name.Substring($Name.LastIndexOf('.') + 1); if ($Api.bySimple.ContainsKey($last)) { $found = @($Api.bySimple[$last] | Where-Object { $_.EndsWith('.' + $Name) }) } }
    $sorted = @($found | Sort-Object { @($_.Split('.') | Where-Object { $_ -cmatch '^[A-Z]' }).Count }, { if ($_.StartsWith('net.minecraft.')) { 0 } else { 1 } }, { $_.Length })
    return $sorted
}

# A class's own name followed by its superclasses in the index.
function Get-KitClassChain {
    param($Api, [string]$Name)
    $start = @(Resolve-KitClass $Api $Name) | Select-Object -First 1
    $chain = @()
    while ($start -and $chain.Count -lt 8) { $chain += $start; $start = $Api.classes[$start][2]; if (-not $Api.classes.ContainsKey($start)) { $start = $null } }
    return $chain
}

function Get-KitDistance {
    param([string]$A, [string]$B)
    $a = $A.ToLowerInvariant(); $b = $B.ToLowerInvariant()
    $previous = 0..$b.Length
    for ($i = 1; $i -le $a.Length; $i++) {
        $current = @($i) + (1..$b.Length | ForEach-Object { 0 })
        for ($j = 1; $j -le $b.Length; $j++) {
            $cost = if ($a[$i - 1] -eq $b[$j - 1]) { 0 } else { 1 }
            $current[$j] = [Math]::Min([Math]::Min($current[$j - 1] + 1, $previous[$j] + 1), $previous[$j - 1] + $cost)
        }
        $previous = $current
    }
    return $previous[$b.Length]
}

# Close member names: same name ignoring case/underscores, containment, shared camelCase words, or small edit distance.
function Find-KitSimilarMembers {
    param($Api, [string]$ClassName, [string]$Name, [string]$Kind)
    $wanted = $Name.ToLowerInvariant().Replace('_', '')
    $words = @([regex]::Matches($Name, '[A-Z]?[a-z]+|[A-Z]+(?![a-z])') | ForEach-Object { $_.Value.ToLowerInvariant() } | Where-Object { $_.Length -ge 4 -and $_ -notin @('get','set','with') })
    $rows = @(foreach ($owner in (Get-KitClassChain $Api $ClassName)) {
        if (-not $Api.members.ContainsKey($owner)) { continue }
        foreach ($m in $Api.members[$owner]) { if ((-not $Kind -or $m[0] -eq $Kind) -and $m[2] -ne '<init>') { ,@($owner, $m) } }
    })
    # Rarer shared words say more: "layer" beats "block" among Block's members.
    $frequency = @{}
    foreach ($w in $words) { $frequency[$w] = @($rows | Where-Object { $_[1][2].ToLowerInvariant().Contains($w) }).Count }
    $scored = @()
    foreach ($row in $rows) {
        $owner = $row[0]; $m = $row[1]
        $candidate = $m[2].ToLowerInvariant().Replace('_', '')
        $weight = 0.0
        foreach ($w in $words) { if ($candidate.Contains($w)) { $weight += 1.0 / [Math]::Max(1, $frequency[$w]) } }
        $score = if ($candidate -eq $wanted) { 0 } elseif ($candidate.Contains($wanted) -or ($wanted.Contains($candidate) -and $candidate.Length -ge 4)) { 1 }
            elseif ($weight -gt 0) { 3 - $weight }
            elseif ([Math]::Abs($candidate.Length - $wanted.Length) -le 3 -and (Get-KitDistance $candidate $wanted) -le 3) { 3 } else { -1 }
        $label = if ($m[0] -eq 'F') { $m[2] + ': ' + $m[3] } else { $m[3] }
        if ($score -ge 0) { $scored += [pscustomobject]@{ score = $score; text = ((Get-KitShortName $owner) + '.' + $label + $(if ($m[4] -match 'client-only') { ' [client-only]' } else { '' })) } }
    }
    return @($scored | Sort-Object score, text -Unique | Select-Object -First 4 | ForEach-Object { $_.text })
}

function Get-KitCompileHint {
    param([string]$Root, [string]$Message, [string[]]$Details, [string]$File, [int]$Line)
    $symbol = ($Details | Where-Object { $_ -match '^symbol:' } | Select-Object -First 1) -replace '^symbol:\s*', ''
    $location = ($Details | Where-Object { $_ -match '^location:' } | Select-Object -First 1) -replace '^location:\s*', ''
    $owner = if ($location -match '(?:class|interface|enum) ([\w.$]+)') { $Matches[1] } elseif ($location -match 'of type ([\w.$]+)') { $Matches[1] } else { '' }
    $owner = $owner -replace '<.*$', ''
    if ($Message -match "illegal character: '\\ufeff'") { return 'the file starts with a UTF-8 byte-order mark (PowerShell Set-Content/Out-File add one); agent.ps1 check strips it, so rerun; write files with your edit tool or [IO.File]::WriteAllText' }
    $api = Get-KitApiIndex $Root
    if ($Message -match 'cannot find symbol' -and $symbol -match '^(method|variable|class) ([\w$]+)') {
        $kind = $Matches[1]; $name = $Matches[2]
        if ($script:KitRenames.ContainsKey($name)) { return "1.12.2 uses $($script:KitRenames[$name])" }
        if (-not $api) { return 'run agent.ps1 check environment to build the API index used for hints' }
        if ($kind -eq 'class') {
            if ($api.bySimple.ContainsKey($name)) { return 'import ' + ($api.bySimple[$name] | Select-Object -First 3) -join ' or import ' }
            $near = @($api.bySimple.Keys | Where-Object { $_.Length -ge $name.Length - 2 -and $_.Length -le $name.Length + 2 -and (Get-KitDistance $_ $name) -le 2 } | Select-Object -First 4)
            if ($near.Count) { return 'no class ' + $name + ' in 1.12.2; similar: ' + ($near -join ', ') }
            return "no class $name in Minecraft/Forge 1.12.2; search: agent.ps1 api -Find <word>"
        }
        if ($owner -and $owner -notmatch '^(com\.example|java\.)') {
            $similar = Find-KitSimilarMembers $api $owner $name $(if ($kind -eq 'method') { 'M' } else { $null })
            if ($similar.Count) { return 'did you mean ' + ($similar -join ' | ') }
            return "no $kind '$name' on $owner; list members: agent.ps1 api $($owner -replace '^.*\.', '')"
        }
    }
    if ($Message -match 'does not override or implement a method from a supertype' -and $api -and [IO.File]::Exists($File)) {
        $source = [IO.File]::ReadAllLines($File)
        $method = $null
        for ($i = $Line - 1; $i -lt [Math]::Min($source.Length, $Line + 3) -and -not $method; $i++) {
            $declaration = $source[$i] -replace '^\s*(@\w+(\([^)]*\))?\s*)+', ''
            if ($declaration -match '\b(\w+)\s*\(') { $method = $Matches[1] }
        }
        $text = [IO.File]::ReadAllText($File)
        if ($method -and $text -match 'extends\s+([\w.]+)') {
            $parent = $Matches[1]
            $same = New-Object 'System.Collections.Generic.List[string]'
            foreach ($owner in (Get-KitClassChain $api $parent)) {
                if (-not $api.members.ContainsKey($owner)) { continue }
                foreach ($m in $api.members[$owner]) { if ($m[2] -eq $method -and $same.Count -lt 4) { $same.Add((Get-KitShortName $owner) + '.' + $m[3]) } }
            }
            if ($same.Count) { return '1.12.2 signature: ' + ($same -join ' | ') }
            $similar = Find-KitSimilarMembers $api $parent $method 'M'
            if ($similar.Count) { return "no $method in $parent; similar: " + ($similar -join ' | ') }
        }
    }
    if ($Message -match 'cannot be applied to given types|no suitable (method|constructor) found|constructor .* cannot be applied') {
        if ($Message -match '(?:method|constructor) ([\w$]+) in (?:class|interface) ([\w.$<>]+)') {
            $name = $Matches[1]; $cls = $Matches[2] -replace '<.*$', ''
            if ($api) {
                $chain = @(Get-KitClassChain $api $cls)
                $simpleName = $cls -replace '^.*\.', ''
                $overloads = New-Object 'System.Collections.Generic.List[string]'
                foreach ($c in $chain) {
                    if (-not $api.members.ContainsKey($c)) { continue }
                    foreach ($m in $api.members[$c]) {
                        $isCtor = $m[2] -eq '<init>' -and $c -eq $chain[0] -and $name -eq $simpleName
                        if (($m[2] -eq $name -or $isCtor) -and $overloads.Count -lt 4) { $overloads.Add($m[3]) }
                    }
                }
                if ($overloads.Count) { return 'available: ' + ($overloads -join ' | ') }
            }
        }
    }
    return $null
}

# Compiler errors: one entry per distinct file:line:message, with the source line and a hint.
function Get-KitCompileErrors {
    param([string]$Root, [string[]]$Lines)
    $errors = New-Object 'System.Collections.Generic.List[object]'
    $seen = New-Object 'System.Collections.Generic.HashSet[string]'
    for ($i = 0; $i -lt $Lines.Length; $i++) {
        if ($Lines[$i] -notmatch '^\s*(.+\.java):(\d+): error: (.+)$') { continue }
        $file = $Matches[1]; $lineNumber = [int]$Matches[2]; $message = $Matches[3].Trim()
        $code = $null; $details = @()
        for ($j = $i + 1; $j -lt [Math]::Min($Lines.Length, $i + 10); $j++) {
            $next = $Lines[$j]
            if ($next -match '\.java:\d+: (error|warning):' -or $next -match '^\d+ errors?$') { break }
            if ($next -match '^\s*\w+(\[[^\]]*\])+\s*$' -or $next -match '^> |^Note: ' -or -not $next.Trim()) { continue }   # interleaved Gradle noise
            if ($next -match '^\s*\^\s*$') { continue }
            if ($next.Trim() -match '^(symbol|location|required|found|reason):') { $details += ($next.Trim() -replace '\s+', ' '); continue }
            if (-not $code -and -not $details.Count) { $code = $next.Trim() }
        }
        if (-not $seen.Add("$file`:$lineNumber`:$message`:$($details -join ';')")) { continue }
        $relative = $file
        if ($file.StartsWith($Root, [StringComparison]::OrdinalIgnoreCase)) { $relative = $file.Substring($Root.Length).TrimStart('\', '/').Replace('\', '/') }
        $errors.Add([pscustomobject]@{
            file = $relative; line = $lineNumber; message = $message; code = $code; details = $details
            hint = (Get-KitCompileHint $Root $message $details $file $lineNumber)
        })
    }
    return $errors
}

# Known crash/log signatures and what they usually mean in 1.12.2.
$script:KitLogHints = @(
    @{ p = 'invalid side SERVER|NoClassDefFoundError: net/minecraft/client|ClassNotFoundException: net\.minecraft\.client'; h = 'common code references a client-only class; move rendering/GUI/model code into a class used only from the client side (@SideOnly(Side.CLIENT) or @Mod.EventBusSubscriber(value = Side.CLIENT))' }
    @{ p = 'is missing a mapping! This is a bug'; h = 'tile entity class not registered: GameRegistry.registerTileEntity(MyTile.class, new ResourceLocation(MODID, "name"))' }
    @{ p = 'Can not register to a locked registry|registry is locked'; h = 'register items/blocks only inside RegistryEvent.Register handlers' }
    @{ p = 'Attempted to set registry name with existing registry name'; h = 'setRegistryName called twice on the same object' }
    @{ p = 'Registry entry .* already registered|Duplicate registration'; h = 'two objects registered under the same modid:name' }
    @{ p = 'Potentially Dangerous alternative prefix'; h = 'registry name namespace differs from the active modid; use setRegistryName(MODID, "name")' }
    @{ p = 'Parsing error loading recipe'; h = 'a recipe JSON is invalid; the following log lines name the file and reason' }
    @{ p = 'NullPointerException'; h = 'check fields used before registration/init (static instances created in the wrong event?)' }
)

function Get-KitLogProblems {
    param([string[]]$Lines)
    $out = New-Object 'System.Collections.Generic.List[string]'
    $seen = New-Object 'System.Collections.Generic.HashSet[string]'
    $benign = $false
    for ($i = 0; $i -lt $Lines.Length -and $out.Count -lt 8; $i++) {
        $line = $Lines[$i]
        # Legacy FML scanning Java 9+ classes inside dev-only jars logs harmless ASM errors.
        # Benign discovery noise, and recipe/loot load errors that the runner already reports as lint ERRORs.
        if ($line -match 'Unable to read a class file correctly|There was a problem reading the entry|Zip file .* failed to read properly|Parsing error loading recipe|Couldn''t load loot table') { $benign = $true; continue }
        if ($line -match '^\[\d\d:\d\d:\d\d\]') { $benign = $false }
        if ($benign) { continue }
        if ($line -match '(Caused by:|^\S*Exception in server tick loop|Encountered an unexpected exception|^\s*[\w.$]+(Exception|Error)(: |$))' -and $line -notmatch '^\s+at ' -and $line -notmatch 'module-info|ASM ClassReader|KIT_|Cache miss!') {
            $text = ($line -replace '^\[[^\]]*\] \[[^\]]*\]( \[[^\]]*\])?:? ?', '').Trim()
            if ($text.Length -gt 240) { $text = $text.Substring(0, 240) }
            if (-not $seen.Add($text)) { continue }
            $frames = @()
            for ($j = $i + 1; $j -lt [Math]::Min($Lines.Length, $i + 40) -and $frames.Count -lt 3; $j++) {
                if ($Lines[$j] -notmatch '^\s+at ([\w.$<>]+)\(') { if ($frames.Count -or $j -gt $i + 2) { break } else { continue } }
                if ($Matches[1] -notmatch '^(net\.minecraft|java\.|sun\.|jdk\.|com\.google|org\.apache|com\.mojang|io\.netty|kit\.gametest|org\.objectweb|LZMA|joptsimple)') { $frames += $Lines[$j].Trim() }
            }
            $out.Add($text)
            foreach ($frame in $frames) { $out.Add('    ' + $frame) }
            foreach ($hint in $script:KitLogHints) { if ($text -match $hint.p) { $out.Add('  hint: ' + $hint.h); break } }
        } elseif ($line -match 'Potentially Dangerous alternative prefix') {
            $text = ($line -replace '^\[[^\]]*\] \[[^\]]*\]( \[[^\]]*\])?:? ?', '').Trim()
            if ($seen.Add($text)) { $out.Add('WARN ' + $text) }
        }
    }
    return $out
}

# Fallback for failures that are neither compiler errors nor Java exceptions (Gradle/toolchain problems).
function Get-BoundedDiagnostics {
    param([string]$LogPath)
    if (-not $LogPath -or -not [IO.File]::Exists($LogPath)) { return @() }
    $lines = [IO.File]::ReadAllLines($LogPath)
    $selected = New-Object 'System.Collections.Generic.List[string]'
    $patterns = 'ASSERTIONS_FAIL:|AssertionError:|^\* What went wrong:|^Execution failed for task|^> (?!Task |Configure |Run with|Get more|Get more help)|Task .+ not found|JAVA_HOME|No matching toolchains|GradleException'
    for ($i = 0; $i -lt $lines.Length -and $selected.Count -lt 8; $i++) {
        if ($lines[$i] -match $patterns -and -not $selected.Contains($lines[$i].Trim())) {
            $text = $lines[$i].Trim(); if ($text.Length -gt 300) { $text = $text.Substring(0, 300) }
            $selected.Add($text)
        }
    }
    return $selected
}
