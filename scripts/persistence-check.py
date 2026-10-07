#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""只比较本次TEST实例重启或独立恢复的业务与岗位，不用于生产资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import http.cookiejar
import json
import os
from pathlib import Path
import urllib.request

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--capture', action='store_true')
args = parser.parse_args()
state = json.loads((root / 'output/clubdesk-quality-state.json').read_text())
base = os.environ.get('TEST_URL', state['base'])
count = 0


def check(value, message):
    """不输出口令、会话或私有业务载荷。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    global count
    count += 1
    if not value:
        raise AssertionError(message)


class Reader:
    """各岗位独立Cookie，除认证外全部只读。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    def __init__(self, prefix):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        csrf = self.get('/api/auth/csrf')
        request = urllib.request.Request(base + '/api/auth/login', method='POST',
            headers={'Content-Type': 'application/json', csrf['header']: csrf['token']},
            data=json.dumps({'username': state[prefix + 'Username'], 'password': state[prefix + 'Password']}).encode())
        with self.opener.open(request, timeout=30) as response:
            check(response.status == 200, prefix + ' original account login')

    def get(self, path):
        with self.opener.open(base + path, timeout=30) as response:
            check(response.status == 200, 'read ' + path)
            return json.loads(response.read())


def normalize(value):
    """按主键和权限名规范集合排序，不修改业务字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    if isinstance(value, dict):
        return {k: normalize(v) for k, v in value.items()}
    if isinstance(value, list):
        values = [normalize(v) for v in value]
        if values and all(isinstance(v, dict) and 'id' in v for v in values):
            return sorted(values, key=lambda v: v['id'])
        if values and all(isinstance(v, str) for v in values):
            return sorted(values)
        return values
    return value


admin = Reader('admin')
check(admin.get('/actuator/health')['status'] == 'UP', 'healthy restored backend')
values = {}
workspace = admin.get('/api/workspace')
values['workspace'] = normalize(workspace)
for kind in ['users', 'roles', 'permissions', 'menus', 'departments', 'dictionaries', 'settings']:
    values['admin/' + kind] = normalize(admin.get('/api/admin/' + kind))
for row in workspace['passes']:
    path = '/api/passes/' + str(row['pass']['id'])
    values[path] = normalize(admin.get(path))
for row in workspace['sessions']:
    path = '/api/sessions/' + str(row['session']['id'])
    values[path] = normalize(admin.get(path))
for row in workspace['reservations']:
    path = '/api/reservations/' + str(row['id'])
    values[path] = normalize(admin.get(path))
for prefix, path in [('member', '/api/portal'), ('other', '/api/portal'), ('coach', '/api/coaching'),
                     ('reception', '/api/workspace'), ('outside', '/api/workspace')]:
    reader = Reader(prefix)
    values[prefix + '/me'] = normalize(reader.get('/api/auth/me'))
    values[prefix + path] = normalize(reader.get(path))
# 登录会追加审计，所以核对历史前缀而不是把正常新登录当作恢复差异。
audit = normalize(admin.get('/api/audit'))
snapshot = root / 'output/clubdesk-persistence-snapshot.json'
if args.capture:
    snapshot.write_text(json.dumps({'values': values, 'audit': audit}, ensure_ascii=False, indent=2))
    snapshot.chmod(0o600)
    print(f'PASS: {count} checks; {len(values)} business/role snapshots and {len(audit)} audit records captured privately')
else:
    previous = json.loads(snapshot.read_text())
    check(previous['values'].keys() == values.keys(), 'same restored response set')
    for key, value in previous['values'].items():
        check(value == values[key], 'exact persisted business/role response: ' + key)
    audit_by_id = {x['id']: x for x in audit}
    check(all(audit_by_id.get(x['id']) == x for x in previous['audit']), 'original audit history retained')
    print(f'PASS: {count} checks; {len(values)} exact responses, original accounts, credit/cash history and audit retained')
