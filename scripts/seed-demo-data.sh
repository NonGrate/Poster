#!/usr/bin/env bash
# Seeds the backend with the demo content the store screenshots are taken from.
#
#   scripts/seed-demo-data.sh --host <host> [--reset] [--favorites-only]
#
# Every language it knows about is seeded in one pass. Which of them a reader
# sees is the app's own business: the feed only returns posts in a language
# the account says it reads, so one seeding serves every language's screenshots
# and the filter is exercised rather than assumed.
#
# Two accounts per language, because one cannot fill the screens on its own:
#
#   author  writes five posts        — this is what the feed shows
#   reader  writes two, likes two  — My Posts, and Favorites
#
# The reader is the account the screenshots sign in as. The feed hides your own
# posts, so the five belong to somebody else; the favorite button is hidden on
# your own, so the two liked have to be somebody else's as well.
#
# --reset wipes first. Screenshots of empty states need an empty database, and
# a rerun that added a second copy of everything would fill the feed with
# duplicates.
#
# --favorites-only re-applies just the hearts. The undo screenshot toggles one
# off, so a run captures light, breaks it, then captures dark against a
# Favorites screen one card short.
set -euo pipefail

HOST=${POSTER_HOST:-poster.example.com}
RESET=0
FAVORITES_ONLY=0
PRINT=
PRINT_LANG=
while [ $# -gt 0 ]; do
  case $1 in
    --host)           HOST=$2; shift ;;
    --reset)          RESET=1 ;;
    --favorites-only) FAVORITES_ONLY=1 ;;
    # One source of truth for these: the screenshot tool needs the reader's
    # sign-in and the title it opens for the details shot, and a second copy
    # over there goes stale the first time this content is edited.
    --detail-title)   PRINT=detail; PRINT_LANG=$2; shift ;;
    --detail-comment) PRINT=comment; PRINT_LANG=$2; shift ;;
    --reader-email)   PRINT=reader; PRINT_LANG=$2; shift ;;
    --password)       PRINT=password ;;
    # Accepted and ignored: every language is seeded now, and callers that
    # asked for one used to get only that one.
    --lang)           shift ;;
    *) echo "unknown flag: $1" >&2; exit 2 ;;
  esac
  shift
done

case $HOST in
  localhost*|127.0.0.1*|10.0.2.2*) SCHEME=http ;;
  *) SCHEME=https ;;
esac
BASE=$SCHEME://$HOST

# The languages with demo content. Adding one means adding its four functions
# below and its code here — nothing else in this file is language-specific.
LANGUAGES="en ru"

PASSWORD=demo123456

api() {  # api <token> <method> <path> [body]
  local token=$1 method=$2 path=$3 body=${4:-}
  if [ -n "$body" ]; then
    curl -sS -X "$method" "$BASE$path" -H 'Content-Type: application/json' \
      ${token:+-H "Authorization: Bearer $token"} -d "$body"
  else
    curl -sS -X "$method" "$BASE$path" ${token:+-H "Authorization: Bearer $token"}
  fi
}

login() {  # login <email> -> "<token> <userId>"
  api "" POST /auth/login "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}" |
    python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["tokens"]["accessToken"], d["user"]["guid"])'
}

register_or_login() {  # register_or_login <email> <name> <surname> -> "<token> <userId>"
  api "" POST /auth/register \
    "{\"name\":\"$2\",\"surname\":\"$3\",\"email\":\"$1\",\"password\":\"$PASSWORD\"}" >/dev/null 2>&1 || true
  # Writing a post needs a confirmed address and this script has no inbox.
  # Without it every post is refused and the screenshots come out of an empty
  # app — silently, because the posts fail one at a time.
  api "" POST /debug/fixtures/confirm "{\"email\":\"$1\"}" >/dev/null 2>&1 || true
  login "$1"
}

uuid() { python3 -c 'import uuid; print(uuid.uuid4())'; }

