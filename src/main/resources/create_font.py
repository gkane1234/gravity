#!/usr/bin/env python3
"""
Creates a simple bitmap font PNG for testing purposes.
This creates a 128x128 pixel PNG with basic ASCII characters.
"""

from PIL import Image, ImageDraw, ImageFont
import os

def create_simple_font():
    # Font parameters
    char_width = 8
    char_height = char_width*2
    chars_per_row = 16
    num_rows = 8
    outline_width = 1
    
    # Image dimensions
    img_width = char_width * chars_per_row
    img_height = char_height * num_rows
    
    # Create image with transparent background
    img = Image.new('RGBA', (img_width, img_height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    fill_color = (0, 0, 0, 255)
    outline_color = (255, 255, 255, 255)

    char_range = range(32, 127)
    largest_possible_font_size = char_width*2
    too_large = True
    while too_large:
        too_large = False
        font = ImageFont.truetype("consola.ttf", largest_possible_font_size)
        for char_code in char_range:
            char = chr(char_code)
            char_w = draw.textlength(char, font=font)+2*outline_width
            char_h = draw.textlength(char, font=font)+2*outline_width
            if char_w > char_width or char_h > char_height:
                largest_possible_font_size -= 1
                too_large = True
                break
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
                x = col * char_width
                y = row * char_height
                if char == "`":
                    y +=1
                if char == "|":
                    y+=1
                if char != ' ':
                    # Draw outline by drawing text in white at 8 surrounding positions
                    for dx in range(-outline_width, outline_width+1):
                        for dy in range(-outline_width, outline_width+1):
                            if dx != 0 or dy != 0:
                                draw.text((x + dx, y + dy), char, fill=outline_color, font=font)
                    draw.text((x, y), char, fill=fill_color, font=font)
    
    # Save the font
    img.save('C:/Users/gkane/Documents/Stuff/gravitychunk/src/main/resources/font.png')
    print(f"Created font.png ({img_width}x{img_height})")
    print(f"Character size: {char_width}x{char_height}")
    print("Characters: ASCII 32-126")

if __name__ == "__main__":
    create_simple_font()