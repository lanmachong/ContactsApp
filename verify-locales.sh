#!/usr/bin/env bash
# 校验三份语言资源
#
# 为什么需要这个脚本？
#   res/values/       中文（默认，也是中文文案的唯一来源）
#   res/values-zh/    中文（locale 限定副本）
#   res/values-en/    英文
#
# 三类检查：
#   1. 键集合必须完全一致 —— 缺一个键在某个语种下就会静默回退到另一语种，
#      表现为「某个界面突然变英文/变中文」。加上 values-zh/ 之后尤其容易漏改。
#   2. XML 结构必须合法 —— AAPT 对这些有硬要求，光看键集合发现不了：
#      重复的 XML 声明、注释里的方括号、未转义的引号，都会直接让构建失败。
#   3. 格式说明符必须一致 —— 同一条文案三个语种的 %1$s / %d 数量和类型
#      不一致时，AAPT 会在构建时报「格式说明符不匹配」直接失败。
#
# 用法：./verify-locales.sh
# 退出码 0 = 通过，1 = 有问题

set -uo pipefail

cd "$(dirname "$0")"

ZH_DEFAULT="app/src/main/res/values/strings.xml"
ZH_LIMITED="app/src/main/res/values-zh/strings.xml"
EN="app/src/main/res/values-en/strings.xml"

status=0

# ————————————————— 键集合检查 —————————————————

extract_keys() {
    # 提取所有 <string name="..."> / <plurals name="..."> 的 name 属性
    grep -oE '<(string|plurals) name="[^"]+"' "$1" \
        | sed -E 's/.*name="([^"]+)"/\1/' \
        | sort -u
}

check_pair() {
    local left_file="$1" right_file="$2" left_name="$3" right_name="$4"
    local left_keys right_keys missing extra

    left_keys=$(extract_keys "$left_file")
    right_keys=$(extract_keys "$right_file")

    # comm 需要已排序的输入
    missing=$(comm -23 <(echo "$left_keys") <(echo "$right_keys"))
    extra=$(comm -13 <(echo "$left_keys") <(echo "$right_keys"))

    if [ -n "$missing" ] || [ -n "$extra" ]; then
        echo "✗ $left_name 与 $right_name 键集合不一致"
        if [ -n "$missing" ]; then
            echo ""
            echo "  $right_name 缺少以下键："
            echo "$missing" | sed 's/^/    - /'
        fi
        if [ -n "$extra" ]; then
            echo ""
            echo "  $right_name 多出以下键（$left_name 里没有）："
            echo "$extra" | sed 's/^/    + /'
        fi
        return 1
    fi

    echo "✓ $left_name 与 $right_name 一致（$(echo "$left_keys" | wc -l | tr -d ' ') 个键）"
    return 0
}

echo "==> 校验语言资源键集合"
check_pair "$ZH_DEFAULT" "$ZH_LIMITED" "values/ (中文默认)" "values-zh/ (中文)" || status=1
check_pair "$ZH_DEFAULT" "$EN"        "values/ (中文默认)" "values-en/ (英文)" || status=1

# ————————————————— XML 结构检查 —————————————————