# Puts an account's reading languages where they belong. Without this every
# account reads English and a Russian pass gets an empty feed — the filter
# doing its job against content nobody told it about.
set_languages() {  # set_languages <token> <user-id> <lang>
  api "$1" POST /accounts "$(LANGUAGE="$3" python3 -c '
import json, os, sys
user = json.load(sys.stdin)
user["languages"] = [os.environ["LANGUAGE"]]
user["defaultLanguage"] = os.environ["LANGUAGE"]
print(json.dumps(user))
' <<< "$(api "$1" GET "/accounts/byId/$2")")" >/dev/null
}

# Dated backwards from now rather than all stamped the same second, so the
# relative times on the cards differ the way a real feed's do.
post_post() {  # post_post <token> <author> <group> <title> <message> <tags> <hours-ago> <lang> [state]
  # [state] is empty for an ordinary post, "private" for one only its author
  # sees, or "answered=<message>" for one marked answered — so the seeded feed
  # shows the group, private and answered labels the UI now has.
  api "$1" POST /posts "$(python3 -c '
import datetime, json, sys
guid, author, group, title, message, tags, hours, language, state = sys.argv[1:]
now = datetime.datetime.now()
date = (now - datetime.timedelta(hours=float(hours))).replace(microsecond=0).isoformat()
post = {"guid": guid, "title": title, "message": message, "author": author,
          "group": group or None, "likes": 0, "date": date,
          "tags": json.loads(tags), "language": language}
if state == "private":
    post["visibility"] = "private"
elif state == "group":
    # Restricted to its group, which is what makes the card show the
    # group pill (a public post that merely belongs to one does not).
    post["visibility"] = "group"
elif state.startswith("answered="):
    # Answered a little after it was posted, and recently, so the feed still
    # shows it with the badge rather than treating it as a stale answer.
    post["completedAt"] = (now - datetime.timedelta(hours=float(hours) / 4)).replace(microsecond=0).isoformat()
    post["completionMessage"] = state[len("answered="):]
print(json.dumps(post))
' "$(uuid)" "$2" "$3" "$4" "$5" "$6" "$7" "$8" "${9:-}")" >/dev/null
}

create_group() {  # create_group <token> <name> -> <id>
  api "$1" POST /groups/create "$(python3 -c '
import json, sys; print(json.dumps({"name": sys.argv[1]}))' "$2")" |
    python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])'
}

# --- the content, per language ---------------------------------------------
#
# Four functions per language, and the code in $LANGUAGES. They live in
# functions rather than inline command substitution because bash misparses a
# heredoc inside $( ) when the body holds an apostrophe, and "My sister's
# exams" is exactly that.
#
# Post fields: hours-ago|title|message|tags
# Tag ids are the curated ones from CuratedTags.kt and are never translated —
# the app localises those itself, and a key invented here becomes a free-text
# tag the picker does not know.

names_en() { echo "Maria|Whitfield|Daniel|Okoye"; }
groups_en() { echo "Family|Riverside Book Club|Thursday Neighbours"; }

author_posts_en() { cat <<'EOF'
30|Looking for a good vet nearby|Our dog needs a check-up and the old clinic closed. Anyone happy with theirs around Riverside?|["pets","recommendation"]
2|Found the missing keys|They were in the coat I had already checked twice. Thank you all for looking.|["lost_and_found","everyday"]|answered=Found. In my own coat pocket, naturally.
8|Quiet evening, chapter six|We meet Thursday at eight. Bring a question about the ending, or just bring yourself.|["books","meetup"]|group
20|Job hunt, week eight|Another rejection today. Posting so I keep applying and keep my head up.|["job_search","work"]
44|Exam week for my sister|She has studied all summer and doubts every bit of it. Tips for staying calm welcome.|["exams","studies"]
EOF
}

