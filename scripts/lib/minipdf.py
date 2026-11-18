#!/usr/bin/env python3
"""Writes a small valid PDF without any library.   minipdf.py <out.pdf> "page 1 line 1|page 1 line 2" "page 2 line 1" ...
Each argument is one page; lines inside a page are separated by |."""
import sys


def make_pdf(pages: list[list[str]]) -> bytes:
    objs: list[str] = ["", "", "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    kids = []
    for lines in pages:
        text = "\n".join(f"BT /F1 12 Tf 50 {780 - i * 16} Td ({l.replace(chr(92), '').replace('(', '').replace(')', '')[:100]}) Tj ET" for i, l in enumerate(lines))
        kids.append(len(objs) + 1)
        objs.append(f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents {len(objs) + 2} 0 R >>")
        objs.append(f"<< /Length {len(text)} >>\nstream\n{text}\nendstream")
    objs[0] = "<< /Type /Catalog /Pages 2 0 R >>"
    objs[1] = f"<< /Type /Pages /Kids [{' '.join(f'{k} 0 R' for k in kids)}] /Count {len(kids)} >>"
    out = "%PDF-1.4\n"
    offsets = []
    for i, o in enumerate(objs):
        offsets.append(len(out))
        out += f"{i + 1} 0 obj\n{o}\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n" + "".join(f"{o:010d} 00000 n \n" for o in offsets)
    out += f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n"
    return out.encode("latin-1", "replace")


if __name__ == "__main__":
    open(sys.argv[1], "wb").write(make_pdf([a.split("|") for a in sys.argv[2:]]))
