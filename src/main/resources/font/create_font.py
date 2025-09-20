"""
Creates a bitmap font atlas PNG for the UI.
"""

from PIL import Image, ImageDraw, ImageFont
import os
def create_font(font_name="consola.ttf", char_width=32, char_height=64,chars_per_row=16,num_rows=8,outline_width=2):
    
    # Image dimensions
    img_width = char_width * chars_per_row
    img_height = char_height * num_rows
    
    # Create image with transparent background
    img = Image.new('RGBA', (img_width, img_height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    fill_color = (0, 0, 0, 255)
    outline_color = (255, 255, 255, 255)
    
    
    largest_possible_font_size = calculate_largest_possible_font_size(font_name, char_width, char_height, outline_width,draw)
    font = ImageFont.truetype(font_name, largest_possible_font_size)  # Windows
    
    #Draw the characters into their cells
    for row in range(num_rows):
        for col in range(chars_per_row):
            char_code = 32 + (row * chars_per_row) + col
            if char_code <= 126:
                char = chr(char_code)
                bbox = draw.textbbox((0,0), char, font=font, stroke_width=outline_width)
                #Calculate the offset to center the character in the cell
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
                #Deal with some characters that don't fit in the cell
                if char == "`":
                    y +=1
                if char == "|":
                    y+=1
                if char != ' ':
                    draw.text((x, y), char, font=font, fill=fill_color, stroke_fill=outline_color, stroke_width=outline_width)
    
    # Save the font
    img.save(f'{font_name}.png')
    print(f"Created font.png ({img_width}x{img_height})")
    print(f"Character size: {char_width}x{char_height}")
    print("Characters: ASCII 32-126")

def calculate_largest_possible_font_size(font_name, char_width, char_height, outline_width,draw):
    char_range = range(32, 127)
    largest_possible_font_size = char_width*2
    too_large = True
    while too_large:
        too_large = False
        font = ImageFont.truetype(font_name, largest_possible_font_size)
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
    return largest_possible_font_size
if __name__ == "__main__":
    create_font()