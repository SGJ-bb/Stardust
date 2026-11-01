# -*- coding: utf-8 -*-
"""wm_keyword_check.py - 候选发货关键词与现有在售商品文本的冲突检查"""
import sqlite3
import collections

conn = sqlite3.connect('file:f:/stradust/xianyu-auto-reply-fix/data/xianyu_data.db?mode=ro', uri=True)
cur = conn.cursor()
cur.execute('SELECT item_title, IFNULL(item_detail, "") FROM item_info')
rows = cur.fetchall()
cands = ['Live2D', '半身皮', '女仆', '御姐', 'VTS', 'AI直出', '皮套', '表情', '六表情', '女仆御姐']
hits = collections.defaultdict(list)
for t, d in rows:
    text = (t or '') + ' ' + (d or '')
    for c in cands:
        if c in text:
            hits[c].append((t or '')[:34])
for c in cands:
    print('%s: %d hit(s)' % (c, len(hits[c])))
    for h in hits[c][:4]:
        print('   -', h)
print('total cached:', len(rows))
