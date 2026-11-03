$ErrorActionPreference='Stop'
if($env:E2E_ISOLATED -ne '1'){throw 'Use test-browser.ps1. Issue smoke writes require the isolated E2E database.'}
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
$lab=(Api POST '/api/auth/login' @{username='lab';password=$env:DEMO_LAB_PASSWORD} '').accessToken
$tag=[Guid]::NewGuid().ToString('N').Substring(0,8)
$category=Api POST '/api/material-categories' @{name="Issue smoke $tag"} $admin
$item=Api POST '/api/materials' @{categoryId=$category.id;name="Issue resistor $tag";unit='piece';unitPrice='0.1234';safeStock='10';status='ACTIVE'} $admin
Api PATCH "/api/materials/$($item.id)/issue-limit" @{monthlyIssueLimit='50'} $admin | Out-Null
Api PATCH "/api/materials/$($item.id)/issue-limit" @{monthlyIssueLimit='999'} $lab 403 | Out-Null
$inbound=Api POST '/api/inbound-orders' @{idempotencyKey=[Guid]::NewGuid().ToString();lines=@(@{itemId=$item.id;quantity='800'})} $admin
$batch=$inbound.lines[0]
$body=@{idempotencyKey=[Guid]::NewGuid().ToString();projectNo="P-$tag";usage='Circuit lab';lines=@(@{batchId=$batch.id;quantity='20'})}
$created=Api POST '/api/issues' $body $student
$again=Api POST '/api/issues' $body $student
if($created.order.id -ne $again.order.id -or [decimal]$created.order.totalAmount -ne 2.468){throw 'Issue idempotency or amount mismatch'}
$stock=Api GET "/api/stock-batches/$($batch.batchCode)" $null $student
if([decimal]$stock.quantity -ne 780){throw '800 minus 20 must be 780'}
$quota=Api GET "/api/materials/$($item.id)/issue-quota" $null $student
if([decimal]$quota.usedQuantity -ne 20 -or [decimal]$quota.remainingQuantity -ne 30){throw 'Monthly quota mismatch'}
$body.lines[0].quantity='1'
Api POST '/api/issues' $body $student 409 | Out-Null
$body.idempotencyKey=[Guid]::NewGuid().ToString();$body.lines[0].quantity='30'
Api POST '/api/issues' $body $student | Out-Null
$body.idempotencyKey=[Guid]::NewGuid().ToString();$body.lines[0].quantity='1'
Api POST '/api/issues' $body $student 409 | Out-Null
$other=Api POST '/api/issues' $body $lab
Api GET "/api/issues/$($other.order.id)" $null $student 403 | Out-Null
Api GET "/api/issues/$($other.order.id)/pdf" $null $student 403 | Out-Null
Api GET '/api/issues' $null $student 403 | Out-Null
$history=Api GET "/api/issues/my?projectNo=P-$tag" $null $student
if($history.total -ne 2){throw 'Persisted private history mismatch'}
$tx=Api GET "/api/stock-transactions?txnType=OUT&itemId=$($item.id)" $null $admin
if($tx.total -ne 3 -or $tx.records[0].referenceType -ne 'ISSUE'){throw 'OUT ledger trace mismatch'}
$pdfPath=Join-Path (Split-Path $PSScriptRoot -Parent) '.runtime/sprint04-http-voucher.pdf'
Invoke-WebRequest -UseBasicParsing -Uri "$base/api/issues/$($created.order.id)/pdf" -Headers @{Authorization="Bearer $student"} -OutFile $pdfPath
$signature=[Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($pdfPath),0,5)
if($signature -ne '%PDF-'){throw 'PDF download is not a real PDF'}
Write-Host 'PASS authenticated real PDF download'
Write-Host 'Issue HTTP smoke passed: exact stock deduction, quota, idempotency, private records, OUT ledger and PDF.'
