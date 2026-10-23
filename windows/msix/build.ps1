param(
    [Parameter(Mandatory=$true)][string]$IdentityName,
    [Parameter(Mandatory=$true)][string]$Publisher,
    [Parameter(Mandatory=$true)][string]$PublisherDisplayName,
    [string]$DisplayName = 'Marantz AV Remote',
    [string]$Version = '1.2.1.0'
)
$ErrorActionPreference = 'Stop'
$stage = Join-Path $PWD 'msix-stage'
New-Item -ItemType Directory -Force "$stage/Assets", 'dist' | Out-Null
Copy-Item 'dist/Marantz-Remote.exe' "$stage/Marantz-Remote.exe"
foreach ($size in @(50,44,150)) { Copy-Item "windows/assets/Logo$size.png" "$stage/Assets/Logo$size.png" }
function Escape([string]$value) { [System.Security.SecurityElement]::Escape($value) }
$name = Escape $IdentityName; $pub = Escape $Publisher; $display = Escape $DisplayName; $publisherLabel = Escape $PublisherDisplayName
@"
<?xml version="1.0" encoding="utf-8"?>
<Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10" xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10" xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities" IgnorableNamespaces="uap rescap">
  <Identity Name="$name" Publisher="$pub" Version="$Version" ProcessorArchitecture="x64" />
  <Properties><DisplayName>$display</DisplayName><PublisherDisplayName>$publisherLabel</PublisherDisplayName><Logo>Assets\Logo50.png</Logo><Description>Unofficial local network remote for compatible Marantz receivers.</Description></Properties>
  <Resources><Resource Language="en-us" /></Resources>
  <Dependencies><TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.17763.0" MaxVersionTested="10.0.26100.0" /></Dependencies>
  <Applications><Application Id="App" Executable="Marantz-Remote.exe" EntryPoint="Windows.FullTrustApplication"><uap:VisualElements DisplayName="$display" Description="Unofficial local network Marantz remote" BackgroundColor="#131311" Square150x150Logo="Assets\Logo150.png" Square44x44Logo="Assets\Logo44.png" /></Application></Applications>
  <Capabilities><rescap:Capability Name="runFullTrust" /></Capabilities>
</Package>
"@ | Set-Content -Encoding utf8 "$stage/AppxManifest.xml"
$makeappx = Get-ChildItem "${env:ProgramFiles(x86)}/Windows Kits/10/bin/*/x64/makeappx.exe" | Sort-Object FullName -Descending | Select-Object -First 1
if (-not $makeappx) { throw 'Windows SDK MakeAppx.exe was not found' }
$output = "dist/Marantz-Remote-Store-$Version-x64.msix"
& $makeappx.FullName pack /o /d $stage /p $output
if ($LASTEXITCODE -ne 0) { throw 'MSIX package validation failed' }
& $makeappx.FullName unpack /o /p $output /d 'msix-verified'
if ($LASTEXITCODE -ne 0) { throw 'MSIX verification failed' }
