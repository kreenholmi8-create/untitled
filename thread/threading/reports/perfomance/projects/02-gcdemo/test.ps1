for ($i = 1; $i -le 100; $i++) {
    Invoke-WebRequest -Uri "http://localhost:8080/memory/leak/bulk?count=5" -UseBasicParsing
    Write-Host "Итерация $i"
    Start-Sleep -Seconds 1
}