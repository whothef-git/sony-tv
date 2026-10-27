# Third-party notices

This project's own code and docs are MIT licensed (see `LICENSE`). It builds on, adapts or bundles
the work below. Each part keeps its own licence.

## Inspiration and adapted code

### cleanyourtv (MIT)

<https://github.com/Gathin23/cleanyourtv> by Gathin. Its prompt-driven debloat-and-launcher workflow
(back up, disable in batches, build a small native launcher, take over HOME, verify with screenshots,
close ADB) is the starting point for `PROMPT.md` and the scripts in `tools/`. `PROMPT.md` and the
scripts here are written fresh; no text was copied.

```text
MIT License

Copyright (c) 2026 Gathin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Afnon (MIT)

<https://github.com/egyjs/afnon-android-tv-launcher> by egyjs and Afnon contributors. Adapted here:
the adhan-java setup with per-prayer minute offsets and the Umm al-Qura Hijri date with its month
names (`PrayerDay.java`, `PrayerPrefs.java`), the lapis and gold colour tokens (`Lapis.java`), the
star-lattice design language, and the India and Canada flag vectors (`res/drawable/flag_in.xml`,
`res/drawable/flag_ca.xml`, the latter re-cut to 3:2).

```text
MIT License

Copyright (c) 2026 egyjs and Afnon contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Libraries

### adhan-java 1.2.1 (MIT)

<https://github.com/batoulapps/adhan-java> by Batoul Apps. Prayer-time astronomy. Pulled in as a Gradle
dependency (`com.batoulapps.adhan:adhan:1.2.1`), not vendored.

```text
MIT License

Copyright (c) 2016 Batoul Apps

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

AndroidX RecyclerView and Palette are Apache License 2.0 (Gradle dependencies).

## Fonts (SIL Open Font License 1.1)

Bundled unmodified in `launcher/app/src/main/res/font/`. The full licence text and copyright line for
each is in `launcher/third_party/<font>/OFL.txt`. The fonts may be bundled and redistributed with
software but not sold on their own.

| Font | Files | Copyright |
|---|---|---|
| Cormorant Garamond | `cormorant_garamond.ttf`, `cormorant_garamond_italic.ttf` | Copyright 2015 the Cormorant Project Authors |
| Outfit | `outfit.ttf` | Copyright 2021 The Outfit Project Authors |
| Alexandria | `alexandria.ttf`, `alexandria_medium.ttf` | Copyright 2022 The Alexandria Project Authors |

## Data

### Open-Meteo (CC BY 4.0)

Weather is fetched at runtime from <https://open-meteo.com>. Nothing is bundled.
Weather data by [Open-Meteo.com](https://open-meteo.com/), licensed under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

### Qur'an and hadith quotations

`content/thoughts.json` contains short English quotations used as a thought of the day:

- Qur'an: the Sahih International translation, checked against quran.com's API
  (<https://api.quran.com>). Short excerpts, quoted with their surah:ayah reference.
- Hadith: short English renderings checked against <https://sunnah.com>, each with its collection and
  number; some are condensed and marked as such in `content/thoughts-verification.md`.

These are brief quotations for a non-commercial, educational display, with the source given for every
entry. The translations belong to their translators and publishers. If you are a rights holder and want
an entry changed or removed, open an issue.

## Trademarks

App names and logos visible in the screenshots (streaming apps in the dock) belong to their owners and
appear only because they were installed on the TV that was photographed. This project is not affiliated
with Google, Sony or any app vendor.
