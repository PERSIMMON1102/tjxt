# -*- coding: utf-8 -*-
"""生成《天机学堂》项目简历素材 docx：版式对齐参考简历（9pt 正文、加粗标签、•/（n）层级）。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_TAB_ALIGNMENT
from docx.enum.section import WD_SECTION
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

FONT = "微软雅黑"
INK = RGBColor(0x1A, 0x1A, 0x1A)
GREY = RGBColor(0x59, 0x59, 0x59)
ACCENT = RGBColor(0x1F, 0x4E, 0x79)

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "天机学堂-项目经历.docx")


def style_run(run, size=9, bold=False, color=INK, italic=False):
    run.font.name = FONT
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = color
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), FONT)
    rfonts.set(qn("w:hAnsi"), FONT)
    rfonts.set(qn("w:eastAsia"), FONT)
    return run


def para(doc, space_after=2, space_before=0, line=1.18, indent=0, hanging=0, align=None):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_after = Pt(space_after)
    pf.space_before = Pt(space_before)
    pf.line_spacing = line
    pf.left_indent = Cm(indent)
    if hanging:
        pf.first_line_indent = Cm(-hanging)
    if align is not None:
        p.alignment = align
    return p


def add_bottom_border(p, color="1F4E79", size=8):
    pPr = p._element.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), str(size))
    bottom.set(qn("w:space"), "1")
    bottom.set(qn("w:color"), color)
    pbdr.append(bottom)
    pPr.append(pbdr)


def section_head(doc, text):
    p = para(doc, space_before=10, space_after=6)
    style_run(p.add_run(text), size=12, bold=True, color=ACCENT)
    add_bottom_border(p)
    return p


def bullet(doc, label, body, indent=0.0):
    p = para(doc, space_after=3, indent=indent + 0.38, hanging=0.38)
    style_run(p.add_run("• "), size=9, bold=True, color=ACCENT)
    if label:
        style_run(p.add_run(label + "："), size=9, bold=True)
    style_run(p.add_run(body), size=9)
    return p


def numbered(doc, idx, label, body, indent=0.0):
    p = para(doc, space_after=3, indent=indent + 0.75, hanging=0.75)
    style_run(p.add_run("（%d）" % idx), size=9, bold=True, color=ACCENT)
    if label:
        style_run(p.add_run(label + "："), size=9, bold=True)
    style_run(p.add_run(body), size=9)
    return p


def plain(doc, text, size=9, bold=False, color=INK, indent=0.0, space_after=3):
    p = para(doc, space_after=space_after, indent=indent)
    style_run(p.add_run(text), size=size, bold=bold, color=color)
    return p


def project_header(doc, title, period, subtitle, role):
    p = para(doc, space_before=4, space_after=1)
    p.paragraph_format.tab_stops.add_tab_stop(Cm(17.4), WD_TAB_ALIGNMENT.RIGHT)
    style_run(p.add_run(title), size=10.5, bold=True)
    style_run(p.add_run("\t" + period), size=9, color=GREY)
    p2 = para(doc, space_after=5, indent=0.0)
    style_run(p2.add_run(role), size=8.5, color=GREY)


FULL = [
    ("项目概述",
     "面向 C 端学员的一站式在线学习平台，按业务域拆分为课程、交易、支付、学习、考试、营销、搜索、消息等 "
     "12 个业务微服务，连同网关、鉴权与公共模块共 16 个 Maven 模块，打通“选课 → 下单 → 支付 → 学习 → 考试 → 互动 → 积分”全链路；"
     "本人负责学习域与营销域共 6 个模块的设计与实现，整体工程量近 800 个 Java 类。"),
    ("技术栈",
     "Spring Boot 2.7、Spring Cloud Alibaba（Nacos 注册配置中心 / Gateway 网关 / Sentinel 限流降级）、"
     "MyBatis-Plus、MySQL、Redis + Redisson、RabbitMQ、Elasticsearch 7.12、MongoDB、XXL-Job、Docker。"),
]

WORK = [
    ("营销域从 0 到 1",
     "独立搭建优惠券服务，落地券模板、发放计划、用户领取、兑换码、到期提醒全流程；"
     "把库存、时间窗、限领次数、发放渠道四类校验收敛进一段 Lua 脚本原子执行，配合 Redisson 分布式锁，"
     "规避超发、重复领取与并发覆盖。"),
    ("最优券组合求解",
     "以策略模式抽象满减、无门槛、折扣率、每满减 4 类券，新增券型仅需扩展一个策略类；"
     "下单时先按券的适用范围细筛可用课程，再排列组合出全部用券方案，用 CompletableFuture + 独立线程池并行计算，"
     "CountDownLatch 设 1s 超时兜底，最终按“优惠金额最大、用券数量最少”双条件去重，返回 TopN 方案供用户自选。"),
    ("交易链路打通",
     "为订单服务提供优惠试算与券核销的 Feign 接口，下单时把优惠金额按券明细分摊到每门课程；"
     "跨服务调用接入 Sentinel fallback，营销服务抖动不阻塞下单主链路。"),
    ("兑换码体系",
     "以“Redis 自增序列号 + 14 位校验码 + Base32”生成 10 位兑换码，字符集剔除易混淆字符；"
     "兑换时一段 Lua 完成“防重复兑换 + 定位券 + 限领校验”，无需回表即可校验真伪，"
     "并发下依靠 Bitmap 置位保证一码一兑。"),
    ("积分与排行榜",
     "积分按行为类型设每日上限，MySQL 存流水、Redis ZSet 存实时榜；榜单按赛季（自然月）分表并动态路由表名，"
     "XXL-Job 分片广播把 Redis 榜持久化落库，实现“当前赛季读 Redis、历史赛季读 MySQL”，"
     "榜单查询与我的排名均为 O(logN)。"),
    ("签到与用户增长",
     "Redis Bitmap 以“用户 + 月”为粒度存签到状态，setBit 天然幂等去重，bitfield 一次取回整月数据计算连续天数，"
     "按 7/14/28 天阶梯发放奖励积分，积分入账走 MQ 异步削峰；单人单月签到状态仅占 4 Byte。"),
    ("互动社区与课表",
     "问答—回答—评论两级结构，点赞数先落 Redis 计数、MQ 异步回写 DB，列表按热度排序以规避深分页，"
     "批量查询点赞与收藏状态消除 N+1；学习进度先写 Redis 再由定时任务批量落库，降低高频写放大；"
     "考试模块以 MongoDB 承载题目与答卷，支持章节测验与限时交卷。"),
]

PRACTICE = ("工程实践",
            "公共模块沉淀分布式锁注解（Redisson）、RabbitMQ 发送工具、用户上下文透传、全局异常与统一分页；"
            "跨服务调用统一 Feign + fallback 降级；券状态流转、榜单落库、兑换码生成统一由 XXL-Job / 线程池调度；"
            "全链路 Docker 化部署。")

BENCH = ("压测验证",
         "用 JMeter 对领券接口做 50 / 100 / 200 三档并发压测：100 并发下 399 TPS、P99 273ms、零错误，"
         "200 并发时 P99 升至 1024ms，定位到线程池排队拐点；8000 次成功请求与 Redis 库存扣减、落库行数逐条对齐，"
         "未出现超发。")

BRIEF = [
    ("项目概述",
     "12 个业务微服务 + 网关/鉴权/公共模块（16 个 Maven 模块）组成的在线教育平台，覆盖课程、交易、支付、学习、"
     "考试、营销、搜索等域，负责学习域与营销域 6 个模块。"),
    ("核心工作",
     "独立搭建优惠券服务：Lua 原子校验 + Redisson 锁解决超发与重复领取，策略模式 + 排列组合 + 并行计算求解"
     "最优券组合（1s 超时兜底），10 位自校验兑换码免回表判重；积分榜单 Redis ZSet + 按月分表 + XXL-Job 落库；"
     "Bitmap 签到单人单月仅 4 Byte；互动问答 MQ 异步回写、批量查询消除 N+1。"),
    ("技术栈",
     "Spring Cloud Alibaba / MyBatis-Plus / Redis + Redisson / RabbitMQ / Elasticsearch / MongoDB / XXL-Job / Docker。"),
]

QUANT = [
    ("领券接口 TPS", "JMeter 50/100/200 并发，ramp 5s", "318 / 399 / 350"),
    ("领券接口 P99", "同上，断言 code=200", "148 / 273 / 1024 ms"),
    ("券超发 / 重复领取", "压测后核对 Redis 库存与 user_coupon 行数", "0（8000 次成功 = 库存 -8000 = 落库 8000）"),
    ("分页读接口 P99", "100 并发，纯 MySQL 查询", "540 ms（EXPLAIN 显示 filesort，可加索引优化）"),
    ("榜单查询复杂度", "Redis ZSet range / zrevrank", "O(logN)"),
    ("签到存储成本", "31 bit / 人 / 月", "4 Byte"),
    ("兑换码判重", "一次 Lua 往返，无 DB 查询", "1 次 RTT"),
]

TALK = (
    "天机学堂是一个在线教育平台，按业务域拆成了 12 个业务微服务，加上网关和公共模块一共 16 个 Maven 模块，"
    "我主要负责学习域和营销域。"
    "做得最完整的是优惠券：从建服务开始，把券模板、发放、领取、兑换码、到期提醒整条链路跑通。"
    "难点在并发领取和最优券组合——领取资格的四类校验收敛到一个 Lua 脚本里原子执行，再用 Redisson 锁兜底，保证不超发；"
    "下单时的最优券组合本质上是个组合爆炸问题，我用策略模式把 4 类券抽象出来，排列组合后用线程池并行算，"
    "1 秒超时兜底，按“优惠最大、用券最少”选出 TopN 给用户选。"
    "另外积分榜单做了按月分表加 Redis ZSet，签到用 Bitmap，一个人一个月只占 4 个字节。"
    "这段经历让我对高并发下的一致性、缓存和数据库怎么配合，有了比较实在的理解。"
)


def build():
    doc = Document()
    sec = doc.sections[0]
    sec.page_width, sec.page_height = Cm(21.0), Cm(29.7)
    sec.top_margin = Cm(1.5)
    sec.bottom_margin = Cm(1.5)
    sec.left_margin = Cm(1.8)
    sec.right_margin = Cm(1.8)

    normal = doc.styles["Normal"]
    normal.font.name = FONT
    normal.font.size = Pt(9)
    normal.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)

    # ---------------- 第一页：可直接粘贴的项目经历 ----------------
    p = para(doc, space_after=4)
    style_run(p.add_run("项目经历"), size=13, bold=True, color=ACCENT)
    add_bottom_border(p, size=12)

    project_header(
        doc,
        "天机学堂｜在线教育微服务平台",
        "2026.09 – 2026.11",
        "",
        "后端开发（学习域 / 营销域）｜Spring Cloud Alibaba · 16 个 Maven 模块 · 近 800 个 Java 类",
    )

    for label, body in FULL:
        bullet(doc, label, body)

    p = para(doc, space_before=5, space_after=3)
    style_run(p.add_run("• 核心工作："), size=9, bold=True, color=ACCENT)

    for i, (label, body) in enumerate(WORK, 1):
        numbered(doc, i, label, body)

    bullet(doc, *PRACTICE)
    bullet(doc, *BENCH)

    # ---------------- 第二页：精简版 + 口述稿 + 口径 ----------------
    doc.add_page_break()

    p = para(doc, space_after=4)
    style_run(p.add_run("附录 A｜精简版（3 行，适配一页纸简历）"), size=12, bold=True, color=ACCENT)
    add_bottom_border(p)
    plain(doc, "天机学堂｜在线教育微服务平台　　后端开发（学习域 / 营销域）　2026.09 – 2026.11", size=9, bold=True, space_after=4)
    for label, body in BRIEF:
        bullet(doc, label, body)

    p = para(doc, space_before=12, space_after=4)
    style_run(p.add_run("附录 B｜60 秒口述稿（自我介绍里引用）"), size=12, bold=True, color=ACCENT)
    add_bottom_border(p)
    plain(doc, TALK, size=9, indent=0.0, space_after=8)

    p = para(doc, space_before=6, space_after=4)
    style_run(p.add_run("附录 C｜量化口径（2026-11-23 实测）"), size=12, bold=True, color=ACCENT)
    add_bottom_border(p)

    table = doc.add_table(rows=1, cols=3)
    table.style = "Table Grid"
    table.autofit = True
    widths = (Cm(4.2), Cm(8.6), Cm(4.6))
    hdr = ("指标", "建议测量方式", "当前口径")
    for i, text in enumerate(hdr):
        cell = table.rows[0].cells[i]
        cell.width = widths[i]
        cp = cell.paragraphs[0]
        cp.paragraph_format.space_after = Pt(1)
        style_run(cp.add_run(text), size=8.5, bold=True)
    for row in QUANT:
        cells = table.add_row().cells
        for i, text in enumerate(row):
            cells[i].width = widths[i]
            cp = cells[i].paragraphs[0]
            cp.paragraph_format.space_after = Pt(1)
            style_run(cp.add_run(text), size=8.5)

    p = para(doc, space_before=12, space_after=4)
    style_run(p.add_run("附录 D｜投递 Agent / AI 应用岗时的定位建议"), size=12, bold=True, color=ACCENT)
    add_bottom_border(p)
    tips = [
        "这段经历证明的是“后端工程 + 高并发一致性”，不是 Agent 能力：主项目仍应是 Agent 项目，本段放第二位，控制在 5~6 行，重点写优惠券、榜单、签到三件事即可。",
        "可迁移的话术（真实成立）：策略模式 + 规则可组合 ≈ 工具/插件编排；1s 超时兜底 + Sentinel 降级 ≈ Agent 的工具调用超时与失败回退；Lua 原子校验 ≈ 多步操作的一致性保障。面试时按“同构问题”讲，不要包装成做过 Agent。",
        "若还有时间，真实可做的 AI 化改造：复用现成 ES 检索做课程/题库 RAG 问答，再把“查课、查订单、领券”封装成工具，交给 LLM 做工具调用的客服 Agent——这样这段项目才真正与岗位对口。",
    ]
    for i, t in enumerate(tips, 1):
        numbered(doc, i, "", t)

    doc.save(OUT)
    print("saved:", OUT)


if __name__ == "__main__":
    build()