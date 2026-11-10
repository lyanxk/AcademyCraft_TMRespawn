"""Resolve bundled numeric parameters for the small production-method test adapters."""
from functools import lru_cache
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parent.parent
gameplay = root / 'src/main/resources/data/academy/gameplay'


@lru_cache(maxsize=None)
def document(path):
    return json.loads((gameplay / f'{path}.json').read_text(encoding='utf-8'))


def resolve_parameters(source):
    """Use default datapack values without changing the extracted method's control flow."""
    def literal(match):
        kind, name, key = match.groups()
        value = document(f'parameters/{name}')[key]
        suffix = {'i': '', 'l': 'L', 'f': 'f', 'd': 'd'}[kind]
        return f'({int(value) if kind in "il" else float(value)}{suffix})'

    return re.sub(r'Balance\.([ilfd])\("([^"]+)"\s*,\s*"([^"]+)"\)', literal, source)
