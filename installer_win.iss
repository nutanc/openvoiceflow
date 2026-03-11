; Open Wispr Flow - Windows Installer Script
; Requires Inno Setup 6+ (https://jrsoftware.org/isinfo.php)
; 
; Usage:
;   1. Build the EXE first: python -m PyInstaller ... (see build_exe.bat)
;   2. Install Inno Setup from https://jrsoftware.org/isdl.php
;   3. Open this .iss file in Inno Setup Compiler
;   4. Click Build > Compile
;   5. Installer will be at: installer_output/OpenWisprFlow_Setup.exe

#define MyAppName "Open Wispr Flow"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Open Wispr Flow"
#define MyAppURL "https://github.com/open-wispr-flow"
#define MyAppExeName "OpenWisprFlow.exe"

[Setup]
AppId={{A1B2C3D4-E5F6-7890-ABCD-EF1234567890}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=installer_output
OutputBaseFilename=OpenWisprFlow_Setup
SetupIconFile=
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "startup"; Description: "Start Open Wispr Flow on Windows startup"; GroupDescription: "Startup:"

[Files]
; Main EXE (built by PyInstaller)
Source: "dist\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

; Create a launcher batch file that prompts for API key on first run
Source: "launcher.bat"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\launcher.bat"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{commondesktop}\{#MyAppName}"; Filename: "{app}\launcher.bat"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Registry]
; Auto-start on login (optional)
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "OpenWisprFlow"; ValueData: """{app}\launcher.bat"""; Flags: uninsdeletevalue; Tasks: startup

[Run]
Filename: "{app}\launcher.bat"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent shellexec

[Code]
var
  ApiKeyPage: TInputQueryWizardPage;

procedure InitializeWizard;
begin
  ApiKeyPage := CreateInputQueryPage(wpSelectTasks,
    'OpenAI API Key',
    'Enter your OpenAI API key to use voice dictation.',
    'You can get an API key from https://platform.openai.com/api-keys'#13#10#13#10 +
    'This will be saved as an environment variable for your user account.');

  ApiKeyPage.Add('API Key (sk-...):', False);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ApiKey: String;
begin
  if CurStep = ssPostInstall then
  begin
    ApiKey := ApiKeyPage.Values[0];
    if ApiKey <> '' then
    begin
      { Set user environment variable }
      RegWriteStringValue(HKEY_CURRENT_USER,
        'Environment',
        'OPENAI_API_KEY',
        ApiKey);
      { Notify system of env var change }
    end;
  end;
end;
