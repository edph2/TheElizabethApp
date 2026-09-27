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

    def _package(self, argv):
        with mock.patch.object(sys, "argv", ["package_voice.py"] + argv), \
                mock.patch("getpass.getpass", return_value="correct horse"):
            package_voice.main()

    def _open(self, path):
        plain = io.BytesIO()
        with open(path, "rb") as f:
            elizbak.decrypt_stream(f, plain, "correct horse")
        return zipfile.ZipFile(plain)

    def _espeak(self, d):
        os.makedirs(os.path.join(d, "espeak-ng-data", "voices", "!v"))
        with open(os.path.join(d, "espeak-ng-data", "voices", "!v", "Mr serious"), "w") as f:
            f.write("x")
        with open(os.path.join(d, "espeak-ng-data", "en_dict"), "w") as f:
            f.write("y")
        return os.path.join(d, "espeak-ng-data")

    def test_package_trained_voice(self):
        import onnx
        from onnx import TensorProto, helper

        with tempfile.TemporaryDirectory() as d:
            data = os.path.join(d, "data")
            make_dataset(data)
            model, config, out = (os.path.join(d, n) for n in ["m.onnx", "m.onnx.json", "v.elizvoice"])
            graph = helper.make_graph([helper.make_node("Identity", ["x"], ["y"])], "g",
                                      [helper.make_tensor_value_info("x", TensorProto.FLOAT, [1])],
                                      [helper.make_tensor_value_info("y", TensorProto.FLOAT, [1])])
            onnx.save(helper.make_model(graph), model)
            with open(config, "w") as f:
                json.dump({"audio": {"sample_rate": 22050}, "espeak": {"voice": "en-gb-x-rp"}, "num_speakers": 1,
                           "language": {"name_english": "English"}, "phoneme_id_map": {"_": [0], "^": [1], " ": [3]}}, f)
            self._package(["--model", model, "--config", config, "--dataset", data, "--espeak-data", self._espeak(d),
                           "--base-model", "base", "--out", out])
            z = self._open(out)
            manifest = json.loads(z.read("voice/manifest.json"))
            self.assertEqual("own", manifest["kind"])
            self.assertEqual("Elizabeth", manifest["speakerName"])
            self.assertEqual(hashlib.sha256(z.read("voice/model.onnx")).hexdigest(), manifest["modelSha256"])
            self.assertEqual("_ 0\n^ 1\n  3\n", z.read("voice/tokens.txt").decode())
            meta = {p.key: p.value for p in onnx.load_from_string(z.read("voice/model.onnx")).metadata_props}
            self.assertEqual({"model_type": "vits", "comment": "piper", "language": "English", "voice": "en-gb-x-rp",
                              "has_espeak": "1", "n_speakers": "1", "sample_rate": "22050"}, meta)
            self.assertIn("voice/espeak-ng-data/voices/!v/Mr serious", z.namelist())

    def test_package_stock_voice(self):
        with tempfile.TemporaryDirectory() as d:
            sherpa = os.path.join(d, "vits-piper-en_GB-test-medium")
            os.makedirs(sherpa)
            self._espeak(sherpa)
            with open(os.path.join(sherpa, "en_GB-test-medium.onnx"), "wb") as f:
                f.write(b"model")
            with open(os.path.join(sherpa, "tokens.txt"), "w") as f:
                f.write("_ 0\n")
            out = os.path.join(d, "s.elizvoice")
            self._package(["--sherpa-dir", sherpa, "--licence", "CC-BY-4.0", "--out", out])
            manifest = json.loads(self._open(out).read("voice/manifest.json"))
            self.assertEqual(("stock", "CC-BY-4.0", "en_GB-test-medium"), (manifest["kind"], manifest["licence"], manifest["name"]))
            with self.assertRaises(SystemExit):
                self._package(["--sherpa-dir", sherpa, "--out", out])  # licence required


if __name__ == "__main__":
    unittest.main()
