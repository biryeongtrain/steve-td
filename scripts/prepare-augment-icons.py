from pathlib import Path
import hashlib
import json
from PIL import Image

root = Path(__file__).resolve().parents[1]
originals = root / 'art/augment-icons/originals'
textures = root / 'src/main/resources/semiontd/ui/augment-icons'
mapping = {
    'DAMAGE': 'attack', 'GUARD': 'protection', 'VITALITY': 'health',
    'REVIVAL': 'revival', 'TEMPO': 'speed', 'REACH': 'aim',
    'CONTROL': 'control', 'GROWTH': 'growth', 'RESOURCE': 'resources',
    'DEPLOY': 'summon', 'SYNERGY': 'synergy',
    'ENHANCEMENT': 'enhancement', 'FORTUNE': 'chance'
}
textures.mkdir(parents=True, exist_ok=True)
rows = []
for category, name in mapping.items():
    source = originals / (name + '.png')
    with Image.open(source) as image:
        if image.mode != 'RGBA' or image.size != (1254, 1254):
            raise ValueError(f'Unexpected source image: {source}')
        box = image.getchannel('A').point(lambda alpha: 255 if alpha >= 128 else 0).getbbox()
        if box is None:
            raise ValueError(f'Empty source silhouette: {source}')
        left, top, right, bottom = box
        scale = 256 * .76 / max(right - left, bottom - top)
        center_x, center_y = (left + right) / 2, (top + bottom) / 2
        resized = image.convert('RGBa').transform((256, 256), Image.Transform.AFFINE,
                (1 / scale, 0, center_x - 128 / scale,
                 0, 1 / scale, center_y - 128 / scale),
                resample=Image.Resampling.BICUBIC).convert('RGBA')
        destination = textures / (name + '.png')
        resized.save(destination, optimize=True)
        rows.append({'category': category, 'file': name + '.png',
                     'originalSHA256': hashlib.sha256(source.read_bytes()).hexdigest(),
                     'textureSHA256': hashlib.sha256(destination.read_bytes()).hexdigest(),
                     'sourceBoundsAlpha128': box, 'normalizedSize': [256, 256],
                     'normalizedBoundsAlpha128': resized.getchannel('A').point(
                         lambda alpha: 255 if alpha >= 128 else 0).getbbox()})
manifest = {'origin': 'User-supplied SemionTD-augment-art-originals.zip; original built-in image generation artwork',
            'originalPixelsPreserved': True, 'normalization': 'Alpha >= 128 bounds centered at 76% of 256px canvas; premultiplied-alpha bicubic resampling',
            'images': rows}
(originals.parent / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'normalizedImages': len(rows), 'textures': str(textures)}))