reader_posts_en() { cat <<'EOF'
5|First week in a new city|New job Monday, and I know nobody here. Where do people actually meet each other?|["moving","city"]
50|Selling the flat, third attempt|Two viewings fell through. Anyone been through this and lived to tell?|["home","finances"]
72|Ideas for a rainy weekend|Two kids, one flat, no plans. Note to self: buy board games before it rains.|["family","idea"]|private
EOF
}

# Comments under the post the detail screenshot opens (detail_title), in
# order: who|text. "reader" is the demo reader, "author" the post's author.
# comments_other_* puts one on a second post so a feed card shows a count too.
comments_en() { cat <<'EOF'
reader|So glad they turned up. Coat pockets are where everything goes to hide.
author|Third coat, first pocket. I should have started there.
reader|Happens to the best of us. Glad it ended well.
EOF
}
comments_other_en() { echo "Job hunt, week eight|reader|Week eight is still early. Keep sending them, one of them will say yes."; }

names_ru() { echo "Мария|Ветрова|Даниил|Орлов"; }
groups_ru() { echo "Семья|Книжный клуб у реки|Соседи по четвергам"; }

author_posts_ru() { cat <<'EOF'
30|Ищу хорошего ветеринара рядом|Собаке нужен осмотр, а старая клиника закрылась. Кто доволен своим врачом в районе?|["pets","recommendation"]
2|Ключи нашлись|Лежали в куртке, которую я проверял дважды. Спасибо всем, кто искал.|["lost_and_found","everyday"]|answered=Нашлись. В собственном кармане, конечно.
8|Тихий вечер, глава шестая|Встречаемся в четверг в восемь. Приносите вопрос о финале или просто приходите.|["books","meetup"]|group
20|Поиск работы, восьмая неделя|Сегодня снова отказ. Пишу, чтобы не бросать и не падать духом.|["job_search","work"]
44|У сестры экзамены|Она готовилась всё лето, а теперь боится, что ничего не помнит. Советы, как не нервничать, приветствуются.|["exams","studies"]
EOF
}

reader_posts_ru() { cat <<'EOF'
5|Первая неделя в новом городе|В понедельник новая работа, а я здесь никого не знаю. Где тут вообще знакомятся?|["moving","city"]
50|Продаём квартиру, третья попытка|Два просмотра сорвались. Кто через это проходил — расскажите.|["home","finances"]
72|Идеи на дождливые выходные|Двое детей, одна квартира, никаких планов. Заметка себе: купить настольные игры до дождя.|["family","idea"]|private
EOF
}

# --- which posts the reader likes ------------------------------------
#
# The second and the fourth of the author's five. Two rather than one so
# Favorites is not a one-card screen, and not the first so it is visibly a
# choice rather than everything.
FAVORITE_POSITIONS="2 4"

comments_ru() { cat <<'EOF'
reader|Как хорошо, что нашлись. В карманах курток пропадает всё на свете.
author|Третья куртка, первый карман. С неё и надо было начинать.
reader|Бывает с каждым. Главное, что всё закончилось хорошо.
EOF
}
comments_other_ru() { echo "Поиск работы, восьмая неделя|reader|Восемь недель — это ещё рано. Продолжайте, одно из них ответит да."; }

author_email() { echo "demo.author.$1@example.com"; }
reader_email() { echo "demo.reader.$1@example.com"; }

# The title the screenshot tool opens to photograph a post's details. The
# reader has not liked this one, so the details screen shows an offer
# rather than an already-filled heart.
# The post the screenshot scripts open: the first one every demo reader can see
# (a group or private post may not be in the reader's feed at all).
# The newest of the author's ordinary posts — top of the feed, so the first card
# on every platform is this one.
detail_title() { "author_posts_$1" | grep -v -E '\|(group|private)$' | sort -t'|' -k1,1n | sed -n '1p' | cut -d'|' -f2; }

