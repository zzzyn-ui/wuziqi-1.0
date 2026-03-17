Get-Process | Where-Object {$_.ProcessName -eq 'java'} | ForEach-Object {
    $proc = $_
    try {
        $wmic = Get-WmiObject Win32_Process -Filter "ProcessId=$($proc.Id)"
        [PSCustomObject]@{
            ProcessId = $proc.Id
            StartTime = $proc.StartTime
            CommandLine = $wmic.CommandLine
        }
    } catch {
        [PSCustomObject]@{
            ProcessId = $proc.Id
            StartTime = $proc.StartTime
            CommandLine = "Access Denied"
        }
    }
} | Format-List
