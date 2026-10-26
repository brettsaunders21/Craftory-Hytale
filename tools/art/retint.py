"""Create Craftory mob textures from a local vanilla asset reference."""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

MOBS = {
    "Glimmer_Wisp": ("Flying_Critter/Bat", "NPC/Flying_Critter/Bat/Models/Texture.png"),
    "Sparkjaw": ("Swimming_Beast/Crocodile", "NPC/Swimming_Beast/Crocodile/Models/Texture.png"),
    "Riftmoth": ("Void/Spawn_Void", "NPC/Void/Spawn_Void/Models/Texture.png"),
    "Goblin_Poacher": ("Intelligent/Goblin/Goblin_Lobber", "NPC/Intelligent/Goblin/Models/Model_Textures/Rusty.png"),
    "Vessa": ("Intelligent/Klops/Klops_Merchant", "NPC/Intelligent/Klops/Models/Model_default.png"),
}


def luminance(rgb: np.ndarray) -> np.ndarray:
    return rgb[..., 0] * 0.2126 + rgb[..., 1] * 0.7152 + rgb[..., 2] * 0.0722


def gradient(
    image: Image.Image,
    low: str,
    middle: str,
    high: str,
    cyan_top: float = 0,
    bright_color: str = "#5ff3ff",
) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA")).copy()
    rgb = rgba[..., :3].astype(np.float32) / 255
    alpha = rgba[..., 3]
    lum = luminance(rgb)
    colors = np.array([[int(c[i:i + 2], 16) for i in (1, 3, 5)] for c in (low, middle, high)],
                      dtype=np.float32) / 255
    out = np.where(
        (lum <= 0.5)[..., None],
        colors[0] + (colors[1] - colors[0]) * (lum[..., None] / 0.5),
        colors[1] + (colors[2] - colors[1]) * ((lum[..., None] - 0.5) / 0.5),
    )
    if cyan_top:
        cutoff = np.quantile(lum[alpha > 0], 1 - cyan_top)
        color = np.array([int(bright_color[i:i + 2], 16) for i in (1, 3, 5)]) / 255
        out[(lum >= cutoff) & (alpha > 0)] = color
    rgba[..., :3] = np.clip(out * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def hsv(image: Image.Image) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    data = np.asarray(image.convert("HSV"), dtype=np.float32) / 255
    return data[..., 0] * 360, data[..., 1], data[..., 2]


def poacher(image: Image.Image) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA")).copy()
    h, s, v = hsv(image)
    valid = rgba[..., 3] > 0
    skin = valid & (h >= 35) & (h <= 75) & (s >= 0.24) & (v >= 0.28)
    rgb = rgba[..., :3].astype(np.float32) / 255
    lum = luminance(rgb)
    colors = np.array([[0x3B, 0x2A, 0x1E], [0xB8, 0x91, 0x3A]], dtype=np.float32) / 255
    t = np.clip(lum, 0, 1)[..., None]
    out = colors[0] + (colors[1] - colors[0]) * t
    replace = valid & ~skin
    rgba[..., :3][replace] = np.clip(out[replace] * 255, 0, 255).astype(np.uint8)
    threshold = np.quantile(lum[replace], 0.997) if replace.any() else 2
    rgba[..., :3][replace & (lum >= threshold)] = (95, 243, 255)
    return Image.fromarray(rgba, "RGBA")


def vessa(image: Image.Image) -> Image.Image:
    rgba = np.asarray(image.convert("RGBA")).copy()
    h, s, value = hsv(image)
    valid = rgba[..., 3] > 0
    skin = valid & (h >= 20) & (h <= 100) & (s >= 0.2) & (value >= 0.18)
    noise = np.random.default_rng(1937).integers(0, 256, size=skin.shape, dtype=np.uint8)
    clustered = np.asarray(Image.fromarray(noise).filter(ImageFilter.GaussianBlur(4)), dtype=np.uint8)
    threshold = np.quantile(clustered[skin], 0.85) if skin.any() else 256
    patches = skin & (clustered >= threshold)
    original = rgba[..., :3].astype(np.float32)
    original[patches] = original[patches] * 0.25 + np.array([95, 243, 255], dtype=np.float32) * 0.75
    rgba[..., :3] = np.clip(original, 0, 255).astype(np.uint8)
    facets = np.argwhere(skin & (value >= np.quantile(value[skin], 0.98))) if skin.any() else []
    for y, x in facets[::max(1, len(facets) // 8)][:8]:
        rgba[y, x, :3] = (95, 243, 255)
    return Image.fromarray(rgba, "RGBA")


def retint(reference: Path, common: Path) -> list[Path]:
    source = reference / "Common"
    output = []
    for name, (_, texture_path) in MOBS.items():
        image = Image.open(source / texture_path).convert("RGBA")
        if name == "Glimmer_Wisp":
            result = gradient(image, "#0b2a33", "#1f7f8f", "#5ff3ff", 0.10, "#d8ffff")
        elif name == "Sparkjaw":
            result = gradient(image, "#3a1d12", "#8a4b22", "#c9a24a", 0.08)
        elif name == "Riftmoth":
            result = gradient(image, "#140821", "#6a2a9a", "#5ff3ff")
        elif name == "Goblin_Poacher":
            result = poacher(image)
        else:
            result = vessa(image)
        if result.size != image.size or list(result.getchannel("A").getdata()) != list(image.getchannel("A").getdata()):
            raise ValueError(f"retint changed dimensions or alpha for {name}")
        path = common / "NPC" / "Craftory" / name / "Texture.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        result.save(path, optimize=True)
        output.append(path)
    return output


if __name__ == "__main__":
    import os

    ref = Path(os.environ["CRAFTORY_VANILLA_REF"]).expanduser()
    root = Path(__file__).resolve().parents[2] / "craftory-tech/src/main/resources/Common"
    print(f"wrote {len(retint(ref, root))} mob textures")
