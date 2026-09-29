#!/usr/bin/env python3
"""Apply reviewed translations to the generated Android resource files."""

from pathlib import Path
import re
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape


ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
BASE = ET.parse(ROOT / "values/strings.xml").getroot()
SOURCES = {item.get("name"): (item.text or "") for item in BASE.findall("string")}
TRANSLATABLE = [item.get("name") for item in BASE.findall("string") if item.get("translatable") != "false"]
LOCALES = ["zh-rCN", "zh-rTW", "ja", "ko", "es", "fr", "de", "pt-rBR", "ru"]
OVERRIDES = {locale: {} for locale in LOCALES}

# Translation engines often read isolated media terms as people, geography, or
# web search. Repair those terms only when the English source gives the context.
CONTEXT_REPAIRS = {
    "zh-rCN": {
        "player": {"玩家": "播放器"},
        "disabled": {"残疾人": "已停用"},
        "subtitle": {"副标题": "字幕"},
        "seek": {"寻求": "跳转", "寻找": "跳转", "查找": "跳转", "搜索": "跳转", "寻道": "跳转"},
        "landscape": {"风景": "横屏", "景观": "横屏"},
        "portrait": {"肖像": "竖屏"},
    },
    "zh-rTW": {
        "player": {"玩家": "播放器"},
        "disabled": {"殘障人士": "已停用"},
        "subtitle": {"副標題": "字幕"},
        "seek": {"尋求": "跳轉", "尋找": "跳轉", "查找": "跳轉", "搜尋": "跳轉"},
        "landscape": {"風景": "橫向", "景觀": "橫向"},
        "portrait": {"肖像": "直向"},
    },
    "ja": {
        "disabled": {"障害者": "無効"},
        "seek": {"探しています": "シーク", "探す": "シーク", "検索": "シーク"},
        "portrait": {"肖像画": "縦向き"},
        "landscape": {"風景": "横向き"},
    },
    "ko": {
        "disabled": {"장애인": "사용 안 함"},
        "seek": {"추구": "재생 위치 이동", "검색": "재생 위치 이동"},
        "portrait": {"초상화": "세로"},
        "landscape": {"풍경": "가로"},
    },
    "es": {"disabled": {"Discapacitado": "Desactivado"}, "player": {"Jugador": "Reproductor", "jugador": "reproductor"}},
    "fr": {"player": {"Joueur": "Lecteur", "joueur": "lecteur"}},
    "de": {"player": {"Spieler": "Player"}, "seek": {"Ich suche": "Spulen"}},
    "pt-rBR": {"player": {"Jogador": "Reprodutor", "jogador": "reprodutor"}},
    "ru": {"player": {"Игрок": "Плеер", "игрока": "плеера", "игроком": "плеером"}, "seek": {"Ищу": "Перемотка"}},
}

# Taiwan Android interfaces use these terms consistently. Machine translation
# mixes Mainland and Taiwan vocabulary, sometimes even within one sentence.
ZH_TW_WORDING = (
    ("文件夹", "資料夾"),
    ("文件夾", "資料夾"),
    ("檔案夹", "資料夾"),
    ("文件", "檔案"),
    ("視頻", "影片"),
    ("播放列表", "播放清單"),
    ("插件", "外掛"),
    ("設置", "設定"),
    ("創建", "建立"),
    ("設備", "裝置"),
    ("本地", "本機"),
    ("刷新", "重新整理"),
    ("添加", "新增"),
    ("當前", "目前"),
    ("脚本", "腳本"),
    ("发現", "發現"),
    ("发现", "發現"),
    ("输入", "輸入"),
    ("自定义", "自訂"),
    ("隐藏", "隱藏"),
    ("时间", "時間"),
    ("这会", "這會"),
    ("将", "將"),
    ("为", "為"),
    ("该", "該"),
    ("区域", "區域"),
    ("控件", "控制項"),
    ("重置", "重設"),
    ("默认", "預設"),
    ("导入", "匯入"),
    ("導入", "匯入"),
    ("鏈接", "連結"),
    ("視圖", "檢視"),
    ("转移", "移動"),
    ("空间", "空間"),
    ("翻转", "翻轉"),
    ("开启", "開啟"),
    ("关闭", "關閉"),
)

