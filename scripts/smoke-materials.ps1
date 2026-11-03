$ErrorActionPreference = 'Stop'
if ($env:E2E_ISOLATED -ne '1') { throw 'Run through scripts/test-browser.ps1. Write smoke tests require the isolated E2E database.' }
. (Join-Path $PSScriptRoot 'load-env.ps1')
$base = 'http://127.0.0.1:8081'
function Api([string]$Method,[string]$Path,[object]$Body,[string]$Token,[int]$Expected=200) {
    $headers=@{}
    if ($Token) { $headers.Authorization="Bearer $Token" }
    $parameters=@{Uri="$base$Path"; Method=$Method; Headers=$headers; ContentType='application/json; charset=utf-8'}
    if ($null -ne $Body) { $parameters.Body=[Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10)) }
    try { $result=Invoke-RestMethod @parameters; $code=200 }
    catch { if (!$_.Exception.Response) { throw }; $code=[int]$_.Exception.Response.StatusCode; $result=$null }
    if ($code -ne $Expected) { throw "$Method $Path expected $Expected, got $code" }
    Write-Host "PASS $Method $Path -> $Expected"
    if ($null -ne $result) { if ($result.code -ne 0) { throw 'Unexpected API envelope' }; return $result.data }
}
$admin=(Api POST '/api/auth/login' @{username='admin';password=$env:DEMO_ADMIN_PASSWORD} '').accessToken
$lab=(Api POST '/api/auth/login' @{username='lab';password=$env:DEMO_LAB_PASSWORD} '').accessToken
$student=(Api POST '/api/auth/login' @{username='student';password=$env:DEMO_STUDENT_PASSWORD} '').accessToken
$tag=[Guid]::NewGuid().ToString('N').Substring(0,10)
$root=Api POST '/api/material-categories' @{name="API smoke $tag";sortOrder=0} $admin
$child=Api POST '/api/material-categories' @{name="Resistors $tag";parentId=$root.id;sortOrder=0} $admin
Api PUT "/api/material-categories/$($child.id)" @{name="Updated resistors $tag";parentId=$root.id;description='Updated via HTTP';sortOrder=1} $admin | Out-Null
Api PUT "/api/material-categories/$($root.id)" @{name="Cycle $tag";parentId=$child.id} $admin 400 | Out-Null
Api POST '/api/material-categories' @{name="Illegal third level $tag";parentId=$child.id} $admin 400 | Out-Null
$body=@{categoryId=$child.id;name="API resistor $tag";brand='Yageo';model='MFR-25';unit='piece';unitPrice='0.1234';safeStock='10';status='ACTIVE'}
$item=Api POST '/api/materials' $body $lab
if ($item.unitPrice -ne '0.1234' -or $item.materialCode -notmatch '^MAT-[0-9]{10}$') { throw 'Generated code or exact price mismatch' }
$body.name="Updated API resistor $tag"
$updated=Api PUT "/api/materials/$($item.id)" $body $lab
if ($updated.materialCode -ne $item.materialCode) { throw 'Material code changed after editing' }
$page=Api GET "/api/materials?keyword=$($item.materialCode)&page=1&size=10&order=asc" $null $student
if ($page.total -ne 1 -or $page.records[0].name -ne $body.name) { throw 'Persisted query mismatch' }
$body.materialCode=$item.materialCode
Api POST '/api/materials' $body $admin 409 | Out-Null
$body.Remove('materialCode')
$body.unitPrice='-1'
Api POST '/api/materials' $body $admin 400 | Out-Null
$body.unitPrice='0.1234'
Api PUT "/api/materials/$($item.id)" $body $student 403 | Out-Null
Api PATCH "/api/materials/$($item.id)/status" @{status='ARCHIVED'} $lab 403 | Out-Null
Api PATCH "/api/materials/$($item.id)/status" @{status='DISABLED'} $lab | Out-Null
Api PATCH "/api/materials/$($item.id)/status" @{status='ARCHIVED'} $admin | Out-Null
$history=Api GET "/api/materials/$($item.id)" $null $student
if ($history.status -ne 'ARCHIVED') { throw 'Archive did not persist' }
Api DELETE "/api/material-categories/$($child.id)" $null $admin 409 | Out-Null
Api DELETE "/api/material-categories/$($root.id)" $null $admin 409 | Out-Null
$empty=Api POST '/api/material-categories' @{name="Empty $tag"} $admin
Api DELETE "/api/material-categories/$($empty.id)" $null $admin | Out-Null
Write-Host 'Material HTTP smoke checks passed against isolated PostgreSQL data.'

