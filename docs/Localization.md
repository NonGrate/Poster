# Localization

The template speaks English and Russian, end to end: app strings, web pages,
emails, the tag catalogue, store captions and demo content. Adding a third
language touches each of those; removing Russian is the same list backwards.

## Where the words are

| Layer | Files |
|---|---|
| App UI | `composeApp/src/commonMain/composeResources/values/strings.xml` (English, the default) and `values-ru/strings.xml`. Compose Multiplatform resources; keys must match; placeholders positional (`%1$s`). |
| Android notification | `composeApp/src/androidMain/res/values*/strings.xml` — the reminder's channel and text are drawn by the system, outside Compose. |
| The language model | `shared/.../model/Language.kt` — `ALL`, `DEFAULT`, `nameOf()`. Posts carry a `language`; accounts carry the languages they read; the feed is filtered by them server-side. |
| Tags | `server/.../model/CuratedTags.kt` (`english`, `russian` per tag), `shared/.../model/TagGroup.kt`, `Tag.label(language)`; the `Tag` table has `label_en`, `label_ru`. |
| Web pages | each `*Page.kt` in `server/` has an `English` and `Russian` copy object; `SiteLanguage.kt` picks by `?lang=` then `Accept-Language`. |
| Emails | `server/.../auth/AccountMail.kt`, `mail/GroupMail.kt` — copy per language, chosen by the recipient's account language. |
| Store | captions in `scripts/build-*-screenshots.sh`, taglines in `scripts/build-feature-graphic.sh` / `build-thumbnail.sh`, locales in `play_locale()` / `store_locale()`, demo content in `scripts/seed-demo-data.sh`. |
| Language picker in the app | `PostLanguageField`, `LanguagesField` (composeApp `ui/components`), shown only when `feature.multiLanguage=true`. |

`scripts/check-architecture.sh` fails when a string exists in one language and
not the other or when placeholders differ.

## Adding a language (say German, `de`)

1. `Language.kt`: add `GERMAN = "de"` to `ALL`, and its native name in `nameOf`.
2. `composeResources/values-de/strings.xml` — copy the English file and translate.
   Plurals need the language's quantity keys (`one`, `other`; Russian also has
   `few`/`many`).
3. `androidMain/res/values-de/strings.xml` — the four reminder strings.
4. Tags: add a `german` field to `CuratedTag` and `TagGroup`, a `label_de`
   column to `Tag.sq` (with a migration — [`Database.md`](Database.md)), and
   extend `Tag.label()`. Or keep tags bilingual and let `label()` fall back —
   it already does.
5. Server pages and mails: a `German` copy object per file, and `prefersRussian()`
   generalised to return a language rather than a boolean.
6. Store scripts: captions and content.

## Removing Russian

Delete `values-ru/`, `res/values-ru/`, the `russian` fields and `Russian*`
objects, and drop `RUSSIAN` from `Language.ALL`. With one language,
`feature.multiLanguage=false` hides the pickers; the column stays (`en`).

## Notes

- The app follows the device locale for UI text. Content language is a
  separate choice: what a person *reads* (profile) and what a post is *written
  in* (per post) — that is what lets one server serve a bilingual group.
- Anything typed in the app is stored as typed; nothing is machine-translated.
