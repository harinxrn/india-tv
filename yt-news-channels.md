# IndiaTV v2 — YT News channels (verified 2026-10-23)

Verified via YouTube oEmbed (author_name / author_url) from the exact links Hari sent 2026-10-23 08:44 IST. Store ONLY the handle — the app resolves the current /live video ID fresh on launch/tap (self-healing design). Do NOT store the video IDs permanently; they are today's live IDs only.

| # | Channel (app tile) | Handle to store | /live URL the app will use | Video ID Hari sent (2026-10-23) | Notes |
|---|---|---|---|---|---|
| 1 | Asianet News | @asianetnews | https://www.youtube.com/@asianetnews/live | x1bfKawndzs | |
| 2 | Manorama News | @manoramanews | https://www.youtube.com/@manoramanews/live | tgBTspqA5nY | |
| 3 | Mathrubhumi News | @TVMathrubhumi | https://www.youtube.com/@TVMathrubhumi/live | RbxEftGN584 | Corrects old guess @MathrubhumiNews (404) |
| 4 | 24 News | @24OnLive | https://www.youtube.com/@24OnLive/live | 1wECsnGZcfc | Corrects old default @24NewsChannel |
| 5 | Reporter TV | @reporterlive | https://www.youtube.com/@reporterlive/live | nObUcHKZEGY | |
| 6 | Media One | @MediaoneTVLive | https://www.youtube.com/@MediaoneTVLive/live | -8d8-c0yvyU | Corrects old default @MediaOneNews |
| 7 | News18 Kerala | @News18Kerala | https://www.youtube.com/@News18Kerala/live | 7Y-MMzEcjeA | |
| 8 | Kairali News | @kairalinews | https://www.youtube.com/@kairalinews/live | yiiqRHY1Bl8 | |
| 9 | Janam TV | @tvjanam | https://www.youtube.com/@tvjanam/live | T9Ol2JHqKlM | Corrects old default @JanamTV |
| 10 | Big TV 24x7 | @bigtv24x7live | https://www.youtube.com/@bigtv24x7live/live | 72ngeza9aKA | Sent as 10th link; was #13 in the curated list |
| 11 | Jaihind TV | @JaihindNewslive | https://www.youtube.com/@JaihindNewslive/live | (found by Muse, not sent) live on 2026-10-23 as 87VtGXHNV3k, source video yz02No5643A | Handle from Jaihind's own channel description; /live verified 200 + canonical video ID 2026-10-23 |

## Missing / IPTV-only for now
- Raj News Malayalam — no YouTube live channel found 2026-10-23 (guessed handles 404, search found no live channel). Keep as IPTV-only tile in TV section unless Hari supplies a link.
- Real News Kerala — same: no YouTube live channel found (guessed handles 404). IPTV-only unless Hari supplies a link.

## Build rule
DEFAULT_YT in v2 must use the handles in this file, keyed by tile base name (lowercase). Any user-supplied watch/live link must be normalized to its channel handle via oEmbed / canonical on first use.
