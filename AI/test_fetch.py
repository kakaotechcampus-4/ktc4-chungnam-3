import os, re, requests
from dotenv import load_dotenv

load_dotenv()
API_KEY = os.getenv("YOUTUBE_API_KEY")

PATTERNS = [
    r'youtube\.com/shorts/([\w-]{11})',
    r'youtu\.be/([\w-]{11})',
    r'youtube\.com/watch\?v=([\w-]{11})',
]

def parse_video_id(url):
    for p in PATTERNS:
        m = re.search(p, url)
        if m:
            return m.group(1)
    return None

def fetch(video_id):
    r = requests.get(
        "https://www.googleapis.com/youtube/v3/videos",
        params={"part": "snippet", "id": video_id, "key": API_KEY}
    )
    items = r.json().get("items", [])
    if not items:
        return None
    s = items[0]["snippet"]
    return {
        "title": s["title"],
        "description": s["description"],
        "channel": s["channelTitle"],
    }

with open("urls.txt") as f:
    URLS = [line.strip() for line in f if line.strip()]

for url in URLS:
    vid = parse_video_id(url)
    if not vid:
        print(f"❌ 파싱 실패: {url}")
        continue

    meta = fetch(vid)
    if not meta:
        print(f"❌ 조회 실패: {vid}")
        continue

    desc = meta["description"]
    print(f"\n{'='*50}")
    print(f"제목: {meta['title']}")
    print(f"설명 길이: {len(desc)}자")
    print(f"설명: {desc[:300]}")