apply_favorites() {  # apply_favorites <lang>
  local language=$1 token reader_id author_id position guid
  read -r token reader_id < <(login "$(reader_email "$language")")
  read -r _ author_id < <(login "$(author_email "$language")")

  # Cleared first: the undo screenshot toggles one off and reruns would
  # otherwise accumulate whatever an earlier pass happened to leave.
  api "$token" GET /favorites/me | python3 -c '
import json, sys
for post in json.load(sys.stdin): print(post["guid"])' |
    while read -r guid; do
      [ -n "$guid" ] && api "$token" DELETE "/favorites/$reader_id/$guid" >/dev/null
    done

  for position in $FAVORITE_POSITIONS; do
    guid=$(api "$token" GET /posts | AUTHOR="$author_id" POSITION="$position" \
      TITLE="$("author_posts_$language" | sed -n "${position}p" | cut -d'|' -f2)" python3 -c '
import json, os, sys
title = os.environ["TITLE"]
match = [p for p in json.load(sys.stdin)
         if p["author"] == os.environ["AUTHOR"] and p["title"] == title]
print(match[0]["guid"] if match else "")')
    [ -n "$guid" ] && api "$token" POST "/favorites/$reader_id/$guid" >/dev/null
  done
}

post_guid() {  # post_guid <token> <author-id> <title> -> guid of that author's post with that title
  api "$1" GET /posts | AUTHOR="$2" TITLE="$3" python3 -c '
import json, os, sys
match = [p for p in json.load(sys.stdin)
         if p["author"] == os.environ["AUTHOR"] and p["title"] == os.environ["TITLE"]]
print(match[0]["guid"] if match else "")'
}

apply_comments() {  # apply_comments <lang>
  local language=$1 author_token author_id reader_token reader_id guid who text title
  read -r author_token author_id < <(login "$(author_email "$language")")
  read -r reader_token reader_id < <(login "$(reader_email "$language")")

  comment() {  # comment <post-guid> <reader|author> <text>
    local token=$reader_token
    [ "$2" = author ] && token=$author_token
    api "$token" POST "/posts/$1/comments" "$(python3 -c 'import json,sys; print(json.dumps({"text": sys.argv[1]}))' "$3")" >/dev/null
  }
  # Cleared first, like the favorites: a rerun without --reset would otherwise
  # stack the same conversation twice.
  clear_comments() {  # clear_comments <post-guid>
    api "$reader_token" GET "/posts/$1/comments" | python3 -c '
import json, sys
for c in json.load(sys.stdin): print(c["author"] + " " + c["guid"])' |
      while read -r owner cid; do
        [ -z "$cid" ] && continue
        [ "$owner" = "$reader_id" ] && api "$reader_token" DELETE "/posts/$1/comments/$cid" >/dev/null
        [ "$owner" = "$author_id" ] && api "$author_token" DELETE "/posts/$1/comments/$cid" >/dev/null
      done
  }

  guid=$(post_guid "$reader_token" "$author_id" "$(detail_title "$language")")
  if [ -n "$guid" ]; then
    clear_comments "$guid"
    while IFS='|' read -r who text; do
      [ -n "${who:-}" ] && comment "$guid" "$who" "$text"
    done < <("comments_$language")
  fi
  IFS='|' read -r title who text < <("comments_other_$language")
  guid=$(post_guid "$reader_token" "$author_id" "$title")
  if [ -n "$guid" ]; then
    clear_comments "$guid"
    comment "$guid" "$who" "$text"
  fi
}

seed_language() {  # seed_language <lang>
  local language=$1
  local author_name author_surname reader_name reader_surname
  IFS='|' read -r author_name author_surname reader_name reader_surname < <("names_$language")
  local family church neighbours
  IFS='|' read -r family church neighbours < <("groups_$language")

  local author_token author_id reader_token reader_id
  read -r author_token author_id < <(register_or_login "$(author_email "$language")" "$author_name" "$author_surname")
  read -r reader_token reader_id < <(register_or_login "$(reader_email "$language")" "$reader_name" "$reader_surname")

  # Set before anything is written, so a post's language and its author's
  # agree from the start.
  set_languages "$author_token" "$author_id" "$language"
  set_languages "$reader_token" "$reader_id" "$language"

  # The reader's own group, plus two they were invited into: the
  # Groups screen with a single row says nothing about what the screen is
  # for.
  local family_id church_id neighbours_id
  family_id=$(create_group "$reader_token" "$family")
  church_id=$(create_group "$author_token" "$church")
  neighbours_id=$(create_group "$author_token" "$neighbours")
  api "$reader_token" POST /groups/join "{\"groupId\":\"$church_id\"}" >/dev/null
  api "$reader_token" POST /groups/join "{\"groupId\":\"$neighbours_id\"}" >/dev/null
  api "$author_token" POST /groups/join "{\"groupId\":\"$family_id\"}" >/dev/null

  # The author's, which are what the reader's feed shows. One is marked answered
  # (state field), so the feed photographs the Answered badge.
  while IFS='|' read -r hours title message tags state; do
    [ -z "${hours:-}" ] && continue
    post_post "$author_token" "$author_id" "$church_id" "$title" "$message" "$tags" "$hours" "$language" "$state"
  done < <("author_posts_$language")

  # The reader's, in the group they own — My Posts hides a post whose
  # group you are not in, and this is the one they certainly are. One is
  # private, so My Posts photographs the private label.
  while IFS='|' read -r hours title message tags state; do
    [ -z "${hours:-}" ] && continue
    post_post "$reader_token" "$reader_id" "$family_id" "$title" "$message" "$tags" "$hours" "$language" "$state"
  done < <("reader_posts_$language")

  # And one of the reader's liked, so the count on a card is not always
  # zero when somebody looks at their own.
  local mine
  mine=$(api "$author_token" GET /posts | READER="$reader_id" python3 -c '
import json, os, sys
theirs = [p for p in json.load(sys.stdin) if p["author"] == os.environ["READER"]]
print(theirs[0]["guid"] if theirs else "")')
  [ -n "$mine" ] && api "$author_token" POST "/favorites/$author_id/$mine" >/dev/null

  apply_favorites "$language"

  # A roster of people who liked each of the author's posts, so the detail
  # screen has a list to show. The fixture seeds named + anonymous friends with
  # backdated times; here it is pointed at every author post the feed shows.
  api "$reader_token" GET /posts | AUTHOR="$author_id" python3 -c '
import json, os, sys
for p in json.load(sys.stdin):
    if p["author"] == os.environ["AUTHOR"]:
        print(p["guid"])' |
    while read -r guid; do
      [ -n "$guid" ] && api "$reader_token" POST /debug/fixtures/liking "{\"postId\":\"$guid\"}" >/dev/null
    done

  apply_comments "$language"

  echo "  $language: 2 accounts, 3 groups, 8 posts (1 answered, 1 private), 2 favorites, 4 comments"
}

# ---- run ------------------------------------------------------------------

case ${PRINT:-} in
  detail)   detail_title "$PRINT_LANG"; exit 0 ;;
  comment)  "comments_$PRINT_LANG" | sed -n '1p' | cut -d'|' -f2; exit 0 ;;
  reader)   reader_email "$PRINT_LANG"; exit 0 ;;
  password) echo "$PASSWORD"; exit 0 ;;
esac

echo "backend: $BASE"

if [ $FAVORITES_ONLY -eq 1 ]; then
  for language in $LANGUAGES; do apply_favorites "$language"; done
  echo "favorites restored"
  exit 0
fi

if [ $RESET -eq 1 ]; then
  api "" POST /debug/fixtures/integration >/dev/null
  echo "reset backend"
fi

for language in $LANGUAGES; do seed_language "$language"; done
echo "seeded: $(echo $LANGUAGES | wc -w | tr -d ' ') languages"
