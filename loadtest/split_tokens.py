import os, sys, csv

base = os.path.dirname(os.path.abspath(__file__))
src = os.path.join(base, "users.csv")
out = os.path.join(base, "tokens")
n = int(sys.argv[1]) if len(sys.argv) > 1 else 500

os.makedirs(out, exist_ok=True)
for f in os.listdir(out):
    if f.endswith(".csv"):
        os.remove(os.path.join(out, f))

with open(src, encoding="utf-8") as fh:
    rows = list(csv.reader(fh))[1:]

rows = [r for r in rows if len(r) == 2 and r[1]]
if len(rows) < n:
    raise SystemExit("not enough users: %d < %d" % (len(rows), n))

for i in range(1, n + 1):
    phone, token = rows[i - 1]
    with open(os.path.join(out, "t%d.csv" % i), "w", encoding="utf-8", newline="") as fh:
        fh.write(phone + "," + token + "\n")
print("wrote %d per-thread token files" % n)