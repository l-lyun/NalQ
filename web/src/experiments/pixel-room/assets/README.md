# 도트 실험 자산 생성 기록

## 2026-09-07 캐릭터·의상·방 선택 확장

### 세 캐릭터의 크기를 유지하는 니트

- `cat-sweater.png`, `bear-sweater.png`: 각각 기본 고양이·곰을 참조해 내장 image_gen으로 만든 니트 착용 원본. `species-sweater-prompts.json`에 프롬프트와 배경 제거 시도 기록.
- 고양이·곰 착용 원본은 배경 제거를 재요청해도 체크무늬 RGB를 반환했다. 전체 이미지를 장면에 직접 표시하지 않는다. 옷 내부의 다각형 영역과 기본 캐릭터의 alpha 실루엣을 교차해 표시한다.
- 세 종 모두 머리·발·외곽 기준은 기본 이미지로 유지하고 몸통의 니트만 교체한다. 착용 원본은 프로덕션용 완성 sprite가 아니며 옷마다 표시 영역 검수가 필요하다.

### 용의 니트 착용 이미지 비교

- `dragon-sweater.png`: 용의 몸에 니트를 입혀 생성한 일체형 1254×1254 RGBA 이미지. 투명 모서리와 alpha 채널 확인. 생성 및 배경 제거 모두 내장 image_gen 사용, `dragon-sweater-prompt.txt`에 기록.
- 생성된 착용 원본은 기본 용보다 외곽이 크다(불투명 alpha>200 기준 기본 x183..1052/y110..1102, 착용 x171..1083/y96..1128). 이 원본 전체로 캐릭터를 교체하면 안 된다.
- 용+니트일 때 기본 용은 그대로 유지하고 착용 이미지의 몸통 부분만 정렬해 원래 몸의 alpha 외곽 안에 표시한다. 머리·날개·꼬리·발과 모자 기준점은 기본 용 기준으로 고정한다. 발 영역은 기존 셔플 방식으로 움직인다.
- 브라우저에서 기본/니트 전환, 모자 제거·재착용, 방 변경 후 착용 유지, 이동 위치·프레임 변경, 320px 가로 넘침 없음 확인. 고양이·곰은 기존 레이어 비교 대상으로 남긴다.

현재 화면은 아래의 독립 생성 자산을 사용합니다. 이후 절의 매트 처리 및 이전 atlas 설명은 첫 실험 기록으로 보존하며 현재 렌더러에는 적용되지 않습니다.

- `dragon-alpha.png`, `cat.png`, `bear.png`: 1254×1254, 실제 alpha PNG 캐릭터.
- `hat-beret.png`, `hat-beanie.png`, `top-sweater.png`: 1254×1254, 실제 alpha PNG 의상.
- `room-day.png`, `room-night.png`: 1536×1024 완성 방 배경.
- 생성 도구: 내장 image_gen. 원본 프롬프트와 참조 이미지 기록은 함께 복사한 `catalog-prompts.json` 및 `catalog-cleanup-prompts.json` 참조.
- 원본을 수정하지 않고 SVG의 캐릭터별 위치·크기로 착용을 맞춥니다. 발 영역을 작게 움직이는 실험이며 실제 걷기 sprite sheet나 모든 아이템 호환 검수가 완료된 자산은 아닙니다.

## 이전 atlas 실험 기록

내장 이미지 생성 도구로 만든 실험 자산입니다. 원본 이미지는 수정하지 않고 SVG에서 셀 선택·정렬·매트 표시 처리를 합니다. 매트의 흰 외곽 혼합 픽셀은 이진 전경 마스크를 원본 좌표 기준 2px 침식해 실험 화면에서만 숨깁니다. 이 처리는 밝은 자산의 가장자리를 손상할 수 있으므로 실제 alpha PNG와 정렬 검수가 완료된 최종 상업용 자산을 의미하지 않습니다.

- room.png: 1536×1024, 캐릭터 없는 공부방.
- character-walk.png: 1254×1254, 627×627 셀 2×2. 원본 머리 위치 편차를 보정하고, 상체는 첫 프레임으로 고정하며 발만 순환합니다.
- hats.png: 1774×887, 887×887 셀 2개. 원본과 같은 셀 크기로 환산한 뒤 착용 위치를 맞춥니다.
- 배경 투명화 도구 요청도 실제 alpha 대신 RGB 체크무늬를 반환했습니다. 최종 원본은 첫 캐릭터 출력이며 두 번째 출력은 채택하지 않았습니다.

