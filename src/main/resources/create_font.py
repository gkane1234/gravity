#!/usr/bin/env python3
"""
Creates a simple bitmap font PNG for testing purposes.
This creates a 128x128 pixel PNG with basic ASCII characters.
"""

from PIL import Image, ImageDraw, ImageFont
import os

def create_simple_font():
    # Font parameters
    char_width = 32
    char_height = char_width*2
    chars_per_row = 16
    num_rows = 8
    outline_width = 2
    
    # Image dimensions
    img_width = char_width * chars_per_row
    img_height = char_height * num_rows
    
    # Create image with transparent background
    img = Image.new('RGBA', (img_width, img_height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    fill_color = (0, 0, 0, 255)
    outline_color = (255, 255, 255, 255)
    text_style = {"stroke_fill":outline_color,"stroke_width":outline_width}
    char_range = range(32, 127)
    largest_possible_font_size = char_width*2
    too_large = True
    while too_large:
        too_large = False
        font = ImageFont.truetype("consola.ttf", largest_possible_font_size)
        max_horizontal_offsets = [0,0]
        max_vertical_offsets = [0,0]
        for char_code in char_range:
            char = chr(char_code)
            bbox = draw.textbbox((0,0), char, font=font, stroke_width=outline_width)
            max_horizontal_offsets[0] = min(max_horizontal_offsets[0], bbox[0])
            max_horizontal_offsets[1] = max(max_horizontal_offsets[1], bbox[2])
            max_vertical_offsets[0] = min(max_vertical_offsets[0], bbox[1])
            max_vertical_offsets[1] = max(max_vertical_offsets[1], bbox[3])
            if max_horizontal_offsets[1] - max_horizontal_offsets[0] > char_width or max_vertical_offsets[1] - max_vertical_offsets[0] > char_height:
                largest_possible_font_size -= 1
                too_large = True
                break
            print(max_horizontal_offsets,max_vertical_offsets)
    print(f"Largest possible font size: {largest_possible_font_size}")
    font = ImageFont.truetype("consola.ttf", largest_possible_font_size)  # Windows
    

    # Draw a white outline around the text
    # We'll first draw the outline, then draw the text on top (so outline is behind)
    # To do this, we need to re-draw the text with a thicker stroke in white, then draw the text in black

    # First, re-draw all characters with a white outline (by drawing text offset in all directions)
    for row in range(num_rows):
        for col in range(chars_per_row):
            char_code = 32 + (row * chars_per_row) + col
            if char_code <= 126:
                char = chr(char_code)
                bbox = draw.textbbox((0,0), char, font=font, stroke_width=outline_width)
                x_offset =0
                y_offset =0
                if bbox[0] < 0:
                    x_offset = -bbox[0]
                if bbox[1] < 0:
                    y_offset = -bbox[1]
                if bbox[2] > char_width:
                    x_offset = char_width - bbox[2]
                if bbox[3] > char_height:
                    y_offset = char_height - bbox[3]
                
                x = col * (char_width)+x_offset
                y = row * (char_height)+y_offset
                if char == "`":
                    y +=1
                if char == "|":
                    y+=1
                if char != ' ':
                    draw.text((x, y), char, font=font, fill=fill_color, stroke_fill=outline_color, stroke_width=outline_width)
    
    # Save the font
    img.save('C:/Users/gkane/Documents/Stuff/gravitychunk/src/main/resources/font.png')
    print(f"Created font.png ({img_width}x{img_height})")
    print(f"Character size: {char_width}x{char_height}")
    print("Characters: ASCII 32-126")

if __name__ == "__main__":
    create_simple_font()