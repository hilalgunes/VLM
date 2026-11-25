"""Reproducible PNG test fixtures; Python standard library only."""
from pathlib import Path
import json, struct, zlib
root = Path(__file__).resolve().parent / 'fixtures'
root.mkdir(exist_ok=True)
def image(): return bytearray([255] * (512 * 512 * 3))
def pixel(data,x,y,color):
 if 0<=x<512 and 0<=y<512: data[(y*512+x)*3:(y*512+x)*3+3]=bytes(color)
def rectangle(data,x0,y0,x1,y1,color):
 for y in range(y0,y1):
  for x in range(x0,x1):pixel(data,x,y,color)
def png(data,path):
 def chunk(tag,body):return struct.pack('>I',len(body))+tag+body+struct.pack('>I',zlib.crc32(tag+body)&0xffffffff)
 raw=b''.join(b'\x00'+data[y*1536:(y+1)*1536] for y in range(512))
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',512,512,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
data=image();rectangle(data,40,180,180,320,(220,30,30))
for cy in [150,360]:
 for y in range(cy-70,cy+71):
  for x in range(290,431):
   if (x-360)**2+(y-cy)**2<=70**2:pixel(data,x,y,(30,70,220))
png(data,root/'shapes.png')
glyphs={
'V':['10001','10001','10001','10001','10001','01010','00100'],
'L':['10000','10000','10000','10000','10000','10000','11111'],
'M':['10001','11011','10101','10101','10001','10001','10001'],
'T':['11111','00100','00100','00100','00100','00100','00100'],
'E':['11111','10000','10000','11110','10000','10000','11111'],
'S':['01111','10000','10000','01110','00001','00001','11110'],
'C':['01111','10000','10000','10000','10000','10000','01111'],
'O':['01110','10001','10001','10001','10001','10001','01110'],
'D':['11110','10001','10001','10001','10001','10001','11110'],
'4':['00010','00110','01010','10010','11111','00010','00010'],
'8':['01110','10001','10001','01110','10001','10001','01110'],
'2':['01110','10001','00001','00010','00100','01000','11111'],
'7':['11111','00001','00010','00100','01000','01000','01000'],
':':['00000','00100','00100','00000','00100','00100','00000'],
' ':['00000']*7}
def text(data,value,y,scale=7):
 x=(512-len(value)*6*scale+scale)//2
 for char in value:
  for row,line in enumerate(glyphs[char]):
   for col,bit in enumerate(line):
    if bit=='1':rectangle(data,x+col*scale,y+row*scale,x+(col+1)*scale,y+(row+1)*scale,(0,0,0))
  x+=6*scale
data=image();text(data,'VLM TEST',130);text(data,'CODE: 4827',260);png(data,root/'ocr.png')
cases=[
 {'id':'count_en','image':'shapes.png','prompt':'How many blue circles are in the image? Reply with only the number.','expected':'2'},
 {'id':'ocr_en','image':'ocr.png','prompt':'Read the four-digit code in the image. Reply with only the four digits.','expected':'4827'},
 {'id':'count_tr','image':'shapes.png','prompt':'Görselde kaç mavi daire var? Yalnızca sayıyı yaz.','expected':'2'}]
(root/'cases.json').write_text(json.dumps(cases,ensure_ascii=False,indent=2)+'\n')
