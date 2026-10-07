#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""核对中英文公开资料、原素材、署名、业务反馈和秘密模式；不替代人工验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
from pathlib import Path
import hashlib
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
errors = []


def verify(ok, message):
    """累积所有发布问题，不因一张图片缺失停止扫描。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    if not ok:
        errors.append(message)


paths = sorted(set(subprocess.check_output(
    ['git', 'ls-files', '--cached', '--others', '--exclude-standard'], cwd=root, text=True
).splitlines()))
tracked = set(subprocess.check_output(['git', 'ls-files'], cwd=root, text=True).splitlines())
image_sets = {}
for name in ['README.md', 'README.en.md']:
    document = (root / name).read_text()
    verify(document.startswith('[中文](README.md) | [English](README.en.md)'), name + ': language links')
    verify('https://www.zhuatech.cn/' in document, name + ': official website')
    images = [next(x for x in match if x) for match in re.findall(
        r'<img[^>]+src="([^"]+)"|!\[[^\]]*\]\(([^)]+)\)', document)]
    image_sets[name] = {x for x in images if x.startswith('docs/screenshots/')}
    verify(len(image_sets[name]) >= 6, name + ': six actual screen categories')
    for image in images:
        verify(not image.startswith(('/', 'http', 'file:')), name + ': relative image ' + image)
        verify((root / image).is_file(), name + ': missing image ' + image)
    for link in re.findall(r'\]\(([^)]+)\)', document):
        if link.startswith(('https:', 'http:', 'mailto:', '#')):
            continue
        verify((root / link.split('#')[0]).exists(), name + ': missing link ' + link)
    if name == 'README.md':
        for required in ['知华科技（上海如静知华信息科技有限公司）', '未经书面授权不得商用',
                         '商业授权或深度定制开发请联系知华科技', 'zhuatech2']:
            verify(required in document, name + ': required attribution/license/contact')
        verify(document.count('height="200"') == 2, 'Chinese QR aligned heights')
    else:
        for required in ['ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)',
                         'non-commercial', 'written authorization', 'mailto:han@zhuatech.cn',
                         'mailto:jack@zhuatech.cn', 'https://wa.me/8617521234993']:
            verify(required in document, name + ': required brand/license/contact')
        verify(not any('wechat' in x.lower() for x in images), 'English README must not embed WeChat QR')
verify(image_sets['README.md'] == image_sets['README.en.md'], 'Matching actual CN/EN screen references')
expected = {
    'wechat-zhuatech.png': 'a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1',
    'wechat-zhuatech2.png': '98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215',
}
for name, digest in expected.items():
    for directory in ['docs/images', 'frontend/public/brand']:
        path = root / directory / name
        verify(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == digest,
               'Original QR unchanged: ' + directory + '/' + name)
        verify(directory + '/' + name in tracked, 'Original QR tracked: ' + directory + '/' + name)
license_text = (root / 'LICENSE').read_text()
verify(all(x in license_text for x in ['上海如静知华信息科技有限公司', '非商业', '未经书面授权不得商用',
                                      'https://www.zhuatech.cn/', 'zhuatech2']), 'Original noncommercial LICENSE')
feedback = (root / 'frontend/src/errors.js').read_text()
for path in (root / 'backend/src/main/java').rglob('*.java'):
    for code in re.findall(r'new Problem\(\d+, "([A-Z_]+)"\)', path.read_text()):
        verify(re.search(r'\b' + code + r':\s*\[', feedback), 'Missing business feedback: ' + code)
patterns = [r'gh[pousr]_[A-Za-z0-9]{30,}', r'github_pat_[A-Za-z0-9_]{30,}',
            r'AKIA[0-9A-Z]{16}', r'-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----']
count = 0
for relative in paths:
    path = root / relative
    if not path.is_file():
        continue
    verify(not any(x in path.parts for x in ['output', 'private-backups', 'node_modules', 'target', 'dist']),
           'Private/generated directory in publish set: ' + relative)
    verify(path.name != '.env' and 'quality-state' not in path.name and path.suffix not in ['.sql.bak', '.dump'],
           'Private data in publish set: ' + relative)
    if path.suffix not in ['.java', '.vue', '.js', '.py', '.sql', '.md', '.yaml', '.yml', '.xml', '.conf', '.json'] \
            and path.name not in ['Dockerfile', 'LICENSE', '.env.example', '.gitignore', '.dockerignore']:
        continue
    value = path.read_text()
    count += 1
    if path.suffix in ['.java', '.vue', '.js', '.py', '.sql']:
        verify('zhuatech2' in value[:1200] and 'https://www.zhuatech.cn/' in value[:1200],
               'Own-source attribution: ' + relative)
    if path.name != 'release-check.py':
        for pattern in patterns:
            verify(not re.search(pattern, value), 'Secret pattern in ' + relative)
for line in (root / '.env.example').read_text().splitlines():
    if line.startswith(('MYSQL_ROOT_PASSWORD=', 'DATABASE_PASSWORD=', 'ADMIN_PASSWORD=')):
        verify(line.endswith('='), 'Example credential must be empty: ' + line.split('=')[0])
verify('skipTests' not in (root / 'backend/Dockerfile').read_text(), 'Docker Maven must run tests')
if errors:
    print(f'FAILED: {len(errors)} release checks; {count} text files scanned')
    for error in errors:
        print(' - ' + error)
    sys.exit(1)
print(f'PASS: {count} text files; synchronized READMEs, actual screens, original QR, contact, license and source attribution')