## 캐릭터 생성 프롬프트

Production game asset, NOT a UI mockup. Create a PNG sprite sheet on genuinely transparent background (alpha). Square 1024x1024 canvas divided invisibly into EXACTLY 2 columns and 2 rows of 512x512 cells. Each cell contains ONE animation frame of the SAME original tiny charcoal dragon from the reference board, sage green cardigan, cream muzzle, little horns, folded wings, stubby short legs. NO HAT: bare head so hats can be overlaid later. No room, ground, shadow, text, labels, borders, gridlines. Transparent pixels everywhere except the character.
Pixel art simplified to a clean 64x64 logical pixel grid PER CELL, scaled nearest neighbor to 512x512; chunky consistent squares, limited 16-color palette, absolutely no noisy texture, blur or painterly shading. Keep character full-body at EXACT same horizontal center x256 and same head top y104, head center x256 y192, feet baseline y440 in EVERY CELL. Character visible bounding width about256px, full height336px. Front facing body, little feet facing slightly right, eyes consistent. Upper body, head, torso, wings and horns MUST be pixel-identical across all 4 frames. Only the short feet and bottom hem may change to imply shuffling walk.
Animation frame order left-to-right top-to-bottom: frame0 left foot forward/right foot behind; frame1 feet together contact; frame2 right foot forward/left behind; frame3 feet together contact. Head stationary, body stationary, no bob baked in. Frame1 and3 same if needed. Walking is meant for sideways screen translation while torso faces viewer, not full side-view. All 4 frames EXACTLY identical scale and registration, no automatic crop or varying positions. This is a test asset for layered dress-up; hats later align at cell x256 y120. Preserve recognizable charcoal dragon charm from reference but prioritize clean small pixel-art production geometry. No hat on any frame.

## 모자 생성 프롬프트

Production pixel game clothing asset sheet, NOT an interface. Use reference ONLY for matching pixel art style and the dragon head shape. Output TWO HATS ONLY on pure flat white background #FFFFFF, no checkerboard, no alpha simulation, no creature, body, text, grid, shadow or room. Wide canvas exact 2:1 aspect ratio, divided into two invisible equal SQUARE cells. Each cell is a full character coordinate canvas, so MOST of each square must be empty white; hats sit near TOP.
First square: cream beret in warm oatmeal beige with charcoal-brown pixel outline, tiny stem. Second square: muted denim blue knit beanie with turned-up cuff, dark charcoal outline.
Registration for BOTH square cells: hat horizontally centered at 52% of cell width, hat brim bottom exactly at 25% of cell height. Hat bounding width40% of cell width, hat visible height20% of cell height (top5% bottom25%). Both hats shown front facing, proportionate to the broad round head in the supplied sprite sheet. Hats have solid interior color (no see-through inside) and simple clean blocky pixels. Use crisp pixel art with very limited colors, no highlights near pure white: lightest cream at most RGB225,212,175 so it separates from white matte. Blue muted dark. Empty canvas below each hat must remain blank pure white down to bottom. Do not center hats vertically. No other elements. This aligns these hats atop a character whose head top is at20% of each same-size cell.

## 방 생성 프롬프트

Production pixel-art room BACKGROUND asset for NalQ. Use the original board as style reference for its cozy wooden study room, but deliver ONLY THE EMPTY ROOM as one landscape image, 3:2 aspect ratio. No UI, labels, frame, phone, text, people, creatures or characters. Room must be empty of any creature: character is separately animated later. Front facing 2D cutaway with shallow floor depth, consistent crisp chunky pixel art, warm wood, cream plaster wall, muted sage furnishings, daylight. Back wall with centered window and sky, wooden desk and open book just left of center, green desk lamp, low bookshelf at right, potted plant at left. Bottom 35% of canvas is uncluttered wooden FLOOR as a horizontal walking lane spanning left to right with safe margins; no foreground objects crossing that lane. Small rug can lie flat farther back under desk, not dominate walking lane. Art fills whole image to edges, no white border. Restrained detailed pixel scene, not smooth 3D, no perspective distortion, no dramatic lighting, no random new objects. The floor baseline for the separate character feet will sit at 88% of image height. Keep desk and bookshelf behind 65% of image height so the walking character doesn't collide with them.
