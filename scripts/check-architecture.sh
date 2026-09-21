#!/usr/bin/env bash
# Layer rules that are cheaper to enforce than to re-litigate in review.
#
# Deliberately grep rather than a lint framework: three rules do not justify a
# dependency, and a rule nobody can read is a rule nobody keeps.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
failed=0

fail() { echo "✗ $1" >&2; failed=1; }
pass() { echo "✓ $1"; }

# 1. No ViewModel may depend on another ViewModel.
#
# Stage 1 removed the one case of this. It is invisible to tests when it comes
# back, because the compiler is perfectly happy with it — which is how it got
# there the first time.
vm_dir=composeApp/src/commonMain/kotlin/com/example/poster/viewmodel
offenders=$(grep -rn "^\s*\(private \)\?val \w\+: \w*ViewModel," "$vm_dir" 2>/dev/null || true)
if [ -n "$offenders" ]; then
  fail "a ViewModel takes another ViewModel as a constructor parameter:"
  echo "$offenders" >&2
else
  pass "no ViewModel depends on another ViewModel"
fi

# 2. shared/ must not reach up into the UI.
#
# shared is consumed by the server too, which has no Compose on its classpath.
up_refs=$(grep -rn "com\.example\.poster\.ui\." shared/src --include="*.kt" 2>/dev/null || true)
if [ -n "$up_refs" ]; then
  fail "shared/ references the UI layer:"
  echo "$up_refs" >&2
else
  pass "shared/ does not reference the UI layer"
fi

# 3. User-visible text belongs in string resources.
#
# Catches the obvious shape: a literal passed straight to Text(...).
# Catches Text("...") and text = "...". The first form was all this checked, and
# two hardcoded English error messages sat in TagInput the whole time.
#
# A template built from stringResource calls is not hardcoded text and passes —
# though gluing two translated words together assumes an English word order, so
# it is worth a second look when you see one.
literals=$(grep -rnE 'Text\(\s*"[^"]{2,}"|text = "[^"]{2,}"' composeApp/src/commonMain/kotlin --include="*.kt" 2>/dev/null | grep -v 'testTag' | grep -v 'stringResource' || true)
if [ -n "$literals" ]; then
  fail "hardcoded user-visible text:"
  echo "$literals" >&2
else
  pass "no hardcoded user-visible text"
fi

# 4. Placeholders in string resources are positional, and match across languages.
#
# Compose Resources substitutes %1$s and prints a bare %s literally. Ten strings
# shipped that way once and showed the raw token to the user, in both languages.
placeholder_report=$(python3 - <<'PYEOF'
import re, pathlib, sys

def strings(path):
    text = pathlib.Path(path).read_text(encoding="utf-8")
    return dict(re.findall(r'<string name="([^"]+)">(.*?)</string>', text, re.S))

en = strings("composeApp/src/commonMain/composeResources/values/strings.xml")
ru = strings("composeApp/src/commonMain/composeResources/values-ru/strings.xml")
positional = re.compile(r"%\d+\$[a-z]")
bare = re.compile(r"%(?![\d%])[a-z]")

problems = []
for language, table in (("en", en), ("ru", ru)):
    for name, value in table.items():
        if bare.search(value):
            problems.append(f"  {name} [{language}]: bare placeholder in {value!r}")

for name, value in en.items():
    if name not in ru:
        continue
    if sorted(positional.findall(value)) != sorted(positional.findall(ru[name])):
        problems.append(f"  {name}: takes different arguments in en and ru")

print("\n".join(problems))
PYEOF
)
if [ -n "$placeholder_report" ]; then
  fail "string resource placeholders:"
  echo "$placeholder_report" >&2
else
  pass "string placeholders are positional and consistent across languages"
fi

exit $failed