# Complete phrases are needed here: grammatical number changes the noun and,
# in several languages, the verb as well.
PLURALS = {
    "zh-rCN": {
        "lua_selected_count": {"other": "已选中 %1$d 个脚本"},
        "videos_moved_to_private_space": {"other": "已将 %1$d 个视频移至私人空间。"},
        "share_videos": {"other": "分享 %1$d 个视频"},
        "items_count": {"other": "%1$d 项"},
        "videos_count_with_date": {"other": "%1$d 个视频 · %2$s"},
        "columns_count": {"other": "%1$d 列"},
        "folders_found": {"other": "已找到 %1$d 个文件夹…"},
        "videos_added_to_playlist": {"other": "已将 %1$d 个视频添加到“%2$s”"},
        "adding_videos_to_playlist": {"other": "正在将 %1$d 个视频添加到播放列表"},
    },
    "zh-rTW": {
        "lua_selected_count": {"other": "已選取 %1$d 個腳本"},
        "videos_moved_to_private_space": {"other": "已將 %1$d 部影片移至私人空間。"},
        "share_videos": {"other": "分享 %1$d 部影片"},
        "items_count": {"other": "%1$d 個項目"},
        "videos_count_with_date": {"other": "%1$d 部影片 · %2$s"},
        "columns_count": {"other": "%1$d 欄"},
        "folders_found": {"other": "已找到 %1$d 個資料夾…"},
        "videos_added_to_playlist": {"other": "已將 %1$d 部影片加入「%2$s」"},
        "adding_videos_to_playlist": {"other": "正在將 %1$d 部影片加入播放清單"},
    },
    "ja": {
        "lua_selected_count": {"other": "%1$d 件のスクリプトを選択中"},
        "videos_moved_to_private_space": {"other": "%1$d 本の動画をプライベートスペースに移動しました。"},
        "share_videos": {"other": "%1$d 本の動画を共有"},
        "items_count": {"other": "%1$d 件"},
        "videos_count_with_date": {"other": "動画 %1$d 本・%2$s"},
        "columns_count": {"other": "%1$d 列"},
        "folders_found": {"other": "フォルダーが %1$d 件見つかりました…"},
        "videos_added_to_playlist": {"other": "%1$d 本の動画を「%2$s」に追加しました"},
        "adding_videos_to_playlist": {"other": "%1$d 本の動画をプレイリストに追加中"},
    },
    "ko": {
        "lua_selected_count": {"other": "스크립트 %1$d개 선택됨"},
        "videos_moved_to_private_space": {"other": "동영상 %1$d개를 비공개 공간으로 이동했습니다."},
        "share_videos": {"other": "동영상 %1$d개 공유"},
        "items_count": {"other": "항목 %1$d개"},
        "videos_count_with_date": {"other": "동영상 %1$d개 · %2$s"},
        "columns_count": {"other": "%1$d열"},
        "folders_found": {"other": "폴더 %1$d개 찾음…"},
        "videos_added_to_playlist": {"other": "동영상 %1$d개를 ‘%2$s’ 재생목록에 추가했습니다"},
        "adding_videos_to_playlist": {"other": "동영상 %1$d개를 재생목록에 추가하는 중"},
    },
    "es": {
        "lua_selected_count": {"one": "%1$d script seleccionado", "other": "%1$d scripts seleccionados"},
        "videos_moved_to_private_space": {"one": "Se ha movido %1$d vídeo al espacio privado.", "other": "Se han movido %1$d vídeos al espacio privado."},
        "share_videos": {"one": "Compartir %1$d vídeo", "other": "Compartir %1$d vídeos"},
        "items_count": {"one": "%1$d elemento", "other": "%1$d elementos"},
        "videos_count_with_date": {"one": "%1$d vídeo • %2$s", "other": "%1$d vídeos • %2$s"},
        "columns_count": {"one": "%1$d columna", "other": "%1$d columnas"},
        "folders_found": {"one": "Se ha encontrado %1$d carpeta…", "other": "Se han encontrado %1$d carpetas…"},
        "videos_added_to_playlist": {"one": "Se ha añadido %1$d vídeo a «%2$s»", "other": "Se han añadido %1$d vídeos a «%2$s»"},
        "adding_videos_to_playlist": {"one": "Añadiendo %1$d vídeo a la lista", "other": "Añadiendo %1$d vídeos a la lista"},
    },
    "fr": {
        "lua_selected_count": {"one": "%1$d script sélectionné", "other": "%1$d scripts sélectionnés"},
        "videos_moved_to_private_space": {"one": "%1$d vidéo déplacée dans l’espace privé.", "other": "%1$d vidéos déplacées dans l’espace privé."},
        "share_videos": {"one": "Partager %1$d vidéo", "other": "Partager %1$d vidéos"},
        "items_count": {"one": "%1$d élément", "other": "%1$d éléments"},
        "videos_count_with_date": {"one": "%1$d vidéo • %2$s", "other": "%1$d vidéos • %2$s"},
        "columns_count": {"one": "%1$d colonne", "other": "%1$d colonnes"},
        "folders_found": {"one": "%1$d dossier trouvé…", "other": "%1$d dossiers trouvés…"},
        "videos_added_to_playlist": {"one": "%1$d vidéo ajoutée à « %2$s »", "other": "%1$d vidéos ajoutées à « %2$s »"},
        "adding_videos_to_playlist": {"one": "Ajout de %1$d vidéo à la liste", "other": "Ajout de %1$d vidéos à la liste"},
    },
    "de": {
        "lua_selected_count": {"one": "%1$d Skript ausgewählt", "other": "%1$d Skripte ausgewählt"},
        "videos_moved_to_private_space": {"one": "%1$d Video in den privaten Bereich verschoben.", "other": "%1$d Videos in den privaten Bereich verschoben."},
        "share_videos": {"one": "%1$d Video teilen", "other": "%1$d Videos teilen"},
        "items_count": {"one": "%1$d Element", "other": "%1$d Elemente"},
        "videos_count_with_date": {"one": "%1$d Video • %2$s", "other": "%1$d Videos • %2$s"},
        "columns_count": {"one": "%1$d Spalte", "other": "%1$d Spalten"},
        "folders_found": {"one": "%1$d Ordner gefunden…", "other": "%1$d Ordner gefunden…"},
        "videos_added_to_playlist": {"one": "%1$d Video zu „%2$s“ hinzugefügt", "other": "%1$d Videos zu „%2$s“ hinzugefügt"},
        "adding_videos_to_playlist": {"one": "%1$d Video wird zur Wiedergabeliste hinzugefügt", "other": "%1$d Videos werden zur Wiedergabeliste hinzugefügt"},
    },
    "pt-rBR": {
        "lua_selected_count": {"one": "%1$d script selecionado", "other": "%1$d scripts selecionados"},
        "videos_moved_to_private_space": {"one": "%1$d vídeo movido para o espaço privado.", "other": "%1$d vídeos movidos para o espaço privado."},
        "share_videos": {"one": "Compartilhar %1$d vídeo", "other": "Compartilhar %1$d vídeos"},
        "items_count": {"one": "%1$d item", "other": "%1$d itens"},
        "videos_count_with_date": {"one": "%1$d vídeo • %2$s", "other": "%1$d vídeos • %2$s"},
        "columns_count": {"one": "%1$d coluna", "other": "%1$d colunas"},
        "folders_found": {"one": "%1$d pasta encontrada…", "other": "%1$d pastas encontradas…"},
        "videos_added_to_playlist": {"one": "%1$d vídeo adicionado a “%2$s”", "other": "%1$d vídeos adicionados a “%2$s”"},
        "adding_videos_to_playlist": {"one": "Adicionando %1$d vídeo à playlist", "other": "Adicionando %1$d vídeos à playlist"},
    },
    "ru": {
        "lua_selected_count": {"one": "Выбран %1$d скрипт", "few": "Выбрано %1$d скрипта", "many": "Выбрано %1$d скриптов", "other": "Выбрано %1$d скрипта"},
        "videos_moved_to_private_space": {"other": "%1$d видео перемещено в личное пространство."},
        "share_videos": {"other": "Поделиться %1$d видео"},
        "items_count": {"one": "%1$d элемент", "few": "%1$d элемента", "many": "%1$d элементов", "other": "%1$d элемента"},
        "videos_count_with_date": {"other": "%1$d видео • %2$s"},
        "columns_count": {"one": "%1$d столбец", "few": "%1$d столбца", "many": "%1$d столбцов", "other": "%1$d столбца"},
        "folders_found": {"one": "Найдена %1$d папка…", "few": "Найдено %1$d папки…", "many": "Найдено %1$d папок…", "other": "Найдено %1$d папки…"},
        "videos_added_to_playlist": {"other": "%1$d видео добавлено в «%2$s»"},
        "adding_videos_to_playlist": {"other": "Добавление %1$d видео в плейлист"},
    },
}

