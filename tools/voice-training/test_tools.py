"""Tests for the training tools. Run: python3 -m unittest discover tools/voice-training"""
import hashlib
import io
import json
import os
import struct
import sys
import tempfile
import unittest
import wave
import zipfile
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import elizbak  # noqa: E402
import package_voice  # noqa: E402
import verify_dataset  # noqa: E402


def make_dataset(root: str, consent=True, corrupt=False) -> None:
    os.makedirs(os.path.join(root, "wavs"))
    files, lines = {}, []
    for i in range(3):
        name = f"utt{i + 1:05d}"
        path = os.path.join(root, "wavs", name + ".wav")
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(22050)
            w.writeframes(struct.pack("<h", 1000) * 22050)
        with open(path, "rb") as f:
            files[f"wavs/{name}.wav"] = hashlib.sha256(f.read()).hexdigest()
        lines.append(f"{name}|Sentence {i}.|Sentence {i}.")
    if corrupt:
        files["wavs/utt00001.wav"] = "0" * 64
    with open(os.path.join(root, "metadata.csv"), "w") as f:
        f.write("\n".join(lines) + "\n")
    manifest = {"speakerName": "Elizabeth", "sampleRate": 22050, "files": files, "utterances": 3, "totalSeconds": 3,
                "consent": {"speakerName": "Elizabeth", "isSelf": True, "statement": "I agree", "timeMillis": 1} if consent else None}
    with open(os.path.join(root, "manifest.json"), "w") as f:
        json.dump(manifest, f)


class ToolsTest(unittest.TestCase):
    def test_encrypt_decrypt_round_trip(self):
        for size in [0, 10, elizbak.CHUNK, elizbak.CHUNK * 2 + 5]:
            plain = os.urandom(size)
            sealed = io.BytesIO()
            elizbak.encrypt_stream(io.BytesIO(plain), sealed, "correct horse")
            out = io.BytesIO()
            elizbak.decrypt_stream(io.BytesIO(sealed.getvalue()), out, "correct horse")
            self.assertEqual(plain, out.getvalue())
            with self.assertRaises(elizbak.ExportError):
                elizbak.decrypt_stream(io.BytesIO(sealed.getvalue()[:-1]), io.BytesIO(), "correct horse")
            with self.assertRaises(elizbak.ExportError):
                elizbak.decrypt_stream(io.BytesIO(sealed.getvalue()), io.BytesIO(), "wrong horse")

    def test_verify_dataset(self):
        with tempfile.TemporaryDirectory() as d:
            make_dataset(d)
            self.assertEqual([], verify_dataset.verify(d))
        with tempfile.TemporaryDirectory() as d:
            make_dataset(d, consent=False, corrupt=True)
            problems = verify_dataset.verify(d)
            self.assertTrue(any("consent" in p for p in problems))
            self.assertTrue(any("checksum" in p for p in problems))

    def test_package_voice(self):
        with tempfile.TemporaryDirectory() as d:
            data = os.path.join(d, "data")
            make_dataset(data)
            model, config, out = (os.path.join(d, n) for n in ["m.onnx", "m.onnx.json", "v.elizvoice"])
            with open(model, "wb") as f:
                f.write(b"fake onnx")
            with open(config, "w") as f:
                json.dump({"audio": {"sample_rate": 22050}}, f)
            argv = ["package_voice.py", "--model", model, "--config", config, "--dataset", data,
                    "--base-model", "base", "--out", out]
            with mock.patch.object(sys, "argv", argv), mock.patch("getpass.getpass", return_value="correct horse"):
                package_voice.main()
            plain = io.BytesIO()
            with open(out, "rb") as f:
                elizbak.decrypt_stream(f, plain, "correct horse")
            z = zipfile.ZipFile(plain)
            manifest = json.loads(z.read("voice/manifest.json"))
            self.assertEqual(hashlib.sha256(b"fake onnx").hexdigest(), manifest["modelSha256"])
            self.assertEqual("Elizabeth", manifest["speakerName"])
            self.assertEqual(b"fake onnx", z.read("voice/model.onnx"))


if __name__ == "__main__":
    unittest.main()
