<#
.SYNOPSIS
    在脚本所在文件夹初始化一个可生成 SystemVerilog 的 Chisel sbt 工程。

.DESCRIPTION
    把本脚本复制到准备作为工程根目录的文件夹，然后在该文件夹打开
    PowerShell 并运行脚本。脚本会创建以下内容：

      build.sbt
      project\build.properties
      src\main\scala\Generate.scala

    默认顶层模块名为 Top。初始化完成后，使用下面的命令生成 SV：

      sbt "runMain Generate"

    生成结果位于 generated\<模块名>.sv。

    脚本使用已配置在 E 盘的 Java、sbt 和依赖缓存。若目标工程文件已经
    存在，脚本默认停止，避免覆盖已有代码。

.PARAMETER ModuleName
    设置示例顶层 Chisel 模块名。名称必须是合法的 Scala 标识符，例如
    MyCPU、ALU 或 Counter。默认值为 Top。

.PARAMETER ProjectName
    设置 build.sbt 中的工程名称。默认使用脚本所在文件夹的名称。

.PARAMETER Force
    覆盖已经存在的 build.sbt、project\build.properties 和
    src\main\scala\Generate.scala。使用前应确认这些文件可以被替换。

.PARAMETER SkipCompile
    只创建工程文件，不执行 sbt compile。适合离线初始化或稍后再下载依赖。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\init-chisel-project.ps1

    在当前脚本所在文件夹初始化工程，顶层模块名使用默认值 Top，并执行
    一次 sbt compile。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\init-chisel-project.ps1 -ModuleName MyCPU

    初始化工程并把示例顶层模块命名为 MyCPU。生成命令为：

      sbt "runMain Generate"

    输出文件为 generated\MyCPU.sv。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\init-chisel-project.ps1 -SkipCompile

    创建工程文件，但不立即编译。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\init-chisel-project.ps1 -ModuleName MyCPU -Force

    使用 MyCPU 作为顶层模块名，并覆盖已有的工程配置与示例源文件。

.NOTES
    查看本帮助：

      powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Help '.\init-chisel-project.ps1' -Detailed"
#>

[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_]*$')]
    [string]$ModuleName = 'Top',

    [string]$ProjectName = '',

    [switch]$Force,

    [switch]$SkipCompile
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($ProjectName)) {
    $ProjectName = Split-Path -Leaf $projectRoot
}
if ([string]::IsNullOrWhiteSpace($ProjectName)) {
    $ProjectName = 'chisel-project'
}

# Escape values before inserting them into Scala string literals.
$sbtProjectName = $ProjectName.Replace('\', '\\').Replace('"', '\"')

$buildFile = Join-Path $projectRoot 'build.sbt'
$propertiesFile = Join-Path $projectRoot 'project\build.properties'
$sourceFile = Join-Path $projectRoot 'src\main\scala\Generate.scala'
$requiredFiles = @($buildFile, $propertiesFile, $sourceFile)
$conflicts = @($requiredFiles | Where-Object { Test-Path -LiteralPath $_ })

if ($conflicts.Count -gt 0 -and -not $Force) {
    $relativeConflicts = $conflicts | ForEach-Object {
        $_.Substring($projectRoot.Length).TrimStart('\')
    }
    throw "Project files already exist: $($relativeConflicts -join ', '). Use -Force to replace them."
}

New-Item -ItemType Directory -Force -Path `
    (Split-Path -Parent $propertiesFile), `
    (Split-Path -Parent $sourceFile) | Out-Null

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$buildContent = @"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "local.chisel"
ThisBuild / version := "0.1.0"

val chiselVersion = "6.5.0"

lazy val root = (project in file("."))
  .settings(
    name := "$sbtProjectName",
    libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion,
    addCompilerPlugin(
      "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
    )
  )
"@

$sourceContent = @"
import chisel3._
import circt.stage.ChiselStage

class $ModuleName extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val y = Output(UInt(9.W))
  })

  io.y := io.a +& io.b
}

object Generate extends App {
  ChiselStage.emitSystemVerilogFile(
    new $ModuleName,
    args = Array("--target-dir", "generated"),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"),
  )
}
"@

[System.IO.File]::WriteAllText($buildFile, $buildContent, $utf8NoBom)
[System.IO.File]::WriteAllText($propertiesFile, "sbt.version=1.10.11`n", $utf8NoBom)
[System.IO.File]::WriteAllText($sourceFile, $sourceContent, $utf8NoBom)

$gitignoreFile = Join-Path $projectRoot '.gitignore'
$ignoreEntries = @('.bsp/', '.bloop/', '.metals/', '.idea/', 'project/target/', 'target/', 'generated/')
$existingIgnoreEntries = @()
if (Test-Path -LiteralPath $gitignoreFile) {
    $existingIgnoreEntries = @(Get-Content -LiteralPath $gitignoreFile)
}
$missingIgnoreEntries = @($ignoreEntries | Where-Object { $existingIgnoreEntries -notcontains $_ })
if ($missingIgnoreEntries.Count -gt 0) {
    $separator = if ((Test-Path -LiteralPath $gitignoreFile) -and (Get-Item $gitignoreFile).Length -gt 0) { "`n" } else { '' }
    [System.IO.File]::AppendAllText(
        $gitignoreFile,
        $separator + ($missingIgnoreEntries -join "`n") + "`n",
        $utf8NoBom
    )
}

# Make the configured E-drive toolchain available in this process as well as new terminals.
$sbtHome = if ($env:SBT_HOME) { $env:SBT_HOME } else { 'E:\chisel-dev\sbt' }
$cacheRoot = 'E:\chisel-dev\cache'
if (Test-Path -LiteralPath 'E:\bin\java.exe') {
    $env:JAVA_HOME = 'E:\'
}
$env:SBT_HOME = $sbtHome
$env:COURSIER_CACHE = Join-Path $cacheRoot 'coursier'
$env:SBT_OPTS = "-Dsbt.global.base=$cacheRoot\sbt-global -Dsbt.ivy.home=$cacheRoot\ivy2 -Dsbt.boot.directory=$cacheRoot\sbt-boot -Dsbt.coursier.home=$cacheRoot\coursier"
$env:Path = "E:\bin;$sbtHome\bin;$env:Path"

$sbt = Get-Command 'sbt.bat' -ErrorAction SilentlyContinue
if (-not $sbt) {
    $fallbackSbt = Join-Path $sbtHome 'bin\sbt.bat'
    if (Test-Path -LiteralPath $fallbackSbt) {
        $sbt = Get-Item -LiteralPath $fallbackSbt
    } else {
        throw "sbt was not found. Expected it at $fallbackSbt."
    }
}

Write-Host "Chisel project initialized at $projectRoot"
Write-Host "Top module: $ModuleName"

if (-not $SkipCompile) {
    Push-Location $projectRoot
    try {
        & $sbt.Source compile
        if ($LASTEXITCODE -ne 0) {
            throw "sbt compile failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

Write-Host ''
Write-Host 'Generate SystemVerilog with:'
Write-Host '  sbt "runMain Generate"'
Write-Host "Output: generated\$ModuleName.sv"
