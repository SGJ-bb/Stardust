"""用 Windows SAPI（Huihui 中文音色）合成唤醒词测试音频，输出 16kHz 单声道 16bit WAV。

用途：在模拟器/真机上验证 KWS 召回，不依赖手机麦克风。
产物目录：F:/stradust/android/tools/wav/
"""
import os
import struct
import subprocess
import sys
import wave

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wav")

PHRASES = {
    # 文件名: (要说的话, 期望是否命中)
    "selftest": ("星尘星尘。", True),
    "hello": ("你好星尘。", True),
    "negative": ("今天天气不错，我们去公园走走吧。", False),
}

# SAPI SpeechAudioFormatType: 12=16kHz8BitMono, 13=16kHz8BitStereo, 14=16kHz16BitMono
SAFT_16K_16BIT_MONO = 14

VBS = """On Error Resume Next
Set v = CreateObject("SAPI.SpVoice")
Set fs = CreateObject("SAPI.SpFileStream")
fs.Format.Type = {fmt}
fs.Open "{wav}", 3, False
Set v.AudioOutputStream = fs
For Each vv In v.GetVoices
  If InStr(1, vv.Id, "zh-CN", 1) > 0 Or InStr(1, vv.Id, "Huihui", 1) > 0 Then
    Set v.Voice = vv
    Exit For
  End If
Next
v.Speak "{text}"
fs.Close
"""


def synth(path, text):
    vbs = path + ".vbs"
    body = VBS.format(fmt=SAFT_16K_16BIT_MONO, wav=path.replace("/", "\\"), text=text)
    # cscript 认 UTF-16LE + BOM 的 .vbs
    with open(vbs, "w", encoding="utf-16") as f:
        f.write(body)
    r = subprocess.run(["cscript", "//nologo", vbs], capture_output=True)
    if r.returncode != 0:
        print("cscript failed:", r.stdout.decode("mbcs", "ignore"), r.stderr.decode("mbcs", "ignore"))
    os.remove(vbs)


def info(path):
    with wave.open(path, "rb") as w:
        return w.getframerate(), w.getnchannels(), w.getsampwidth(), w.getnframes()


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, (text, expect) in PHRASES.items():
        path = os.path.join(OUT_DIR, name + ".wav")
        if os.path.exists(path):
            os.remove(path)
        synth(path, text)
        if not os.path.exists(path):
            print(f"[FAIL] {name}: 没有生成")
            continue
        sr, ch, sw, n = info(path)
        print(f"[ok] {name}: {sr}Hz {ch}ch {sw*8}bit {n/sr:.2f}s  expect_hit={expect}  text={text}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
