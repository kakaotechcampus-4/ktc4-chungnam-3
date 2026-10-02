"""앱에 번들하는 폰트의 서브셋을 만든다.

원본
  Noto Sans KR 400 · 500: npm 패키지 @expo-google-fonts/noto-sans-kr@0.4.3 안의 ttf
  (Google Fonts 배포본. OFL 1.1, Reserved Font Name 'Source').
  스크립트가 `npm pack` 으로 해당 버전을 임시 폴더에 받아서 꺼낸다. 프로젝트 의존성은 필요 없다.

서브셋 방식
  exclude: 원본 cmap 전체에서 제외 범위만 뺀다. 나머지 글자·기호는 모두 남긴다.
    Noto Sans KR 은 한자만 뺀다 (멘토 리뷰). 가게 이름·영상 제목에 어떤 글자가 올지 모르기 때문이다.
      U+4E00–9FFF   CJK 통합 한자
      U+3400–4DBF   CJK 통합 한자 확장 A
      U+F900–FAFF   CJK 호환 한자
      U+2E80–2FDF   CJK 부수 보충 · 강희 부수
      U+2FF0–2FFF   한자 구조 기술 문자
      U+20000 이상  확장 B 이후 · 호환 한자 보충(U+2F800–2FA1F)
  text_files: 파일에 적힌 글자만 남긴다. Gowun Dodum 처럼 고정 제목에만 쓰는 폰트용.
  커닝 등 OpenType 레이아웃 기능과 name 테이블(저작권·라이선스)은 유지한다. 출력은 ttf.

실행
  pip install fonttools
  cd frontend
  python scripts/subset-fonts.py            # 전체
  python scripts/subset-fonts.py NotoSansKR-Regular   # 이름으로 골라서
"""

import sys
import tarfile
import tempfile
import subprocess
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

FRONTEND = Path(__file__).resolve().parent.parent

HAN_RANGES = [
    (0x4E00, 0x9FFF),
    (0x3400, 0x4DBF),
    (0xF900, 0xFAFF),
    (0x2E80, 0x2FDF),
    (0x2FF0, 0x2FFF),
    (0x20000, 0x10FFFF),
]

FONTS = [
    {
        'name': 'NotoSansKR-Regular',
        'package': '@expo-google-fonts/noto-sans-kr@0.4.3',
        'file': '400Regular/NotoSansKR_400Regular.ttf',
        'exclude': HAN_RANGES,
        'output': 'assets/fonts/NotoSansKR-Regular-subset.ttf',
    },
    {
        'name': 'NotoSansKR-Medium',
        'package': '@expo-google-fonts/noto-sans-kr@0.4.3',
        'file': '500Medium/NotoSansKR_500Medium.ttf',
        'exclude': HAN_RANGES,
        'output': 'assets/fonts/NotoSansKR-Medium-subset.ttf',
    },
    # Gowun Dodum 은 화면 제목 확정 후 추가한다. 예:
    # {
    #     'name': 'GowunDodum-Regular',
    #     'package': '@expo-google-fonts/gowun-dodum@0.4.1',
    #     'file': '400Regular/GowunDodum_400Regular.ttf',
    #     'text_files': ['scripts/gowun-dodum-glyphs.txt'],
    #     'output': 'assets/fonts/GowunDodum-Regular-subset.ttf',
    # },
]


def fetch_package(spec: str, workdir: Path) -> Path:
    """npm pack 으로 패키지를 받아 풀고, 패키지 루트 경로를 돌려준다."""
    out = subprocess.run(
        ['npm', 'pack', spec, '--pack-destination', str(workdir), '--silent'],
        check=True, capture_output=True, text=True, shell=sys.platform == 'win32',
    )
    tgz = workdir / out.stdout.strip().splitlines()[-1]
    with tarfile.open(tgz) as tar:
        tar.extractall(workdir / tgz.stem, filter='data')
    return workdir / tgz.stem / 'package'


def in_ranges(cp: int, ranges) -> bool:
    return any(lo <= cp <= hi for lo, hi in ranges)


def codepoints_for(font: dict, source: Path) -> list[int]:
    if 'exclude' in font:
        cmap = TTFont(source).getBestCmap()
        return sorted(cp for cp in cmap if not in_ranges(cp, font['exclude']))
    chars = set()
    for text_file in font['text_files']:
        chars.update((FRONTEND / text_file).read_text(encoding='utf-8'))
    return sorted(ord(c) for c in chars if not c.isspace() or c == ' ')


def build(font: dict, source: Path) -> None:
    output = FRONTEND / font['output']
    output.parent.mkdir(parents=True, exist_ok=True)
    options = subset.Options()
    options.layout_features = ['*']
    options.name_IDs = ['*']
    options.name_languages = ['*']
    options.notdef_outline = True
    # 원본 수정 시각을 유지해 다시 실행해도 같은 파일이 나오게 한다.
    ttf = TTFont(source, recalcTimestamp=False)
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=codepoints_for(font, source))
    subsetter.subset(ttf)
    ttf.save(output)
    print(f"{font['name']}: {source.stat().st_size:,} B -> {output.stat().st_size:,} B  ({output.relative_to(FRONTEND)})")


def main() -> None:
    wanted = set(sys.argv[1:])
    targets = [f for f in FONTS if not wanted or f['name'] in wanted]
    if not targets:
        sys.exit(f"알 수 없는 이름: {', '.join(sorted(wanted))}")
    with tempfile.TemporaryDirectory() as tmp:
        packages: dict[str, Path] = {}
        for font in targets:
            spec = font['package']
            if spec not in packages:
                packages[spec] = fetch_package(spec, Path(tmp))
            build(font, packages[spec] / font['file'])


if __name__ == '__main__':
    main()
