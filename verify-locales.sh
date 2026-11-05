#!/usr/bin/env bash
# 校验三份语言资源的键集合是否一致
#
# 为什么需要这个脚本？
#   res/values/       中文（默认，也是中文文案的唯一来源）
#   res/values-zh/    中文（locale 限定副本）
#   res/values-en/    英文
#
# 三者必须键集合完全一致，缺一个键在某个语种下就会静默回退到另一语种，
# 表现为「某个界面突然变英文/变中文」。加上 values-zh/ 之后尤其容易漏改。
#
# 用法：./verify-locales.sh
# 退出码 0 = 通过，1 = 有差异

set -uo pipefail

cd "$(dirname "$0")"

ZH_DEFAULT="app/src/main/res/values/strings.xml"
ZH_LIMITED="app/src/main/res/values-zh/strings.xml"
EN="app/src/main/res/values-en/strings.xml"

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

status=0

echo "==> 校验语言资源键集合"
check_pair "$ZH_DEFAULT" "$ZH_LIMITED" "values/ (中文默认)" "values-zh/ (中文)" || status=1
check_pair "$ZH_DEFAULT" "$EN"        "values/ (中文默认)" "values-en/ (英文)" || status=1

echo ""
if [ "$status" -eq 0 ]; then
    echo "==> 全部通过"
else
    echo "==> 存在差异，请修正后重试"
fi

exit "$status"
