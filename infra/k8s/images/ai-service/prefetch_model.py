"""Downloads the embedding model into the HF cache (the PersistentVolumeClaim) and logs the progress.

The model is not baked into the image (about 2.3 GB). The first start downloads it; later starts find it in the cache and return at once.
Usage: prefetch_model.py <repo id>
"""

import sys
import time

from huggingface_hub import snapshot_download
from huggingface_hub.utils import tqdm as hf_tqdm

# only what sentence-transformers needs: configs, tokenizer, pooling and the weights
PATTERNS = ["*.json", "*.txt", "*.model", "1_Pooling/*", "pytorch_model.bin", "model.safetensors", "sentencepiece.bpe.model"]
_last = {"t": 0.0}


class LogProgress(hf_tqdm):
    """A tqdm that writes one plain log line every few seconds instead of redrawing a bar."""

    def __init__(self, *args, **kwargs):  # type: ignore[no-untyped-def]
        kwargs["disable"] = False
        kwargs["file"] = open("/dev/null", "w")  # noqa: SIM115
        super().__init__(*args, **kwargs)

    def update(self, n=1):  # type: ignore[no-untyped-def]
        out = super().update(n)
        now = time.monotonic()
        if self.total and (now - _last["t"] > 5 or self.n >= self.total):
            _last["t"] = now
            unit = self.unit or "it"
            if unit == "B":
                print(f"embedding model download: {self.desc or 'file'}: {self.n / 1e6:.0f} / {self.total / 1e6:.0f} MB ({100 * self.n / self.total:.0f}%)", flush=True)
            else:
                print(f"embedding model download: {self.desc}: {self.n}/{self.total}", flush=True)
        return out


def main() -> None:
    repo = sys.argv[1]
    print(f"embedding model {repo}: checking the cache (the first start downloads it, about 2.3 GB, please wait)", flush=True)
    started = time.monotonic()
    path = snapshot_download(repo_id=repo, allow_patterns=PATTERNS, tqdm_class=LogProgress)
    print(f"embedding model {repo}: ready in {path} after {time.monotonic() - started:.0f}s", flush=True)


if __name__ == "__main__":
    main()
