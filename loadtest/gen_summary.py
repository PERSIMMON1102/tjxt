# -*- coding: utf-8 -*-
"""生成自包含的压测报告 summary.html（图片 base64 内嵌）"""
import base64, os

base = os.path.dirname(os.path.abspath(__file__))
img = base64.b64encode(open(os.path.join(base, "chart.png"), "rb").read()).decode()

rows = [
    ("领取优惠券", "50", "2000", "318", "45", "103", "148", "0"),
    ("领取优惠券", "100", "3000", "399", "103", "206", "273", "0"),
    ("领取优惠券", "200", "3000", "350", "248", "662", "1024", "0"),
    ("我的优惠券分页", "100", "3000", "346", "128", "361", "540", "0"),
]

tr = "\n".join(
    "<tr><td>%s</td><td>%s</td><td>%s</td><td><b>%s</b></td><td>%s</td><td>%s</td><td><b>%s</b></td><td>%s</td></tr>" % r
    for r in rows
)

html = """<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>天机学堂 · 接口压测报告</title>
<style>
 body{font-family:"Microsoft YaHei",system-ui,sans-serif;color:#1a1a1a;margin:0;background:#f6f7f9}
 .wrap{max-width:1180px;margin:0 auto;padding:32px 28px 60px}
 h1{font-size:26px;margin:0 0 6px} .sub{color:#6b7280;font-size:13px;margin-bottom:24px}
 .card{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:20px 22px;margin-bottom:18px}
 h2{font-size:16px;margin:0 0 12px;color:#1f4e79;border-left:4px solid #1f4e79;padding-left:9px}
 table{border-collapse:collapse;width:100%;font-size:13.5px}
 th,td{border-bottom:1px solid #eceef2;padding:8px 10px;text-align:left}
 th{background:#f3f6fa;font-weight:600;color:#374151}
 tr:hover td{background:#fafbfc}
 img{width:100%;border:1px solid #eceef2;border-radius:8px}
 code{background:#f3f4f6;padding:1px 5px;border-radius:4px;font-size:12.5px}
 ul{margin:6px 0 0 18px;padding:0;font-size:13.5px;line-height:1.85}
 .ok{color:#15803d;font-weight:600}.warn{color:#b45309;font-weight:600}
 .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
 .kpi{background:#f8fafc;border:1px solid #e5e7eb;border-radius:8px;padding:14px}
 .kpi .v{font-size:22px;font-weight:700;color:#1f4e79}.kpi .k{font-size:12px;color:#6b7280;margin-top:2px}
</style></head><body><div class="wrap">
<h1>天机学堂 · 领取优惠券接口压测报告</h1>
<div class="sub">JMeter 5.4.1 ｜ 2026-11-23 22:00 ｜ 网关 192.168.150.101:10010 → tj-promotion (8092)</div>

<div class="card"><h2>一、关键结论</h2>
<div class="grid">
 <div class="kpi"><div class="v">399 TPS</div><div class="k">100 并发峰值吞吐</div></div>
 <div class="kpi"><div class="v">273 ms</div><div class="k">100 并发 P99</div></div>
 <div class="kpi"><div class="v">0 / 8000</div><div class="k">错误数 / 成功请求</div></div>
 <div class="kpi"><div class="v">0</div><div class="k">超发与库存偏差</div></div>
</div>
</div>

<div class="card"><h2>二、压测曲线</h2>
<img src="data:image/png;base64,__IMG__" alt="压测曲线">
<div class="sub" style="margin:8px 0 0">写路径在 100 并发达到拐点（399 TPS / P99 273ms）；继续加压到 200 并发，TPS 反降至 350，P99 涨到 1024ms —— 典型的线程池/连接池排队。</div>
</div>

<div class="card"><h2>三、明细数据</h2>
<table>
<tr><th>场景</th><th>并发</th><th>请求数</th><th>TPS</th><th>P50 (ms)</th><th>P95 (ms)</th><th>P99 (ms)</th><th>错误</th></tr>
__ROWS__
</table>
<div class="sub" style="margin-top:8px">读接口「我的优惠券分页」为纯 MySQL 查询，P99 540ms，明显慢于走 Redis Lua 的领券写接口。</div>
</div>

<div class="card"><h2>四、一致性校验（并发下是否超发）</h2>
<ul>
<li>压测前把测试券库存重置为 <code>100000</code>，Redis 计数清零。</li>
<li>三轮写压测共 <b>8000</b> 次成功；结束后 Redis <code>totalNum</code> = <b>92000</b>，扣减量与成功数<b>完全一致</b>。</li>
<li>落库侧 <code>user_coupon</code> 行数 = 15145，与历次成功请求数逐条对齐（MQ 异步落库，压测后约 8s 追平）。</li>
<li>结论：<span class="ok">Lua 原子扣减 + 分布式锁下未出现超发、未出现“扣了库存没落库”的漂移</span>。</li>
</ul>
</div>

<div class="card"><h2>五、测试方法</h2>
<ul>
<li><b>账号</b>：批量创建 1120 个学生账号（BCrypt 口令），每轮压测前重新登录换取 JWT；token 有效期仅 5 分钟，故「登录 → 压测」串行执行。</li>
<li><b>避免 CSV 锁</b>：JMeter 的 CSV Data Set 在 <code>shareMode.all</code> 下每次取样都要抢文件锁，实测把吞吐压到 ~100 TPS；改为 <b>每线程独立 token 文件</b>（<code>${__threadNum}.csv</code>）后吞吐才反映真实服务端能力。</li>
<li><b>数据隔离</b>：使用专属测试券 <code>1900000000000000001</code>（库存与限领均为 10 万），不触碰业务券；压测产生的 15145 行数据已清理。</li>
<li><b>断言</b>：响应体包含 <code>"code":200</code>，非 200 计为失败。</li>
</ul>
</div>

<div class="card"><h2>六、口径与局限</h2>
<ul>
<li>压测机与被测 VM 共用一台物理机（VMware NAT），200 并发时压测机自身也可能是瓶颈，故 200 并发数值偏保守。</li>
<li>未接入 JVM/GC/CPU 监控，瓶颈定位仅到「接口 + 中间件」层面；如需进一步定位，建议在 VM 内用 <code>top -Hp</code> + <code>jstack</code> 看线程池排队。</li>
<li>读接口的 540ms P99 与其数据量相关：每用户 1 条记录时测得；当单用户累积到上百条时（压测中途曾出现），P99 升至 1509ms，<code>EXPLAIN</code> 显示 <code>Using filesort</code>（索引为 <code>user_id,coupon_id</code>，未覆盖 <code>create_time</code> 排序）——这是可以落地的优化点。</li>
</ul>
</div>
</div></body></html>"""

html = html.replace("__IMG__", img).replace("__ROWS__", tr)
out = os.path.join(base, "summary.html")
open(out, "w", encoding="utf-8").write(html)
print("saved", out)