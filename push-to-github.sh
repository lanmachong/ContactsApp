#!/usr/bin/env bash
# 一键推送到 GitHub
#
# 前置条件：GitHub 上已存在 lanmachong/ContactsApp 仓库（空仓库即可，不要勾选
# "Add a README"，否则首次推送会因历史不一致被拒）。
#
# SSH 公钥已添加到账号并验证通过：
#   ssh -T git@github.com  ->  Hi lanmachong! You've successfully authenticated
# 本机代理阻断了 github.com 的 HTTPS，但 SSH 22 端口通，所以走 SSH 推送。

set -euo pipefail

cd "$(dirname "$0")"

REMOTE="git@github.com:lanmachong/ContactsApp.git"

echo "==> 检查远程仓库是否存在"
if ! git ls-remote --heads "$REMOTE" >/dev/null 2>&1; then
    echo "✗ 远程仓库 lanmachong/ContactsApp 不存在。"
    echo ""
    echo "  请先在浏览器打开下面这个链接创建仓库："
    echo "    https://github.com/new?name=ContactsApp&description=%E6%9C%AC%E5%9C%B0%E5%8A%A0%E5%AF%86%E9%80%9A%E8%AE%AF%E5%BD%95"
    echo ""
    echo "  创建时要点："
    echo "    · Repository name 填 ContactsApp"
    echo "    · Public（开源项目）"
    echo "    · 不要勾选 Add a README / .gitignore / license"
    echo ""
    echo "  建好之后重新运行本脚本即可。"
    exit 1
fi

echo "==> 推送 main 分支"
git push -u origin main

echo ""
echo "==> 完成"
echo "  仓库地址：https://github.com/lanmachong/ContactsApp"
echo "  当前提交："
git log --oneline -1
