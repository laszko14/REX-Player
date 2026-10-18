import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('check_polish', Path(__file__).with_name('check_polish.py'))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class PolishResourceChecks(unittest.TestCase):
    def check(self, base, polish, allowed=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for folder, text in [('values', base), ('values-pl', polish)]:
                (root / folder).mkdir()
                (root / folder / 'strings.xml').write_text(text, encoding='utf-8')
            return checker.validate(root, allowed or {})

    def test_missing_translation(self):
        self.assertTrue(self.check('<resources><string name="play">Play</string></resources>', '<resources/>'))

    def test_placeholder_indices_and_types_are_checked(self):
        base = '<resources><string name="x">%1$s: %2$d%%</string></resources>'
        self.assertTrue(self.check(base, '<resources><string name="x">%1$d: %2$s%%</string></resources>'))
        self.assertFalse(self.check(base, '<resources><string name="x">%2$d%% — %1$s</string></resources>'))

    def test_malformed_xml(self):
        self.assertTrue(self.check('<resources/>', '<resources><string>'))

    def test_untranslated_sentence(self):
        text = '<resources><string name="play">Show player controls</string></resources>'
        self.assertTrue(self.check(text, text))

    def test_brands_and_technical_names_can_be_identical(self):
        text = '<resources><string name="engine">Vulkan</string></resources>'
        self.assertFalse(self.check(text, text, {'engine': 'Technology name'}))

    def test_translated_brand_and_bad_gesture_terminology(self):
        self.assertTrue(self.check('<resources><string name="theme">Catppuccin</string></resources>',
            '<resources><string name="theme">Catppucyna</string></resources>'))
        self.assertTrue(self.check('<resources><string name="gesture">Double tap</string></resources>',
            '<resources><string name="gesture">Podwójny kran</string></resources>'))

    def test_polish_plural_categories_and_each_placeholder(self):
        base = '<resources><plurals name="files"><item quantity="one">%d file</item><item quantity="other">%d files</item></plurals></resources>'
        incomplete = '<resources><plurals name="files"><item quantity="one">%d plik</item><item quantity="other">%d plików</item></plurals></resources>'
        self.assertTrue(self.check(base, incomplete))
        complete = '<resources><plurals name="files">' + ''.join(
            '<item quantity="%s">%%d %s</item>' % (quantity, word)
            for quantity, word in [('one', 'plik'), ('few', 'pliki'), ('many', 'plików'), ('other', 'pliku')]) + '</plurals></resources>'
        self.assertFalse(self.check(base, complete))

    def test_duplicate_names_and_invalid_unicode(self):
        self.assertTrue(self.check('<resources><string name="a">One</string></resources>',
            '<resources><string name="a">Jeden</string><string name="a">Drugi</string></resources>'))
        self.assertTrue(self.check('<resources><string name="a">One</string></resources>',
            '<resources><string name="a">Jed�en</string></resources>'))

    def test_nontranslatable_entries_do_not_require_polish(self):
        self.assertFalse(self.check('<resources><string name="brand" translatable="false">MPV Alpha</string></resources>', '<resources/>'))

    def test_meaning_specific_search_and_seek(self):
        self.assertFalse(self.check('<resources><string name="search">Search</string></resources>',
            '<resources><string name="search">Szukaj</string></resources>'))
        self.assertTrue(self.check('<resources><string name="pref_seek">Seek</string></resources>',
            '<resources><string name="pref_seek">Szukaj</string></resources>'))

    def test_hardcoded_current_is_rejected_but_technical_names_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'Screen.kt'
            source.write_text('Text("Current: 10%")')
            self.assertTrue(checker.check_kotlin(root))
            source.write_text('Text("Vulkan"); Text(stringResource(R.string.current))')
            self.assertFalse(checker.check_kotlin(root))



if __name__ == '__main__':
    unittest.main()
