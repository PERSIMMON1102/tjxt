import re

p = r"D:\pojo\code\tianji\loadtest\receive.jmx"
t = open(p, encoding="utf-8").read()
t = t.replace(
    r"D:\pojo\code\tianji\loadtest\users.csv",
    r"D:\pojo\code\tianji\loadtest\tokens\t${__threadNum}.csv",
)
t = t.replace(
    '<boolProp name="ignoreFirstLine">true</boolProp>',
    '<boolProp name="ignoreFirstLine">false</boolProp>',
)
open(p, "w", encoding="utf-8", newline="").write(t)
print("chinese intact:", "天机学堂" in t)
print([l.strip() for l in t.splitlines() if "filename" in l or "ignoreFirstLine" in l])