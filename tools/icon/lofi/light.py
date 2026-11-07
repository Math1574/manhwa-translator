import numpy as np
from PIL import Image
def relight(img, cx, cy, R, ambient=(0.46, 0.50, 0.80), warm=(1.08, 0.84, 0.56), up=1.25, down=0.55, gain=1.0, power=1.4):
    """Éclaire une image RGBA par une source chaude en (cx, cy) (fractions de l'image) : la lumière monte plus loin qu'elle ne descend."""
    a = np.asarray(img).astype(np.float32); h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    dx = (xx / w - cx) / R; dy = (yy / h - cy)
    dy = np.where(dy < 0, dy / (R * up), dy / (R * down))
    L = (np.clip(1 - np.hypot(dx, dy), 0, 1) ** power * gain)[..., None]
    rgb = a[..., :3] / 255.0
    a[..., :3] = np.clip(rgb * (np.array(ambient)[None, None, :] * (1 - L) + np.array(warm)[None, None, :] * L), 0, 1) * 255
    return Image.fromarray(a.astype(np.uint8))
def radial_rgba(size, cx, cy, r, color, alpha, power=1.5, squash=1.0):
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    d = np.clip(np.hypot((xx / size - cx), (yy / size - cy) / squash) / r, 0, 1)
    al = ((1 - d) ** power * alpha * 255).astype(np.uint8)
    im = Image.new("RGBA", (size, size), color + (0,)); im.putalpha(Image.fromarray(al)); return im
