# Download the two binary dependencies missing from the repository.
# Run from the Android project root: powershell -ExecutionPolicy Bypass -File .\scripts\setup-android-deps.ps1
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$items = @(
  @{
    Uri = 'https://services.gradle.org/distributions/gradle-9.3.1-wrapper.jar'
    Output = 'gradle/wrapper/gradle-wrapper.jar'
    SHA256 = 'b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13'
  },
  @{
    Uri = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar'
    Output = 'app/libs/sherpa-onnx-1.13.8.aar'
    SHA256 = '633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'
  }
)

foreach ($item in $items) {
  $dest = Join-Path $root $item.Output
  $parent = Split-Path -Parent $dest
  New-Item -ItemType Directory -Path $parent -Force | Out-Null

  if (Test-Path $dest) {
    $current = (Get-FileHash -Algorithm SHA256 $dest).Hash.ToLowerInvariant()
    if ($current -eq $item.SHA256) {
      Write-Host "Verified existing $($item.Output)"
      continue
    }
    throw "Checksum mismatch for existing $($item.Output). Inspect/delete the file manually before retrying."
  }

  $tempFile = "$dest.download"
  try {
    Invoke-WebRequest -Uri $item.Uri -OutFile $tempFile -MaximumRedirection 10
    $actual = (Get-FileHash -Algorithm SHA256 $tempFile).Hash.ToLowerInvariant()
    if ($actual -ne $item.SHA256) {
      throw "SHA-256 mismatch for $($item.Output): expected $($item.SHA256), got $actual"
    }
    Move-Item -Path $tempFile -Destination $dest
    Write-Host "Downloaded and verified $($item.Output)"
  } finally {
    if (Test-Path $tempFile) { Remove-Item $tempFile -Force }
  }
}

Write-Host 'Dependencies verified. Open the project in Android Studio and sync Gradle.'
