
$inputJson = $input | Out-String | ConvertFrom-Json

$toolName = $inputJson.tool_name
$errorMsg = $inputJson.tool_response.error

if ($errorMsg) {
    $detail = "Tool '$toolName' failed with error: $errorMsg"
    $output = @{
        decision = "allow"
        systemMessage = "DEBUG: $detail"
        hookSpecificOutput = @{
            additionalContext = "The tool '$toolName' failed with the following detailed error: $errorMsg. Please inform the user or try to recover."
        }
    }
} else {
    $output = @{
        decision = "allow"
    }
}

$output | ConvertTo-Json -Compress
