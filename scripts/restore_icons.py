from PIL import Image, ImageDraw, ImageFont
import os

icon_dir = r"F:\ai-companion-desktop-pet\android\app\src\main\res"

sizes = {
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for folder, size in sizes.items():
    folder_path = os.path.join(icon_dir, folder)
    os.makedirs(folder_path, exist_ok=True)
    
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    margin = size // 6
    draw.rounded_rectangle(
        [margin, margin, size - margin, size - margin],
        radius=margin // 2,
        fill=(98, 0, 238)
    )
    
    cx, cy = size // 2, size // 2
    eye_size = size // 12
    draw.ellipse([cx - eye_size * 2.5, cy - eye_size, cx - eye_size * 0.5, cy + eye_size], fill=(255, 255, 255))
    draw.ellipse([cx + eye_size * 0.5, cy - eye_size, cx + eye_size * 2.5, cy + eye_size], fill=(255, 255, 255))
    
    ear_w = size // 8
    ear_h = size // 4
    draw.polygon([
        (cx - size // 3, cy - size // 3),
        (cx - size // 3 + ear_w, cy - size // 3 - ear_h),
        (cx - size // 6, cy - size // 3)
    ], fill=(255, 255, 255))
    draw.polygon([
        (cx + size // 6, cy - size // 3),
        (cx + size // 3 - ear_w, cy - size // 3 - ear_h),
        (cx + size // 3, cy - size // 3)
    ], fill=(255, 255, 255))
    
    output_path = os.path.join(folder_path, "ic_launcher.png")
    img.save(output_path, "PNG")
    print(f"Saved {output_path} ({size}x{size})")

print("Original-style icons restored!")
