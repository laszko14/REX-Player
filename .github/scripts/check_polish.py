#!/usr/bin/env python3
"""Validate Polish Android UI resources and selected hardcoded Compose text patterns."""
from collections import Counter
from pathlib import Path
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET

FORMAT = re.compile(r'%(?:(\d+)\$)?[-#+0,(<]*\d*(?:\.\d+)?([bBhHsScCdoxXeEfgGaAtT])')
TECHNOLOGIES = ('Lua', 'HUD', 'HDR', 'DRC', 'mpv', 'Vulkan', 'MediaCodec', 'Catppuccin', 'FFmpeg', 'OpenGL', 'SMB', 'FTP', 'WebDAV')
BAD_TERMS = re.compile(r'podwójny kran|podwójne stukanie|dwukrotny czas trwania wyszukiwania|catppucyna|sterowanie odtwarzaczem szkła|paska widokowego szkła|pokaż grę w nagłówku|double tap', re.I)
ENGLISH_UI = re.compile(r'\b(Current|Cancel|Close|Failed|Error|Settings|Enabled|Disabled|Default|Show|Hide|Reset|Delete|Play|Pause|None|Low|Medium|High|Loaded|Invalid|Unable|Unknown|Share|Remove|Select|Open|Save|Copy|Clear|Retry|Loading)\b')
UI_LITERAL = re.compile(r'(?:\bText\s*\(\s*(?:text\s*=\s*)?|\b(?:contentDescription|label|title|summary)\s*=\s*|\b(?:showToast|onShowToast)\s*\(\s*|Toast\.makeText\(\s*[^,\n]+,\s*)("(?:\\.|[^"\\\n])*")')


def placeholders(text):
    result, next_index = Counter(), 1
    for match in FORMAT.finditer(text.replace('%%', '')):
        index = int(match[1]) if match[1] else next_index
        if not match[1]:
            next_index += 1
        result[(index, match[2])] += 1
    return result


def resources(directory, errors):
    result = {}
    for file in sorted(directory.glob('*.xml')):
        try:
            root = ET.parse(file).getroot()
        except (ET.ParseError, UnicodeError) as error:
            errors.append(f'{file}: invalid XML/UTF-8: {error}')
            continue
        for node in root:
            if node.tag not in ('string', 'plurals', 'string-array'):
                continue
            key = node.get('name')
            if key in result:
                errors.append(f'{file}: duplicate resource {key}')
            result[key] = node
    return result


def text(node):
    return ''.join(node.itertext())


def validate(res_dir: Path, allowed_identical=None):
    allowed_identical = allowed_identical or {}
    errors = []
    base = resources(res_dir / 'values', errors)
    polish = resources(res_dir / 'values-pl', errors)
    for name, original in base.items():
        if original.get('translatable') == 'false':
            continue
        translated = polish.get(name)
        if translated is None:
            errors.append(f'{name}: missing Polish translation')
            continue
        if translated.tag != original.tag:
            errors.append(f'{name}: wrong resource type')
            continue
        if original.tag == 'plurals':
            source_items = {child.get('quantity'): child for child in original}
            target_items = {child.get('quantity'): child for child in translated}
            for quantity in ('one', 'few', 'many', 'other'):
                if quantity not in target_items:
                    errors.append(f'{name}: missing Polish plural {quantity}')
            pairs = [(name + '|' + quantity, source_items.get(quantity, source_items.get('other')), child)
                     for quantity, child in target_items.items()]
        elif original.tag == 'string-array':
            if len(original) != len(translated):
                errors.append(f'{name}: different array length')
            pairs = [(name + '|' + str(index), a, b) for index, (a, b) in enumerate(zip(original, translated))]
        else:
            pairs = [(name, original, translated)]
        for key, a, b in pairs:
            if a is None:
                errors.append(f'{key}: no source plural fallback')
                continue
            source, target = text(a), text(b)
            if not target.strip() or '\ufffd' in target or re.search('[\u00c3\u00c4\u00c5][\u0080-\u00bf]', target):
                errors.append(f'{key}: empty or damaged Unicode translation')
            if placeholders(source) != placeholders(target):
                errors.append(f'{key}: incompatible placeholders: {dict(placeholders(source))} != {dict(placeholders(target))}')
            if source.strip() == target.strip() and key not in allowed_identical:
                errors.append(f'{key}: untranslated text (or missing reviewed technical-name exemption): {target[:90]}')
            if BAD_TERMS.search(target):
                errors.append(f'{key}: inconsistent Polish terminology: {target[:90]}')
            if 'seek' in name.lower() and 'search' not in name.lower() and re.fullmatch(r'Szukaj[.!]?', target.strip(), re.I):
                errors.append(f'{key}: seek incorrectly translated as search')
            for technology in TECHNOLOGIES:
                if re.search(r'(?<!\w)' + re.escape(technology) + r'(?!\w)', source) and technology not in target:
                    errors.append(f'{key}: changed technology name {technology}')
            # Inline markup must preserve semantic tags, excluding Android styling attributes.
            if Counter(child.tag for child in a.iter() if child is not a) != Counter(child.tag for child in b.iter() if child is not b):
                errors.append(f'{key}: inline XML markup differs')
    return errors


def check_kotlin(source_dir):
    errors = []
    for file in sorted(source_dir.rglob('*.kt')):
        source = file.read_text(encoding='utf-8')
        for match in UI_LITERAL.finditer(source):
            literal = match[1]
            if ENGLISH_UI.search(literal):
                line = source.count('\n', 0, match.start()) + 1
                errors.append(f'{file}:{line}: hardcoded untranslated UI text {literal}')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--res', type=Path, default=Path('app/src/main/res'))
    parser.add_argument('--allowlist', type=Path, default=Path('.github/i18n/polish-technical-names.json'))
    parser.add_argument('--skip-kotlin', action='store_true')
    args = parser.parse_args()
    allowed = json.loads(args.allowlist.read_text()) if args.allowlist.exists() else {}
    errors = validate(args.res, allowed)
    if not args.skip_kotlin:
        errors.extend(check_kotlin(args.res.parent / 'kotlin'))
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        print(f'Polish localization: {len(errors)} errors', file=sys.stderr)
        return 1
    print('Polish localization: XML, completeness, placeholders, plurals, technology names, terminology and UI checks passed')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
