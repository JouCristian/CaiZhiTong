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
$me=Api GET '/api/auth/me' $null $admin
$tag=[Guid]::NewGuid().ToString('N').Substring(0,10)
$name="final_smoke_$tag"
$profile=@{displayName='Administration smoke';email='admin-smoke@example.test';roles=@('LAB_TECH');enabled=$true}
$user=Api POST '/api/users' @{username=$name;credentials=@{password='test-only-user-password-2026'};profile=$profile} $admin
$old=(Api POST '/api/auth/login' @{username=$name;password='test-only-user-password-2026'} '').accessToken
$profile.roles=@('STUDENT')
Api PUT "/api/users/$($user.id)" $profile $admin | Out-Null
Api GET '/api/workspace/modules/inbound' $null $old 403 | Out-Null
Api PATCH "/api/users/$($user.id)/status" @{enabled=$false} $admin | Out-Null
Api GET '/api/auth/me' $null $old 401 | Out-Null
Api PATCH "/api/users/$($user.id)/status" @{enabled=$true} $admin | Out-Null
Api PATCH "/api/users/$($user.id)/password" @{password='changed-test-user-password-2026'} $admin | Out-Null
Api POST '/api/auth/login' @{username=$name;password='test-only-user-password-2026'} '' 401 | Out-Null
Api POST '/api/auth/login' @{username=$name;password='changed-test-user-password-2026'} '' | Out-Null
Api GET '/api/users' $null $student 403 | Out-Null
Api PATCH "/api/users/$($me.id)/status" @{enabled=$false} $admin 409 | Out-Null
$original=Api GET '/api/settings' $null $admin
$payload=$original|ConvertTo-Json -Depth 10|ConvertFrom-Json
$payload.systemDescription='Real persisted API smoke setting'
try {
 $saved=Api PUT '/api/settings' $payload $admin
 $read=Api GET '/api/settings' $null $admin
 if($read.systemDescription -ne $payload.systemDescription){throw 'Settings were not persisted'}
 Api PUT '/api/settings' $payload $student 403 | Out-Null
 $report=Api GET '/api/reports/semester' $null $admin
 if($report.meta.from -ne $read.autumnStart -and $report.meta.from -ne $read.springStart){throw 'Report did not use configured semester'}
} finally { $current=Api GET '/api/settings' $null $admin; $original.revision=$current.revision;Api PUT '/api/settings' $original $admin | Out-Null }
$dashboard=Api GET '/api/workspace/dashboard' $null $admin
if($dashboard.scope -ne 'ALL' -or !$dashboard.canViewPeople){throw 'Admin dashboard scope mismatch'}
$private=Api GET '/api/workspace/dashboard' $null $student
if($private.scope -ne 'PERSONAL' -or $private.ranking.Count -ne 0){throw 'Student dashboard leaked rankings'}
$public=Api GET '/api/public/system' $null ''
if($public.version -ne '0.7.0' -or ($public.PSObject.Properties.Name -match 'password|secret')){throw 'Public settings contract or secrecy failed'}
Write-Host 'Administration HTTP smoke passed: user lifecycle, JWT revocation, settings, semester defaults and scoped dashboard.'
