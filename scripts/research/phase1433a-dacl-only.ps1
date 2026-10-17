$ErrorActionPreference = 'Stop'
if (-not ('Phase1433ADacl' -as [type])) {
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class Phase1433ADacl {
 [DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
 public static extern uint SetNamedSecurityInfo(string name, int kind, uint info, IntPtr owner, IntPtr group, IntPtr dacl, IntPtr sacl);
 [DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
 public static extern bool ConvertStringSecurityDescriptorToSecurityDescriptor(string s, uint revision, out IntPtr sd, out uint size);
 [DllImport("advapi32.dll", SetLastError=true)]
 public static extern bool GetSecurityDescriptorDacl(IntPtr sd, out bool present, out IntPtr dacl, out bool defaulted);
 [DllImport("kernel32.dll")] public static extern IntPtr LocalFree(IntPtr p);
}
"@
}
function Assert-CustodyPath([string]$Path, [string]$Checkout) {
 if ($Path -notmatch '^[A-Za-z]:[\\/]' -or $Path -match '(^|[\\/])\.\.([\\/]|$)') { throw 'PATH_FORMAT' }
 $full=[IO.Path]::GetFullPath($Path).TrimEnd('\')
 $root=[IO.Path]::GetFullPath($Checkout).TrimEnd('\')
 if ($full -eq $root -or $full.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'CHECKOUT_PATH' }
 $cursor=$full
 while ($cursor) {
  if (Test-Path -LiteralPath $cursor) {
   $item=Get-Item -Force -LiteralPath $cursor
   if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'REPARSE_POINT' }
  }
  $cursor=[IO.Path]::GetDirectoryName($cursor)
 }
 return $full
}
function Set-CustodyDaclOnly([string]$Path,[string]$Checkout,[string]$ExpectedOwner) {
 $full=Assert-CustodyPath $Path $Checkout
 $item=Get-Item -Force -LiteralPath $full
 if (-not $item.PSIsContainer) { throw 'NOT_DIRECTORY' }
 if (@(Get-ChildItem -Force -LiteralPath $full).Count -ne 0) { throw 'NOT_EMPTY' }
 $before=Get-Acl -LiteralPath $full
 $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
 if ($ExpectedOwner -ne $sid -or $before.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $sid) { throw 'OWNER_MISMATCH' }
 $sddl='D:P(A;OICI;FA;;;'+$sid+')(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)'
 $sd=[IntPtr]::Zero; $size=[uint32]0
 try {
  if (-not [Phase1433ADacl]::ConvertStringSecurityDescriptorToSecurityDescriptor($sddl,1,[ref]$sd,[ref]$size)) { throw 'SDDL_CONVERSION' }
  $present=$false; $defaulted=$false; $dacl=[IntPtr]::Zero
  if (-not [Phase1433ADacl]::GetSecurityDescriptorDacl($sd,[ref]$present,[ref]$dacl,[ref]$defaulted) -or -not $present -or $dacl -eq [IntPtr]::Zero) { throw 'DACL_MISSING' }
  # DACL_SECURITY_INFORMATION | PROTECTED_DACL_SECURITY_INFORMATION only.
  # No OWNER, GROUP or SACL flags; all corresponding pointers are null.
  $code=[Phase1433ADacl]::SetNamedSecurityInfo($full,1,[uint32]2147483652,[IntPtr]::Zero,[IntPtr]::Zero,$dacl,[IntPtr]::Zero)
  if ($code -ne 0) { throw ('DACL_WIN32_ERROR_'+$code) }
 } finally { if ($sd -ne [IntPtr]::Zero) { [void][Phase1433ADacl]::LocalFree($sd) } }
 $after=Get-Acl -LiteralPath $full
 if ($before.Owner -ne $after.Owner -or $before.Group -ne $after.Group) { throw 'OWNER_GROUP_CHANGED' }
 $rules=@($after.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier]))
 $expected=@($sid,'S-1-5-18','S-1-5-32-544')
 if (-not $after.AreAccessRulesProtected -or $rules.Count -ne 3) { throw 'DACL_SHAPE' }
 foreach ($rule in $rules) {
  if ($rule.IdentityReference.Value -notin $expected -or $rule.IsInherited -or $rule.AccessControlType -ne 'Allow' -or $rule.FileSystemRights -ne 'FullControl' -or $rule.InheritanceFlags -ne 'ContainerInherit, ObjectInherit' -or $rule.PropagationFlags -ne 'None') { throw 'DACL_RULE' }
 }
 [pscustomobject]@{Win32ExitCode=$code;SecurityInformation='0x80000004';OwnerUnchanged=$true;GroupUnchanged=$true;Protected=$true;RuleCount=3;SaclRequested=$false;Sddl=$after.Sddl}
}
