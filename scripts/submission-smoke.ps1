[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$InfrastructureOnly,
    [string]$QuestionId = "A9-SMOKE-001",
    [string]$Question = "SK하이닉스가 2024년 4월 발표한 신규시설투자의 투자금액과 목적은 무엇이고, 자기자본 대비 비율은 맞는가?",
    [string]$ExpectedReceiptNo = "20240424800596",
    [int]$TimeoutSec = 45
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

function Assert-Condition {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Get-Json {
    param([string]$Uri)

    Invoke-RestMethod -Method Get -Uri $Uri -TimeoutSec $TimeoutSec
}

$liveness = Get-Json "$BaseUrl/actuator/health/liveness"
Assert-Condition ($liveness.status -eq "UP") "Liveness가 UP이 아닙니다."

$readiness = Get-Json "$BaseUrl/actuator/health/readiness"
Assert-Condition ($readiness.status -eq "UP") "Readiness가 UP이 아닙니다."

if ($InfrastructureOnly) {
    Write-Host "A9 infrastructure smoke passed."
    exit 0
}

$encodedQuestionId = [Uri]::EscapeDataString($QuestionId)
$encodedQuestion = [Uri]::EscapeDataString($Question)
$response = Get-Json "$BaseUrl/answer?question_id=$encodedQuestionId&question=$encodedQuestion"

$expectedKeys = @("answer", "question", "question_id", "retrieved_context", "think_trace") | Sort-Object
$actualKeys = @($response.PSObject.Properties.Name) | Sort-Object
$keyDifference = Compare-Object $expectedKeys $actualKeys
Assert-Condition ($null -eq $keyDifference) "평가 응답의 최상위 키가 정확한 5개가 아닙니다."
Assert-Condition ($response.question_id -eq $QuestionId) "question_id가 보존되지 않았습니다."
Assert-Condition ($response.question -eq $Question) "질문 원문이 보존되지 않았습니다."
Assert-Condition (@($response.think_trace).Count -gt 0) "think_trace가 비어 있습니다."
Assert-Condition (@($response.retrieved_context).Count -gt 0) "retrieved_context가 비어 있습니다. A8 실제 데이터 연결을 확인하세요."
Assert-Condition (
    @($response.retrieved_context | Where-Object { $_.receipt_no -eq $ExpectedReceiptNo }).Count -gt 0
) "대표 공시 $ExpectedReceiptNo 가 retrieved_context에 없습니다."
Assert-Condition (-not [string]::IsNullOrWhiteSpace([string]$response.answer)) "answer가 비어 있습니다."
Assert-Condition (
    $response.answer -ne "답변 생성 기능이 아직 연결되지 않았습니다."
) "placeholder 답변이 반환됐습니다."

Write-Host "A9 submission smoke passed."
