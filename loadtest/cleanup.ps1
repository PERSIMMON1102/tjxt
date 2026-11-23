# 清理压测产生的测试数据（只删本次压测新建的账号/券，不碰业务数据）
# 用法：powershell -ExecutionPolicy Bypass -File cleanup.ps1

$cp = ".;C:\Users\20895\.m2\repository\mysql\mysql-connector-java\8.0.23\mysql-connector-java-8.0.23.jar"
$root = "D:\pojo\code\tianji\loadtest"

Write-Host "[1/3] 删除测试券与用户券记录 ..."
& D:\jdk-11\bin\java.exe -cp $cp SqlExec "delete from tj_promotion.user_coupon where coupon_id=1900000000000000001"
& D:\jdk-11\bin\java.exe -cp $cp SqlExec "delete from tj_promotion.coupon where id=1900000000000000001"

Write-Host "[2/3] 删除 1120 个压测账号 ..."
& D:\jdk-11\bin\java.exe -cp $cp SqlExec "delete from tj_user.user where id>=1700000000000000000"

Write-Host "[3/3] 清理 Redis 缓存 ..."
Push-Location $root
& D:\jdk-11\bin\java.exe -cp . Resp DEL "prs:coupon:1900000000000000001"
& D:\jdk-11\bin\java.exe -cp . Resp DEL "prs:user:coupon:1900000000000000001"
Pop-Location

Write-Host "done."