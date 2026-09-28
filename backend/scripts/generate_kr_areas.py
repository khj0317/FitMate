"""
전국 행정구역(시 / 시군구 / 읍면동) 검색 목록을 만든다.

입력: vuski/admdongkor 의 행정동 경계 GeoJSON (WGS84)
  https://github.com/vuski/admdongkor  (예: ver20260701/HangJeongDong_ver20260701.geojson)
출력: src/main/resources/locations/kr-areas.csv  (name,areaName,latitude,longitude,level)

출처: 본 데이터는 통계청 통계지리정보서비스(SGIS, https://sgis.kostat.go.kr)에서 공공누리 제1유형으로
개방한 행정동 경계를 가공한 것이며(가공: vuski/admdongkor), CC BY 4.0으로 배포됩니다.

사용법:
  python scripts/generate_kr_areas.py HangJeongDong_ver20260701.geojson
"""
import csv
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "src/main/resources/locations/kr-areas.csv"

# 레벨: 검색 결과 정렬에 쓴다 (작을수록 넓은 지역이라 먼저 보여준다)
LEVEL_CITY = 0      # 일반구가 있는 시 (부천시, 성남시 등)
LEVEL_DISTRICT = 1  # 시군구
LEVEL_DONG = 3      # 읍면동 (2는 역·명소 목록이 쓴다)

SIDO_SHORT = {
    "서울특별시": "서울", "부산광역시": "부산", "대구광역시": "대구", "인천광역시": "인천",
    "광주광역시": "광주", "대전광역시": "대전", "울산광역시": "울산", "세종특별자치시": "세종",
    "경기도": "경기", "강원특별자치도": "강원", "강원도": "강원", "충청북도": "충북", "충청남도": "충남",
    "전북특별자치도": "전북", "전라북도": "전북", "전라남도": "전남", "경상북도": "경북", "경상남도": "경남",
    "제주특별자치도": "제주", "전남광주통합특별시": "전남광주",
}


def shoelace(ring):
    """평면 근사로 외곽선의 넓이와 무게중심을 구한다 (동 단위 크기에서는 충분히 정확)"""
    area = cx = cy = 0.0
    for (x1, y1), (x2, y2) in zip(ring, ring[1:] + ring[:1]):
        cross = x1 * y2 - x2 * y1
        area += cross
        cx += (x1 + x2) * cross
        cy += (y1 + y2) * cross
    area /= 2
    if abs(area) < 1e-12:
        xs, ys = zip(*ring)
        return 0.0, sum(xs) / len(xs), sum(ys) / len(ys)
    return abs(area), cx / (6 * area), cy / (6 * area)


def centroid(geometry):
    """(멀티)폴리곤의 넓이 가중 무게중심. 섬이 여러 개인 지역도 전체 넓이로 가중한다"""
    polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
    total = sx = sy = 0.0
    for polygon in polygons:
        area, x, y = shoelace([tuple(point[:2]) for point in polygon[0]])
        total += area
        sx += x * area
        sy += y * area
    if total == 0:
        area, x, y = shoelace([tuple(point[:2]) for point in polygons[0][0]])
        return 0.0, x, y
    return total, sx / total, sy / total


def split_sgg(sggnm):
    """'부천시원미구' → ('부천시', '원미구'), '도봉구' → (None, '도봉구')"""
    match = re.fullmatch(r"(.+?시)(.+구)", sggnm)
    return (match.group(1), match.group(2)) if match else (None, sggnm)


def main(geojson_path):
    features = json.loads(Path(geojson_path).read_text(encoding="utf-8"))["features"]
    rows = []
    districts = defaultdict(lambda: [0.0, 0.0, 0.0])  # key → [넓이 합, x*넓이, y*넓이]
    cities = defaultdict(lambda: [0.0, 0.0, 0.0])

    for feature in features:
        props = feature["properties"]
        sido = SIDO_SHORT.get(props["sidonm"], props["sidonm"])
        city, gu = split_sgg(props["sggnm"])
        sgg_label = f"{city} {gu}" if city else gu
        dong = props["adm_nm"].split()[-1]
        area, lng, lat = centroid(feature["geometry"])

        rows.append((dong, f"{sido} {sgg_label} {dong}", lat, lng, LEVEL_DONG))
        for bucket, key in ((districts, (sido, sgg_label)), (cities, (sido, city))):
            if key[1] is None:
                continue
            bucket[key][0] += area
            bucket[key][1] += lng * area
            bucket[key][2] += lat * area

    for (sido, label), (area, sx, sy) in districts.items():
        rows.append((label, f"{sido} {label}", sy / area, sx / area, LEVEL_DISTRICT))
    for (sido, city), (area, sx, sy) in cities.items():
        rows.append((city, f"{sido} {city}", sy / area, sx / area, LEVEL_CITY))

    rows.sort(key=lambda row: (row[4], row[1]))
    with OUTPUT.open("w", encoding="utf-8", newline="") as out:
        writer = csv.writer(out, lineterminator="\n")
        writer.writerow(["name", "areaName", "latitude", "longitude", "level"])
        for name, area_name, lat, lng, level in rows:
            writer.writerow([name, area_name, f"{lat:.6f}", f"{lng:.6f}", level])
    print(f"{len(rows)} rows → {OUTPUT}")


if __name__ == "__main__":
    main(sys.argv[1])