NEW_VIDEO_DAYS = {
    "zh-rCN": {"other": "最近 %1$d 天内加入且从未播放的视频会标记为“新”"},
    "zh-rTW": {"other": "最近 %1$d 天內新增且從未播放的影片會標示為「新」"},
    "ja": {"other": "過去 %1$d 日以内に追加された未再生の動画を「新着」と表示"},
    "ko": {"other": "최근 %1$d일 이내에 추가한 미재생 동영상을 ‘신규’로 표시"},
    "es": {
        "one": "Los vídeos añadidos en el último %1$d día que aún no se han reproducido se marcarán como nuevos",
        "other": "Los vídeos añadidos en los últimos %1$d días que aún no se han reproducido se marcarán como nuevos",
    },
    "fr": {
        "one": "Les vidéos ajoutées au cours du dernier %1$d jour et jamais lues seront marquées comme nouvelles",
        "other": "Les vidéos ajoutées au cours des %1$d derniers jours et jamais lues seront marquées comme nouvelles",
    },
    "de": {
        "one": "Videos, die innerhalb von %1$d Tag hinzugefügt und noch nicht abgespielt wurden, erhalten die Markierung „Neu“",
        "other": "Videos, die innerhalb von %1$d Tagen hinzugefügt und noch nicht abgespielt wurden, erhalten die Markierung „Neu“",
    },
    "pt-rBR": {
        "one": "Vídeos adicionados no último %1$d dia e ainda não reproduzidos serão marcados como novos",
        "other": "Vídeos adicionados nos últimos %1$d dias e ainda não reproduzidos serão marcados como novos",
    },
    "ru": {
        "one": "Видео, добавленные за последний %1$d день и ещё не воспроизводившиеся, будут отмечены как новые",
        "few": "Видео, добавленные за последние %1$d дня и ещё не воспроизводившиеся, будут отмечены как новые",
        "many": "Видео, добавленные за последние %1$d дней и ещё не воспроизводившиеся, будут отмечены как новые",
        "other": "Видео, добавленные за последние %1$d дня и ещё не воспроизводившиеся, будут отмечены как новые",
    },
}
for locale, quantities in NEW_VIDEO_DAYS.items():
    PLURALS[locale]["pref_appearance_unplayed_old_video_days_summary"] = quantities

