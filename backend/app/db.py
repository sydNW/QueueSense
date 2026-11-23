import os
from pathlib import Path

import psycopg
from dotenv import load_dotenv

# Reads backend/.env (not committed to Git).
load_dotenv(Path(__file__).resolve().parent.parent / ".env")

DATABASE_URL = os.environ.get("DATABASE_URL")


def get_connection():
    if not DATABASE_URL:
        raise RuntimeError("DATABASE_URL is not set. Create backend/.env (see .env.example).")
    return psycopg.connect(DATABASE_URL)