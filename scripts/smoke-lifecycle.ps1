$ErrorActionPreference='Stop'
if($env:E2E_ISOLATED -ne '1'){throw 'Use test-browser.ps1. Lifecycle writes require the isolated E2E database.'}
. (Join-Path $PSScriptRoot 'load-env.ps1')
$base='http://127.0.0.1:8081'
function Api([string]$Method,[string]$Path,[object]$Body,[string]$Token,[int]$Expected=200){
 $headers=@{};if($Token){$headers.Authorization="Bearer $Token"}
 $parameters=@{Uri="$base$Path";Method=$Method;Headers=$headers;ContentType='application/json; charset=utf-8'}
 if($null -ne $Body){$parameters.Body=[Text.Encoding]::UTF8.GetBytes(($Body|ConvertTo-Json -Depth 10))}
 try{$r=Invoke-RestMethod @parameters;$code=200}catch{if(!$_.Exception.Response){throw};$code=[int]$_.Exception.Response.StatusCode;$r=$null}
 if($code -ne $Expected){throw "$Method $Path expected $Expected, got $code"}
 Write-Host "PASS $Method $Path -> $Expected"
 if($r){if($r.code -ne 0){throw 'Unexpected response'};return $r.data}
}
$admin=(Api POST '/api/auth/login' @{username='admin';password=$env:DEMO_ADMIN_PASSWORD} '').accessToken
$student=(Api POST '/api/auth/login' @{username='student';password=$env:DEMO_STUDENT_PASSWORD} '').accessToken
$tag=[Guid]::NewGuid().ToString('N').Substring(0,8)
$category=Api POST '/api/material-categories' @{name="Lifecycle smoke $tag"} $admin
$item=Api POST '/api/materials' @{categoryId=$category.id;name="Return resistor $tag";unit='piece';unitPrice='0.1234';safeStock='100';status='ACTIVE'} $admin
$inbound=Api POST '/api/inbound-orders' @{idempotencyKey=[Guid]::NewGuid().ToString();lines=@(@{itemId=$item.id;quantity='100'})} $admin
$batch=$inbound.lines[0]
$issued=Api POST '/api/issues' @{idempotencyKey=[Guid]::NewGuid().ToString();projectNo="P-$tag";course='Electronics';usage='Circuit';lines=@(@{batchId=$batch.id;quantity='20'})} $student
$returnBody=@{idempotencyKey=[Guid]::NewGuid().ToString();issueOrderId=$issued.order.id;lines=@(@{issueOrderLineId=$issued.lines[0].id;quantity='5'})}
$returned=Api POST '/api/returns' $returnBody $admin
$replay=Api POST '/api/returns' $returnBody $admin
if($returned.order.id -ne $replay.order.id){throw 'Return replay duplicated record'}
Api POST '/api/returns' $returnBody $student 403 | Out-Null
$returnBody.idempotencyKey=[Guid]::NewGuid().ToString();$returnBody.lines[0].quantity='16';Api POST '/api/returns' $returnBody $admin 409 | Out-Null
$scrapBody=@{idempotencyKey=[Guid]::NewGuid().ToString();batchId=$batch.id;quantity='3';reason='Warehouse damage'}
$scrapped=Api POST '/api/scraps' $scrapBody $admin
Api POST '/api/scraps' $scrapBody $admin | Out-Null
$stock=Api GET "/api/stock-batches/$($batch.batchCode)" $null $admin
if([decimal]$stock.quantity -ne 82){throw '100 - 20 + 5 - 3 must be 82'}
$alerts=Api GET "/api/alerts?keyword=$($item.materialCode)&status=ACTIVE" $null $admin
if($alerts.total -ne 1 -or $alerts.records[0].mailStatus -ne 'NOT_CONFIGURED'){throw 'Persistent alert or missing SMTP state mismatch'}
$eventId=$alerts.records[0].id
Api POST "/api/alerts/$eventId/acknowledge" @{note='Procurement contacted'} $admin | Out-Null
$from=(Get-Date).ToString('yyyy-MM-dd');$studentId=$issued.order.userId
foreach($type in @('semester','funding','personal')){
 $filters="from=$from&to=$from&keyword=$($item.materialCode)";if($type -eq 'personal'){$filters+="&userId=$studentId"}
 $preview=Api GET "/api/reports/${type}?$filters" $null $admin
 if($preview.total -ne 1 -or [decimal]$preview.records[0].netQuantity -ne 15 -or [decimal]$preview.records[0].netAmount -ne 1.851){throw 'Report preview is not exact'}
 foreach($format in @('xlsx','pdf')){
  $target=Join-Path (Split-Path $PSScriptRoot -Parent) ".runtime/sprint05-http-$type.$format"
  Invoke-WebRequest -UseBasicParsing -Uri "$base/api/reports/$type/export/${format}?$filters" -Headers @{Authorization="Bearer $admin"} -OutFile $target
  $bytes=[IO.File]::ReadAllBytes($target);$magic=[Text.Encoding]::ASCII.GetString($bytes,0,2)
  if(($format -eq 'xlsx' -and $magic -ne 'PK') -or ($format -eq 'pdf' -and $magic -ne '%P')){throw 'Invalid export signature'}
  Write-Host "PASS authenticated $type.$format HTTP download"
 }
}
Api GET '/api/reports/funding' $null $student 403 | Out-Null
Api POST '/api/inbound-orders' @{idempotencyKey=[Guid]::NewGuid().ToString();lines=@(@{itemId=$item.id;quantity='18'})} $admin | Out-Null
$event=Api GET "/api/alerts/$eventId" $null $admin
if($event.status -ne 'RESOLVED'){throw 'Restored inventory did not resolve alert'}
Write-Host 'Lifecycle PostgreSQL HTTP smoke passed: return, scrap, idempotency, ledger, alerts, reports and all six downloads.'