check_xml() {
    local file="$1" label="$2" issues=0

    # 1) XML 声明只能有一个 —— 同步文件时最容易犯的错
    local decl_count
    decl_count=$(grep -c '<?xml version' "$file")
    if [ "$decl_count" -gt 1 ]; then
        echo "✗ $label 有 $decl_count 个 XML 声明，只能有一个"
        issues=1
    fi

    # 2) 注释里不能出现方括号 —— AAPT 会当成格式说明符
    if grep '<!--' "$file" | grep -q '\[' ; then
        echo "✗ $label 的注释里出现了方括号，AAPT 会当成格式说明符"
        issues=1
    fi

    # 3) <resources> 与 </resources> 必须成对
    local open close
    open=$(grep -c '<resources>' "$file")
    close=$(grep -c '</resources>' "$file")
    if [ "$open" -ne "$close" ]; then
        echo "✗ $label 的 resources 标签不成对（$open / $close）"
        issues=1
    fi

    # 4) 字符串里未转义的引号 / & 号会让 AAPT 解析失败
    #    用 Python 精确解析，避免 shell 正则转义的坑。
    #    注意：&amp; 这类实体是「已经转义好的」，不能当成未转义。
    local bad
    bad=$(python - "$file" <<'PYEOF'
import re, sys

src = open(sys.argv[1], encoding="utf-8").read()

# 合法的 XML 实体，先换成占位符，剩下的 & 才是真正没转义的
ENTITIES = ("&amp;", "&lt;", "&gt;", "&quot;", "&apos;")

bad = []
for m in re.finditer(r"<string name=\"[^\"]*\">(.*?)</string>", src, re.S):
    raw = m.group(1)
    s = raw
    for e in ENTITIES:
        s = s.replace(e, "\x00")
    # AAPT 允许的反斜杠转义
    s = s.replace("\\'", "\x00").replace('\\"', "\x00")
    if "'" in s or '"' in s or "&" in s:
        bad.append(re.sub(r"\s+", " ", m.group(0))[:110])

for b in bad[:5]:
    print(b)
PYEOF
    )
    if [ -n "$bad" ]; then
        echo "✗ $label 有未转义的单引号、双引号或 & 号（需要写成 \\' 、\\\" 与 &amp;）："
        echo "$bad" | sed 's/^/    /'
        issues=1
    fi

    # 5) 真正的 XML 解析 —— 前面几项都是文本匹配，抓不到「孤儿文本」这类问题。
    #    典型翻车现场：删一个多行字符串时只删掉了开头那一行，
    #    正文和结尾的 </string> 留在原地，AAPT 就会报
    #    「元素类型 "resources" 必须由匹配的结束标记 </resources> 终止」。
    local parse_err
    parse_err=$(python - "$file" <<'PYEOF2'
import sys
import xml.etree.ElementTree as ET

try:
    ET.parse(sys.argv[1])
except ET.ParseError as e:
    print("第 %s 行：%s" % (e.position[0], e.msg))
PYEOF2
    )
    if [ -n "$parse_err" ]; then
        echo "✗ $label XML 解析失败："
        echo "$parse_err" | sed 's/^/    /'
        issues=1
    fi

    if [ "$issues" -eq 0 ]; then
        echo "✓ $label XML 结构正常"
        return 0
    fi
    return 1
}

echo ""
echo "==> 校验 XML 结构"
for f in "$ZH_DEFAULT" "$ZH_LIMITED" "$EN"; do
    check_xml "$f" "$(basename "$(dirname "$f")")/strings.xml" || status=1
done

# ————————————————— 格式说明符一致性检查 —————————————————

check_formats() {
    local left_file="$1" right_file="$2" left_name="$3" right_name="$4"
    local diffs
    diffs=$(python - "$left_file" "$right_file" <<'PYEOF'
import re, sys

# 抓出 %1$s / %d / %.2f 这类说明符：类型字母 + 可选的序号
SPEC = re.compile(r"%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?([a-zA-Z%])")

def specs(path):
    src = open(path, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r"<string name=\"([^\"]+)\">(.*?)</string>", src, re.S):
        found = []
        for s in SPEC.finditer(m.group(2)):
            idx = s.group(1)[:-1] if s.group(1) else ""
            found.append((idx, s.group(3)))
        out[m.group(1)] = found
    return out

a = specs(sys.argv[1])
b = specs(sys.argv[2])

for key in sorted(set(a) & set(b)):
    if a[key] != b[key]:
        print(f"{key}: {a[key]}  ≠  {b[key]}")
PYEOF
    )
    if [ -n "$diffs" ]; then
        echo "✗ $left_name 与 $right_name 的格式说明符不一致（AAPT 会报「格式说明符不匹配」）："
        echo "$diffs" | sed 's/^/    /'
        return 1
    fi
    echo "✓ $left_name 与 $right_name 的格式说明符一致"
    return 0
}

echo ""
echo "==> 校验格式说明符"
check_formats "$ZH_DEFAULT" "$ZH_LIMITED" "values/ (中文默认)" "values-zh/ (中文)" || status=1
check_formats "$ZH_DEFAULT" "$EN"        "values/ (中文默认)" "values-en/ (英文)" || status=1

echo ""
if [ "$status" -eq 0 ]; then
    echo "==> 全部通过"
else
    echo "==> 存在问题，请修正后重试"
fi

exit "$status"
