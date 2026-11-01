"""桌面端交叉验证：用同一份 KWS 模型 + 同一套关键词行，跑真实中文 TTS 音频。

作用：Android 端跑不动/没法连真机时，先在这台机器上证明
「唤醒词拼出来的 token 行是模型能吃的」「该命中的命中、不该命中的不误报」。
关键词行的拼法与 Kotlin 的 PinyinConverter 完全一致（同一份 pinyin.tsv + 同一套切分规则）。

用法：
    python kws_desktop_check.py
"""
import os
import sys
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.abspath(os.path.join(HERE, "..", "app", "src", "main", "assets"))
PINYIN_TSV = os.path.join(ASSETS, "kws", "pinyin.tsv")
MODEL = os.path.join(
    "F:/_sherpa/kws/assets",
    "sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01",
)
WAVS = os.path.join(HERE, "wav")

DEFAULT_WORDS = "星尘星尘,你好星尘"
THRESHOLD = 0.25
BOOST = 3.0

INITIALS3 = ("zh", "ch", "sh")
INITIALS1 = "bpmfdtnlgkhjqxrzcsyw"


def load_table():
    table = {}
    with open(PINYIN_TSV, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if len(line) >= 3 and line[1] == "\t":
                table[line[0]] = line[2:]
    return table


def split_syl(syl):
    low = syl.lower()
    if low[:2] in INITIALS3:
        return syl[:2], syl[2:]
    if low[:1] in INITIALS1:
        return syl[:1], syl[1:]
    return "", syl


def keyword_lines(table, words, threshold=THRESHOLD, boost=BOOST):
    out = []
    for word in words.replace("，", ",").replace("；", ",").replace("、", ",").split(","):
        word = word.strip()
        if not word:
            continue
        tokens = []
        skipped = []
        for ch in word:
            syl = table.get(ch)
            if not syl:
                skipped.append(ch)
                continue
            ini, fin = split_syl(syl)
            if ini:
                tokens.append(ini)
            if fin:
                tokens.append(fin)
        if not tokens:
            continue
        out.append("%s :%g #%g @%s" % (" ".join(tokens), boost, threshold, word))
        if skipped:
            print("  ! 跳过无法转拼音的字:", "".join(skipped))
    return out


def read_wav(path):
    with wave.open(path, "rb") as w:
        assert w.getsampwidth() == 2, "只支持 16bit PCM"
        sr, ch, n = w.getframerate(), w.getnchannels(), w.getnframes()
        raw = w.readframes(n)
    import array
    a = array.array("h")
    a.frombytes(raw)
    if ch > 1:
        a = a[::ch]
    return sr, [s / 32768.0 for s in a]


def main():
    import sherpa_onnx

    table = load_table()
    lines = keyword_lines(table, DEFAULT_WORDS)
    print("关键词行：")
    for ln in lines:
        print("   ", ln)
    if not lines:
        print("没有可用关键词")
        return 1

    kw_text = "\n".join(lines)
    kw_file = os.path.join(HERE, "_kws_keywords.txt")
    with open(kw_file, "w", encoding="utf-8", newline="\n") as f:
        f.write(kw_text + "\n")

    kws = sherpa_onnx.KeywordSpotter(
        tokens=os.path.join(MODEL, "tokens.txt"),
        encoder=os.path.join(MODEL, "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"),
        decoder=os.path.join(MODEL, "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"),
        joiner=os.path.join(MODEL, "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"),
        num_threads=2,
        max_active_paths=4,
        keywords_file=kw_file,
        keywords_score=1.0,
        keywords_threshold=THRESHOLD,
        num_trailing_blanks=2,
    )

    def detect(path):
        sr, samples = read_wav(path)
        try:
            s = kws.create_stream(keywords=kw_text)
        except TypeError:
            s = kws.create_stream()
            if hasattr(s, "accept_waveform"):
                pass
        s.accept_waveform(sr, samples) if sr == 16000 else s.accept_waveform(sr, samples)
        s.input_finished()
        hit = ""
        while kws.is_ready(s):
            kws.decode_stream(s)
            r = kws.get_result(s)
            if r:
                hit = r
                break
        return hit

    cases = [
        ("selftest_16k.wav", True, "星尘星尘"),
        ("hello_16k.wav", True, "你好星尘"),
        ("negative_16k.wav", False, "无关句子"),
    ]
    ok_all = True
    for name, expect, desc in cases:
        path = os.path.join(WAVS, name)
        if not os.path.exists(path):
            print(f"[skip] {name} 不存在")
            continue
        hit = detect(path)
        good = bool(hit) == expect
        ok_all &= good
        print(f"[{'PASS' if good else 'FAIL'}] {name} ({desc}) → 命中={hit or '无'}，期望={'命中' if expect else '不命中'}")

    print("\n结论：", "全部符合预期" if ok_all else "有不符预期的用例")
    return 0 if ok_all else 2


if __name__ == "__main__":
    sys.exit(main())
