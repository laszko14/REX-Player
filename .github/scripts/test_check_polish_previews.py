import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('previews', Path(__file__).with_name('check_polish_previews.py'))
previews = importlib.util.module_from_spec(spec)
spec.loader.exec_module(previews)


class PolishPreviewChecks(unittest.TestCase):
    def test_each_requested_screen_requires_both_font_sizes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertTrue(previews.validate(root))
            for screen in previews.SCREENS:
                for scale in (100, 130):
                    (root / f'Polish{screen}SettingsScreenshot{scale}.png').write_bytes(
                        b'\x89PNG\r\n\x1a\n' + b'\x00\x00\x00\rIHDR' + struct.pack('>II', 360, 2600))
            self.assertFalse(previews.validate(root))
            next(root.iterdir()).write_bytes(b'not a PNG')
            self.assertTrue(previews.validate(root))