PLURALS_FOR_COUNTS = {
    "zh-rCN": {
        "fonts_loaded": {"other": "已加载 %1$d 种字体"},
        "file_unique_filename_failed": {"other": "尝试 %1$d 次后，仍找不到可用的文件名"},
    },
    "zh-rTW": {
        "fonts_loaded": {"other": "已載入 %1$d 種字型"},
        "file_unique_filename_failed": {"other": "嘗試 %1$d 次後，仍找不到可用的檔名"},
    },
    "ja": {
        "fonts_loaded": {"other": "フォントを %1$d 件読み込みました"},
        "file_unique_filename_failed": {"other": "%1$d 回試しても使用できるファイル名が見つかりませんでした"},
    },
    "ko": {
        "fonts_loaded": {"other": "글꼴 %1$d개 불러옴"},
        "file_unique_filename_failed": {"other": "%1$d번 시도했지만 사용할 수 있는 파일 이름을 찾지 못했습니다"},
    },
    "es": {
        "fonts_loaded": {"one": "Se ha cargado %1$d fuente", "other": "Se han cargado %1$d fuentes"},
        "file_unique_filename_failed": {"one": "No se encontró un nombre de archivo disponible tras %1$d intento", "other": "No se encontró un nombre de archivo disponible tras %1$d intentos"},
        "seconds": {"one": "%1$d segundo", "other": "%1$d segundos"},
    },
    "fr": {
        "fonts_loaded": {"one": "%1$d police chargée", "other": "%1$d polices chargées"},
        "file_unique_filename_failed": {"one": "Aucun nom de fichier disponible après %1$d tentative", "other": "Aucun nom de fichier disponible après %1$d tentatives"},
        "seconds": {"one": "%1$d seconde", "other": "%1$d secondes"},
    },
    "de": {
        "fonts_loaded": {"one": "%1$d Schriftart geladen", "other": "%1$d Schriftarten geladen"},
        "file_unique_filename_failed": {"one": "Nach %1$d Versuch wurde kein freier Dateiname gefunden", "other": "Nach %1$d Versuchen wurde kein freier Dateiname gefunden"},
    },
    "pt-rBR": {
        "fonts_loaded": {"one": "%1$d fonte carregada", "other": "%1$d fontes carregadas"},
        "file_unique_filename_failed": {"one": "Não foi encontrado um nome de arquivo disponível após %1$d tentativa", "other": "Não foi encontrado um nome de arquivo disponível após %1$d tentativas"},
        "seconds": {"one": "%1$d segundo", "other": "%1$d segundos"},
    },
    "ru": {
        "fonts_loaded": {"one": "Загружен %1$d шрифт", "few": "Загружено %1$d шрифта", "many": "Загружено %1$d шрифтов", "other": "Загружено %1$d шрифта"},
        "file_unique_filename_failed": {"one": "После %1$d попытки не удалось найти свободное имя файла", "few": "После %1$d попыток не удалось найти свободное имя файла", "many": "После %1$d попыток не удалось найти свободное имя файла", "other": "После %1$d попыток не удалось найти свободное имя файла"},
    },
}
for locale, translations in PLURALS_FOR_COUNTS.items():
    PLURALS[locale].update(translations)

