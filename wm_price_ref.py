# -*- coding: utf-8 -*-
import sqlite3

conn = sqlite3.connect('file:f:/stradust/xianyu-auto-reply-fix/data/xianyu_data.db?mode=ro', uri=True)
cur = conn.cursor()
cur.execute("SELECT item_title, item_price, updated_at FROM item_info WHERE item_title LIKE '%鲸鱼娘%' OR item_title LIKE '%Live2D%' OR item_title LIKE '%OC%'")
for r in cur.fetchall():
    print(r)
