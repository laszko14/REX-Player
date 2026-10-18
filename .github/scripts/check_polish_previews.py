#!/usr/bin/env python3
"""Require ten real Polish settings PNGs (five screens, normal and large font)."""
from pathlib import Path
import struct

SCREENS = ('Gesture', 'Appearance', 'Playback', 'Audio', 'LuaButton')
ROOT = Path('app/src/screenshotTestDebug/reference')


def validate(root=ROOT):
    errors, files = [], list(root.rglob('*.png'))
    for screen in SCREENS:
        matches = [file for file in files if 'Polish' + screen + 'SettingsScreenshot' in str(file)]
        if len(matches) < 2:
            errors.append(f'{screen}: expected normal/large-font Polish PNGs, found {len(matches)}')
        for file in matches:
            header = file.read_bytes()[:24]
            if len(header) != 24 or header[:8] != b'\x89PNG\r\n\x1a\n':
                errors.append(f'{file}: invalid PNG')
                continue
            width, height = struct.unpack('>II', header[16:24])
            if width < 320 or height < 1000:
                errors.append(f'{file}: unexpected settings viewport {width}x{height}')
    return errors


if __name__ == '__main__':
    errors = validate()
    if errors:
        raise SystemExit('\n'.join(errors))
    print('Polish settings: all five production screens rendered at both font scales')
