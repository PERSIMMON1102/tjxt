import re, subprocess

REPO = r"D:\pojo\code\tianji"
rel = "tj-pay/tj-pay-service/src/main/resources/bootstrap.yml"
path = REPO + "\\" + rel.replace("/", "\\")

orig = subprocess.run(["git", "-C", REPO, "show", "HEAD:" + rel],
                      capture_output=True, text=True, encoding="utf-8").stdout

def grab(key):
    m = re.search(key + r':\s*"([^"]+)"', orig)
    return m.group(1) if m else None

merchant = grab("merchantPrivateKey")
v3key = grab("apiV3Key")
assert merchant and v3key, (bool(merchant), bool(v3key))

rules = [
    (merchant, "${ALIPAY_MERCHANT_PRIVATE_KEY:}"),
    (v3key, "${WX_API_V3_KEY:}"),
]
open(REPO + r"\loadtest\replacements2.txt", "w", encoding="utf-8", newline="\n").write(
    "\n".join("literal:%s==>%s" % r for r in rules) + "\n")

t = open(path, encoding="utf-8").read()
for a, b in rules:
    t = t.replace(a, b)
open(path, "w", encoding="utf-8", newline="").write(t)
print("patched; merchant len=%d, v3key len=%d" % (len(merchant), len(v3key)))
print([l.strip()[:50] for l in t.splitlines() if "PrivateKey" in l or "apiV3Key" in l])