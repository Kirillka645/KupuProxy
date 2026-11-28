#!/usr/bin/env python3
"""Regenerate Android/desktop icons from the approved source. Requires Pillow."""
from pathlib import Path
from collections import deque
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'assets/branding/icon-source.png'
BACKGROUND = '#1e2427'


def generate():
    image = Image.open(SOURCE).convert('RGBA')
    if image.size != (1024, 1024):
        raise ValueError('Expected a 1024x1024 approved source')
    # Remove only white pixels connected to the outer corners, not the logo.
    pixels = image.load()
    queue = deque([(0, 0), (1023, 0), (0, 1023), (1023, 1023)])
    seen = set()
    while queue:
        x, y = queue.popleft()
        if not (0 <= x < 1024 and 0 <= y < 1024) or (x, y) in seen:
            continue
        seen.add((x, y))
        r, g, b, _ = pixels[x, y]
        if min(r, g, b) < 235:
            continue
        pixels[x, y] = (r, g, b, 0)
        queue.extend(((x-1, y), (x+1, y), (x, y-1), (x, y+1)))
    desktop = ROOT / 'desktop/src/desktopMain/resources'
    image.resize((128,128), Image.Resampling.LANCZOS).save(ROOT / 'assets/branding/readme-icon.png', optimize=True)
    image.save(desktop / 'icon.png', optimize=True)
    image.save(desktop / 'icon.ico', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
    image.save(desktop / 'icon.icns')
    res = ROOT / 'app/src/main/res'
    for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        directory = res / ('mipmap-' + density)
        directory.mkdir(exist_ok=True)
        for name in ('ic_launcher', 'ic_launcher_round'):
            icon = image.resize((size, size), Image.Resampling.LANCZOS)
            if name.endswith('round'):
                # Crop the legacy round asset to a real circle without white corners.
                mask = Image.new('L', (size, size));ImageDraw.Draw(mask).ellipse((0,0,size-1,size-1), fill=255)
                icon.putalpha(mask)
            icon.save(directory / (name + '.png'), optimize=True)
    # Adaptive foreground: isolate the green mark, leaving shape/mask to Android.
    mark = Image.open(SOURCE).convert('RGBA').crop((272,254,752,770))
    data = []
    for r,g,b,a in mark.getdata():
        alpha = max(0, min(255, round((g-r-12)*255/70))) if g > 60 else 0
        data.append((r,g,b,alpha))
    mark.putdata(data)
    mark.thumbnail((188,188), Image.Resampling.LANCZOS)
    foreground = Image.new('RGBA',(432,432))
    foreground.alpha_composite(mark, ((432-mark.width)//2,(432-mark.height)//2))
    directory = res / 'drawable-xxxhdpi';directory.mkdir(exist_ok=True)
    foreground.save(directory / 'ic_launcher_foreground.png', optimize=True)
    # Monochrome vector for Android 13 themed icons, using the same K silhouette.
    (res / 'drawable/ic_launcher_monochrome.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="0.09" android:scaleY="0.09" android:translateX="8" android:translateY="8">
        <path android:fillColor="#FFFFFFFF" android:pathData="M274,256H419V439C480,419 510,375 530,347L500,325 567,301 594,256H750C663,434 567,555 419,575V768H274Z M314,296V728H378V540C520,534 608,436 683,296H617L589,341 586,391 559,371C511,438 467,473 378,486V296Z" android:fillType="evenOdd" />
        <path android:fillColor="#FFFFFFFF" android:pathData="M551,555L583,532C655,598 703,686 750,768H586C550,680 518,646 495,636L471,663 447,576 539,583 518,609C560,643 590,692 610,728H682C643,660 604,604 551,555Z" />
    </group>
</vector>
''')
    # QA renders show the exact desktop image and launcher mask variants.
    preview = Image.new('RGB', (760,320), '#f4f5f6')
    icon = image.resize((256,256),Image.Resampling.LANCZOS)
    preview.paste(icon,(20,32),icon)
    for offset,shape in [(320,'circle'),(540,'rounded')]:
        tile = Image.new('RGBA',(192,192),BACKGROUND)
        tile.alpha_composite(foreground.resize((288,288),Image.Resampling.LANCZOS),(-48,-48))
        mask = Image.new('L',(192,192));draw=ImageDraw.Draw(mask)
        if shape=='circle':draw.ellipse((0,0,191,191),fill=255)
        else:draw.rounded_rectangle((0,0,191,191),radius=40,fill=255)
        preview.paste(tile,(offset,64),mask)
    import os
    preview_path = os.environ.get('KUPU_ICON_PREVIEW')
    if preview_path:
        preview.save(preview_path)


if __name__ == '__main__':
    generate()
