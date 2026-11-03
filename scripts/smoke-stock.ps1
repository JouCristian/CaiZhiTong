$ErrorActionPreference = 'Stop'
if ($env:E2E_ISOLATED -ne '1') { throw 'Run scripts/test-browser.ps1; stock writes require the isolated test database.' }
. (Join-Path $PSScriptRoot 'load-env.ps1')
$base='http://127.0.0.1:8081'
function Api([string]$Method,[string]$Path,[object]$Body,[string]$Token,[int]$Expected=200) {
 $headers=@{}; if($Token){$headers.Authorization="Bearer $Token"}
 $parameters=@{Uri="$base$Path";Method=$Method;Headers=$headers;ContentType='application/json; charset=utf-8'}
 if($null -ne $Body){$parameters.Body=[Text.Encoding]::UTF8.GetBytes(($Body|ConvertTo-Json -Depth 10))}
 try{$result=Invoke-RestMethod @parameters;$code=200}catch{if(!$_.Exception.Response){throw};$code=[int]$_.Exception.Response.StatusCode;$result=$null}
 if($code -ne $Expected){throw "$Method $Path expected $Expected, got $code"}
 Write-Host "PASS $Method $Path -> $Expected"
 if($result){if($result.code -ne 0){throw 'Unexpected envelope'};return $result.data}
}
$admin=(Api POST '/api/auth/login' @{username='admin';password=$env:DEMO_ADMIN_PASSWORD} '').accessToken
$lab=(Api POST '/api/auth/login' @{username='lab';password=$env:DEMO_LAB_PASSWORD} '').accessToken
$student=(Api POST '/api/auth/login' @{username='student';password=$env:DEMO_STUDENT_PASSWORD} '').accessToken
$tag=[Guid]::NewGuid().ToString('N').Substring(0,8)
$category=Api POST '/api/material-categories' @{name="Stock smoke $tag"} $admin
$item=Api POST '/api/materials' @{categoryId=$category.id;name="Stock resistor $tag";unit='piece';unitPrice='0.1234';currency='CNY';safeStock='10';status='ACTIVE'} $admin
$body=@{idempotencyKey=[Guid]::NewGuid().ToString();remark="Stock smoke $tag";lines=@(@{itemId=$item.id;quantity='2.125';location='A-01'},@{itemId=$item.id;quantity='3.875';unitPrice='0.2345'})}
Api POST '/api/inbound-orders' $body $student 403 | Out-Null
$created=Api POST '/api/inbound-orders' $body $lab
$replayed=Api POST '/api/inbound-orders' $body $lab
if($created.order.id -ne $replayed.order.id -or $created.lines.Count -ne 2){throw 'Idempotency or multiline failure'}
$stock=Api GET "/api/stocks?itemId=$($item.id)&size=1" $null $student
if([decimal]$stock.records[0].quantity -ne 6 -or $stock.records[0].batchCount -ne 2){throw 'Stock aggregation mismatch'}
$tx=Api GET "/api/stock-transactions?itemId=$($item.id)&txnType=IN" $null $admin
if($tx.total -ne 2){throw 'Ledger rows mismatch'}
foreach($row in $tx.records){if([decimal]$row.beforeQuantity -ne 0 -or [decimal]$row.afterQuantity -ne [decimal]$row.qty -or $row.referenceNo -ne $created.order.orderNo){throw 'Ledger trace mismatch'}}
$batch=$created.lines[0].batchCode
$detail=Api GET "/api/stock-batches/$batch" $null $student
if($detail.qrContent -ne "http://127.0.0.1:5180/stock/batches/$batch"){throw 'QR origin mismatch'}
$png=Invoke-WebRequest -UseBasicParsing -Uri "$base/api/stock-batches/$batch/qrcode" -Headers @{Authorization="Bearer $admin"}
if($png.Headers['Content-Type'] -notlike 'image/png*' -or $png.RawContentLength -lt 100){throw 'Invalid QR PNG'}
Write-Host 'PASS authenticated QR PNG'
$body.lines[0].quantity='9'
Api POST '/api/inbound-orders' $body $lab 409 | Out-Null
Api POST '/api/inbound-orders' @{idempotencyKey=[Guid]::NewGuid().ToString();lines=@(@{itemId=$item.id;quantity='0'})} $admin 400 | Out-Null
Api GET '/api/stock-batches/BAT-9999999999' $null $admin 404 | Out-Null
Api GET "/api/stock-batches/$batch" $null '' 401 | Out-Null
$history=Api GET "/api/inbound-orders/$($created.order.id)" $null $admin
if($history.lines[0].unitPriceSnapshot -ne '0.1234'){throw 'Price snapshot mismatch'}
Write-Host 'Stock HTTP smoke passed: real PostgreSQL, replay, exact quantities, ledger, QR and permissions.'
