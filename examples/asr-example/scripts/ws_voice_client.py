#!/usr/bin/env python3
"""Minimal WebSocket client for asr-example (PCM 16kHz mono → ASR → LLM → TTS).

Usage:
  pip install websocket-client
  ffmpeg -i input.wav -ar 16000 -ac 1 -f s16le - | python ws_voice_client.py --stdin
  python ws_voice_client.py sample.wav

Requires asr-example running at ws://localhost:8080/ws/asr (override with --url).
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import wave
from pathlib import Path

try:
    import websocket
except ImportError as exc:
    raise SystemExit(
        "Missing dependency: pip install websocket-client"
    ) from exc

DEFAULT_URL = "ws://localhost:8080/ws/asr"
CHUNK_BYTES = 3200  # ~100ms @ 16kHz s16le mono
CHUNK_INTERVAL_S = 0.1  # pace PCM like live microphone input


def load_pcm(path: Path | None, use_stdin: bool) -> bytes:
    if use_stdin:
        return sys.stdin.buffer.read()
    if path is None:
        raise SystemExit("Provide a .wav file or use --stdin with ffmpeg pipe")
    with wave.open(str(path), "rb") as wf:
        if wf.getnchannels() != 1 or wf.getsampwidth() != 2 or wf.getframerate() != 16000:
            raise SystemExit(
                f"{path}: need PCM 16kHz mono 16-bit WAV. "
                "Convert: ffmpeg -i input.wav -ar 16000 -ac 1 sample.wav"
            )
        return wf.readframes(wf.getnframes())


def run(url: str, pcm: bytes, out_mp3: Path, chunk_interval_s: float = CHUNK_INTERVAL_S) -> None:
    ws = websocket.create_connection(url, timeout=120)
    try:
        connected = json.loads(ws.recv())
        print("←", connected)

        for offset in range(0, len(pcm), CHUNK_BYTES):
            ws.send_binary(pcm[offset : offset + CHUNK_BYTES])
            if chunk_interval_s > 0 and offset + CHUNK_BYTES < len(pcm):
                time.sleep(chunk_interval_s)

        ws.send("END")
        print("→ END")

        audio = bytearray()
        while True:
            frame = ws.recv()
            if isinstance(frame, bytes):
                audio.extend(frame)
                continue
            print("←", frame)
            msg = json.loads(frame)
            if msg.get("type") == "complete":
                break
            if msg.get("type") == "error":
                raise SystemExit(msg.get("message", "unknown error"))

        if audio:
            out_mp3.write_bytes(audio)
            print(f"Saved TTS audio: {out_mp3} ({len(audio)} bytes)")
    finally:
        ws.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="asr-example WebSocket test client")
    parser.add_argument("wav", nargs="?", help="16kHz mono 16-bit WAV file")
    parser.add_argument("--stdin", action="store_true", help="Read raw PCM from stdin")
    parser.add_argument("--url", default=DEFAULT_URL, help=f"WebSocket URL (default: {DEFAULT_URL})")
    parser.add_argument(
        "--chunk-interval",
        type=float,
        default=CHUNK_INTERVAL_S,
        help="Seconds between PCM chunks (default: 0.1, helps streaming ASR)",
    )
    parser.add_argument(
        "--out", default="reply.mp3", type=Path, help="Output MP3 path (default: reply.mp3)"
    )
    args = parser.parse_args()

    wav_path = Path(args.wav) if args.wav else None
    pcm = load_pcm(wav_path, args.stdin)
    if not pcm:
        raise SystemExit("No audio data")
    run(args.url, pcm, args.out, args.chunk_interval)


if __name__ == "__main__":
    main()
