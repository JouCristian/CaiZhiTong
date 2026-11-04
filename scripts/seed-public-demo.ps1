param([string]$BaseUrl='http://127.0.0.1:8080',[string]$Username='admin',[Security.SecureString]$AdminPassword,[switch]$AllowCloudWrites)
$ErrorActionPreference='Stop'
$uri=[Uri]$BaseUrl
if($uri.Scheme -ne 'https' -and !($uri.Scheme -eq 'http' -and $uri.Host -in @('127.0.0.1','localhost'))){throw 'Use HTTPS or loopback.'}
if($uri.Host -notin @('127.0.0.1','localhost') -and !$AllowCloudWrites){throw 'Cloud demonstration writes require explicit -AllowCloudWrites; never import local user data.'}
$secure=if($AdminPassword){$AdminPassword}else{Read-Host 'Administrator password (hidden)' -AsSecureString}
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try{$plainPassword=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
$token=$null
function Call-Api([string]$Method,[string]$Path,[object]$Body){
 $headers=@{};if($token){$headers.Authorization='Bearer '+$token}
 $parameters=@{Uri=($BaseUrl.TrimEnd('/')+$Path);Method=$Method;Headers=$headers;ContentType='application/json; charset=utf-8';TimeoutSec=240}
 if($null -ne $Body){$parameters.Body=[Text.Encoding]::UTF8.GetBytes(($Body|ConvertTo-Json -Depth 12))}
 try{$r=Invoke-RestMethod @parameters}catch{throw 'Demo API request failed; credentials and provider responses suppressed.'}
 if($r.code -ne 0){throw 'Demo API returned an unsuccessful response.'};return $r.data
}
try{
 $token=(Call-Api POST '/api/auth/login' @{username=$Username;password=$plainPassword}).accessToken
 $keyword=[Uri]::EscapeDataString('公开体验·合成电阻')
 $existing=Call-Api GET ('/api/materials?keyword='+$keyword) $null
 if($existing.total -gt 1){throw 'Ambiguous synthetic material name; no existing records changed.'}
 if($existing.total -eq 1){$item=$existing.records[0];if($item.model -ne 'DEMO-R10K'){throw 'Existing material is not owned by this seed script; no existing records changed.'}}
 else {
  $categories=Call-Api GET '/api/material-categories/tree' $null
  $category=@($categories|Where-Object {$_.name -eq '公开体验·合成材料'})|Select-Object -First 1
  if(!$category){$category=Call-Api POST '/api/material-categories' @{name='公开体验·合成材料';description='仅供公开只读体验，不含真实个人信息'}}
  $item=Call-Api POST '/api/materials' @{categoryId=$category.id;name='公开体验·合成电阻';brand='示例品牌';model='DEMO-R10K';spec='10kΩ ±1% · 合成演示';unit='只';unitPrice='0.1000';currency='CNY';safeStock='10';status='ACTIVE';description='课程项目合成演示材料，非真实采购数据'}
 }
 Call-Api POST '/api/inbound-orders' @{idempotencyKey='d76d7f47-0dad-4811-9ee9-0e5788690070';remark='公开体验合成数据';lines=@(@{itemId=$item.id;quantity='100';location='演示库位'})}|Out-Null
 Write-Output ('Synthetic material stored through protected APIs: '+$item.materialCode+'; initial stock 100. No user records imported.')
}finally{$plainPassword=$null;$token=$null}
