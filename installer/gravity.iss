; Polished Windows installer for GravityChunk.
; Built by scripts/build-installer.ps1 which defines:
;   AppVersion, AppName, AppImageDir, OutputDir

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef AppName
  #define AppName "GravityChunk"
#endif
#ifndef AppImageDir
  #define AppImageDir "..\dist\app-image\GravityChunk"
#endif
#ifndef OutputDir
  #define OutputDir "..\dist\installer"
#endif

[Setup]
AppId={{A7C3E9F1-4B2D-4F8A-9C1E-GravityChunkWin}}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=Grumbo
AppPublisherURL=https://github.com/gkane1234/gravity
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputDir={#OutputDir}
OutputBaseFilename={#AppName}-Setup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
InfoBeforeFile=requirements.txt
UninstallDisplayIcon={app}\{#AppName}.exe

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional icons:"; Flags: unchecked

[Files]
Source: "{#AppImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppName}.exe"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppName}.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppName}.exe"; Description: "Launch {#AppName}"; Flags: nowait postinstall skipifsilent
