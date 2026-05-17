#!/usr/bin/env python3
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/java/com/itsaky/androidide/templates/impl/androidstudio'

issues = []

for p in ROOT.rglob('*.kt'):
    txt = p.read_text(encoding='utf-8')
    rel = p.relative_to(ROOT)

    if p.name.endswith('Template.kt') or p.name.endswith('template.kt'):
        if 'widgets(' in txt and 'LanguageWidget()' not in txt and 'TemplateConstraint.Kotlin' not in txt:
            issues.append((str(rel), 'template_missing_language_widget'))

    if p.name.endswith('Recipe.kt'):
        if 'data.language' not in txt and 'Language.' not in txt:
            issues.append((str(rel), 'recipe_no_language_branch'))
        if 'build.gradle' in txt and ('useKts' not in txt):
            issues.append((str(rel), 'recipe_gradle_without_kts_branch'))
        if ('libs.versions.toml' in txt or 'version catalog' in txt.lower()) and ('useToml' not in txt):
            issues.append((str(rel), 'recipe_toml_without_switch'))

if not issues:
    print('OK: no audit issues found')
    raise SystemExit(0)

print('AndroidStudio template audit issues:')
for f, i in issues:
    print(f'- {i}: {f}')
raise SystemExit(1)
