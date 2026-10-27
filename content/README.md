# Thought of the day

`thoughts.json` feeds the small quote between the timeline and the dock. `tools/build-install.sh`
copies it into the app (`launcher/app/src/main/res/raw/thoughts.json`) on every build, so edit it here.
Turn the widget off with `thoughts_enabled` in `config.xml`.

## Schema

```json
{
  "weekday": [ { "text": "Finish what you started before taking on something new.", "source": null } ],
  "friday":  [ { "text": "...", "source": "Sahih al-Bukhari 935" } ]
}
```

- `text`: one sentence, ideally under 100 characters. Two lines fit on screen at the largest text
  size; longer text is cut with an ellipsis.
- `source`: shown in small type under the text, or `null` to show nothing.
- The pick is stable for the whole day (in the TV's time zone): `weekday[epochDay % n]` from Saturday
  to Thursday, and on Fridays `friday[(epochDay / 7) % n]`, so each Friday gets the next entry.
- An empty or missing `friday` list means Fridays use the weekday list too.

## Rules for `source`

`source` is a claim, so it is only set when the claim has been checked:

1. **Qur'an**: `"Qur'an 2:286"` style, and only when the English matches a recognised translation
   (the shipped list uses Sahih International).
2. **Hadith**: `"Sahih al-Bukhari 6464"` style, and only for Bukhari, Muslim, or another collection where
   the grade shown on sunnah.com is Sahih or Hasan. No weak (da'if) or fabricated narrations, and no
   "it is said that" sayings attributed to the Prophet ﷺ.
3. **Everything else** (your own reminders, proverbs, productivity notes): `"source": null`. Never invent
   a reference, and never attach a hadith number to a paraphrase you have not matched to the page.

## How to verify an entry

- Hadith: open `https://sunnah.com/<collection>:<number>` (for example `https://sunnah.com/bukhari:6464`),
  read the English, check the grade line, and make sure your shortened text keeps the meaning.
- Qur'an: `https://api.quran.com/api/v4/verses/by_key/2:286?translations=20` returns Sahih International
  (translation id 20); or read it on `https://quran.com/2/286`.
- Log what you checked in `thoughts-verification.md` (id, text, source, URL, verdict, note). The shipped
  list has 120 weekday and 52 Friday entries, all logged there.

## Swapping the list

The shipped Friday list is Islamic (Jumu'ah reminders) and the weekday list mixes hadith, Qur'an and
plain productivity notes. To use something else entirely (family sayings, quotes, another tradition),
replace both arrays and keep the schema. If you want no Friday special case, set `"friday": []`.
The tag above the text reads "Thought for today", or "Jumu'ah Mubarak" on Fridays when a Friday entry is
shown; change those strings in `MainActivity.renderThought()` if your list is not Islamic.

Asking Claude Code to do it works well: "Replace content/thoughts.json with 60 short weekday thoughts
about X and leave friday empty; source null for all of them."
