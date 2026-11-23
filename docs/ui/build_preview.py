"""Rebuild the saved UI preview using only Python's standard library."""

from __future__ import annotations

import hashlib
import json
import re
from html import escape
from html.parser import HTMLParser
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SOURCE_MARKER = "<!-- KITSUNE_UI_SOURCE -->"
ICON_MARKER = "/* KITSUNE_UI_ICONS */"

# Ordered source fragments. Each fragment is an exact contiguous slice of the original
# unified design. The .inc files are not standalone scripts or CSS documents.
SOURCE_PARTS = (
    "src/00-shell.html",
    "src/styles/01-foundation.css.inc",
    "src/styles/02-components.css.inc",
    "src/styles/03-pages.css.inc",
    "src/app/01-state.js.inc",
    "src/pages/02-home.js.inc",
    "src/pages/03-meals.js.inc",
    "src/pages/04-ledger.js.inc",
    "src/pages/05-settings.js.inc",
    "src/pages/06-forms.js.inc",
    "src/app/07-motion.js.inc",
    "src/app/08-render.js.inc",
    "src/app/09-events.js.inc",
)


class PreviewFrame(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.srcdoc: str | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag == "iframe" and attributes.get("id") == "codex-visualization":
            self.srcdoc = attributes.get("data-srcdoc")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    template = (ROOT / "preview.template.html").read_text(encoding="utf-8")
    parser = PreviewFrame()
    parser.feed(template)
    frame = parser.srcdoc
    if frame is None or frame.count(SOURCE_MARKER) != 1 or frame.count(ICON_MARKER) != 1:
        raise ValueError("The preview template must contain one source and one icon marker")

    source_path = ROOT / "kitsune-ui.fragment.html"
    source = "".join((ROOT / name).read_text(encoding="utf-8") for name in SOURCE_PARTS)
    # Keep the historical monolithic path as a generated compatibility artifact.
    source_path.write_text(source, encoding="utf-8", newline="\n")
    source = source.strip()
    # Use native hover titles in the exported page so tooltips need no CDN scripts.
    # The original conversation fragment remains unchanged.
    standalone_source = source.replace("data-tooltip=", "title=")
    library_path = ROOT / "vendor" / "lucide-1.8.0.min.js"
    icons = library_path.read_text(encoding="utf-8")
    icons = re.sub(r"(?m)^//# sourceMappingURL=.*$", "", icons)
    license_text = (ROOT / "vendor" / "LUCIDE-LICENSE").read_text(encoding="utf-8")
    icons = "/*\n" + license_text.replace("*/", "* /") + "\n*/\n" + icons
    icons = icons.replace("</script", "<\\/script")
    exported_frame = frame.replace(SOURCE_MARKER, standalone_source).replace(ICON_MARKER, icons)
    encoded_frame = escape(frame, quote=True)
    if template.count(encoded_frame) != 1:
        raise ValueError("Could not uniquely identify the preview frame")
    output = template.replace(encoded_frame, escape(exported_frame, quote=True), 1)
    output_path = ROOT / "index.html"
    output_path.write_text(output, encoding="utf-8", newline="\n")
    manifest = {
        "designDate": "2026-11-23",
        "source": source_path.name,
        "sourceSha256": sha256(source_path),
        "preview": output_path.name,
        "previewSha256": sha256(output_path),
        "icons": {"library": "lucide", "version": "1.8.0", "sha256": sha256(library_path)},
        "data": "fictional demonstration records; not Android application storage",
    }
    (ROOT / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    print(f"Saved {output_path}")


if __name__ == "__main__":
    main()