# CLDR has a "many" category for very large numbers in these languages; the
# displayed noun is still the regular plural. Russian has four categories even
# when the noun (such as видео) does not change form.
for locale in ("es", "fr", "pt-rBR"):
    for quantities in PLURALS[locale].values():
        quantities.setdefault("many", quantities["other"])
for quantities in PLURALS["ru"].values():
    for category in ("one", "few", "many"):
        quantities.setdefault(category, quantities["other"])

for number, line in enumerate((Path(__file__).parent / "i18n_glossary.txt").read_text().splitlines(), 1):
    if not line or line.startswith("#"):
        continue
    fields = line.split("|")
    if len(fields) != len(LOCALES) + 1:
        raise ValueError(f"Line {number}: expected {len(LOCALES) + 1} fields, got {len(fields)}")
    selector, *translations = fields
    keys = (
        [selector[1:]]
        if selector.startswith("@")
        else [key for key, source in SOURCES.items() if source == selector]
    )
    if not keys:
        raise ValueError(f"Line {number}: no base string matches {selector!r}")
    for locale, text in zip(LOCALES, translations):
        if not text.strip():
            raise ValueError(f"Line {number}: empty {locale} translation")
        for key in keys:
            OVERRIDES[locale][key] = text


def resource_text(value):
    return escape(value).replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")


for locale in LOCALES:
    path = ROOT / f"values-{locale}/strings.xml"
    changes = 0
    seen = set()
    lines = []
    for line in path.read_text().splitlines():
        match = re.match(r'(\s*<string name="([^"]+)"[^>]*>)(.*?)(</string>)$', line)
        if match:
            key = match.group(2)
            if key not in TRANSLATABLE:
                changes += 1
                continue
            seen.add(key)
            source = SOURCES.get(key, "").lower()
            value = match.group(3)
            if locale == "zh-rTW":
                for old, new in ZH_TW_WORDING:
                    value = value.replace(old, new)
                value = re.sub(r"視訊(?!編解碼器|驅動程式|串流|軌道)", "影片", value)
            if locale == "ja":
                value = re.sub(r"ビデオ(?![ 　]?ドライバー)", "動画", value)
            if locale == "ko":
                value = re.sub(r"비디오(?![ 　]?드라이버|[ 　]?코덱)", "동영상", value)
            if locale == "es":
                value = value.replace("guión", "guion").replace("Guión", "Guion")
            for term, replacements in CONTEXT_REPAIRS[locale].items():
                if re.search(rf"\b{term}\b", source):
                    for old, new in replacements.items():
                        value = value.replace(old, new)
            if key in OVERRIDES[locale]:
                value = resource_text(OVERRIDES[locale][key])
            if value != match.group(3):
                line = match.group(1) + value + match.group(4)
                changes += 1
        lines.append(line)
    output = "\n".join(lines) + "\n"
    for key in TRANSLATABLE:
        if key not in seen:
            if key not in OVERRIDES[locale]:
                raise ValueError(f"{locale}/{key}: missing translation and no reviewed replacement")
            new_string = f'  <string name="{key}">{resource_text(OVERRIDES[locale][key])}</string>'
            output = output.replace("</resources>", new_string + "\n</resources>")
            changes += 1
    for name, quantities in PLURALS[locale].items():
        block = f'  <plurals name="{name}">\n'
        block += "".join(
            f'    <item quantity="{quantity}">{resource_text(value)}</item>\n'
            for quantity, value in quantities.items()
        )
        block += "  </plurals>"
        expression = rf'  <plurals name="{name}">.*?</plurals>'
        if re.search(expression, output, flags=re.DOTALL):
            output = re.sub(expression, lambda _: block, output, flags=re.DOTALL)
        else:
            output = output.replace("</resources>", block + "\n</resources>")
    path.write_text(output)
    print(f"{locale}: reviewed {changes} strings")
