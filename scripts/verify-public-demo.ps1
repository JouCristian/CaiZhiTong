param([string]$BaseUrl='http://127.0.0.1:8080',[string]$Username='admin',[Security.SecureString]$Password)
$ErrorActionPreference='Stop'
$uri=[Uri]$BaseUrl
if($uri.Scheme -ne 'https' -and !($uri.Scheme -eq 'http' -and $uri.Host -in @('127.0.0.1','localhost'))){throw 'Use HTTPS or loopback.'}
$secure=if($Password){$Password}else{Read-Host 'Account password (hidden)' -AsSecureString}
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try{$plain=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
$token=$null
function Read-Demo([string]$Path){
 try{$response=Invoke-RestMethod -Uri ($BaseUrl.TrimEnd('/')+$Path) -Headers @{Authorization=('Bearer '+$token)} -TimeoutSec 240}
 catch{throw 'Demo query failed; private response suppressed.'}
 if($response.code -ne 0){throw 'Demo query failed.'};return $response.data
}
try{
 try{$token=(Invoke-RestMethod -Uri ($BaseUrl.TrimEnd('/')+'/api/auth/login') -Method POST -ContentType 'application/json' -Body (@{username=$Username;password=$plain}|ConvertTo-Json) -TimeoutSec 240).data.accessToken}
 catch{throw 'Login failed; credentials and response suppressed.'}
 $overview=Read-Demo '/api/demo/dashboard'
 if($overview.scope -ne 'SYNTHETIC_DEMO' -or !$overview.readOnly -or $overview.metrics.materials -ne 25 -or $overview.batchCount -ne 50){throw 'Unexpected independent demo catalogue.'}
 $list=Read-Demo '/api/demo/materials?page=1&size=10'
 if($list.total -ne 25 -or $list.records.Count -ne 10){throw 'Demo paging failed.'}
 $detail=Read-Demo '/api/demo/materials/1'
 if($detail.batches.Count -ne 2){throw 'Demo batches missing.'}
 $batch=Read-Demo ('/api/demo/batches/'+$detail.batches[0].id)
 if($batch.events.Count -ne 2){throw 'Demo events missing.'}
 Write-Output 'PASS: independent synthetic demo catalogue (25 materials / 50 batches), paging, details and events. No business data written.'
}finally{$plain=$null;$token=$null}
