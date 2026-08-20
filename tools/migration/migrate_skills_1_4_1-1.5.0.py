#!/usr/bin/env python3
"""One-off manual migration: 1.4.1 single-plugin -> 1.5.0 three-plugin split.

Copies ONLY player skill progress from the old LeetHelper SQLite DB into the new
      LeetSkills DB. Everything else (feature toggles, XP trackers, crafting
state) is intentionally NOT migrated -- the split keeps each plugin's data in
its own data folder, and only skills need to survive so players keep their
spent points.

Storage model (identical schema on both sides):
    kv_store(feature_id TEXT, key TEXT, uuid TEXT, value TEXT, updated_at INTEGER)
Skills live in the row feature_id='skills', key='levels', where value is the
Gson JSON map {skill_id: level,...} for that player UUID.

Run it ONCE from the server root (so the default data folders resolve), before
first starting the server on 1.5.0:

    python3 tools/migration/migrate_skills_1_4_1-1.5.0.py

Defaults resolve relative to the working directory:
    source: plugins/LeetHelper/data.db
    dest:   plugins/LeetSkills/data.db
Override only if needed, e.g.:
    python3 tools/migration/migrate_skills_1_4_1-1.5.0.py --dry-run

Behaviour / safety:
  * Idempotent and non-destructive. Uses INSERT OR IGNORE keyed on the row's
    primary key (feature_id,key,uuid), so:
        - the first run copies every skill row over;
        - re-running copies nothing twice;
        - progress already present in the dest is never overwritten.
  * The old DB is read, never modified.
  * The dest DB is created (with the kv_store table) if it does not exist, and
    its OTHER rows are left untouched.
  * Dry-run mode (--dry-run) reports what WOULD be copied without writing.

Requires python3 with the stdlib sqlite3 module.
"""
import argparse
import os
import sqlite3
import sys

SKILLS_FEATURE = "skills"
SKILLS_KEY = "levels"


def copy_skills(source, dest, dry_run):
    if not os.path.isfile(source):
        sys.exit(f"error: source DB not found: {source}")

    src = sqlite3.connect(source)
    try:
        rows = src.execute(
            "SELECT feature_id, key, uuid, value, updated_at "
            "FROM kv_store "
            "WHERE feature_id=? AND key=?",
            (SKILLS_FEATURE, SKILLS_KEY),
        ).fetchall()
    finally:
        src.close()
    if not rows:
        print("No skill rows found in the source DB -- nothing to migrate.")
        return 0

    print(f"Found {len(rows)} player skill record(s) in {source}.")

    dest_parent = os.path.dirname(dest)
    if dest_parent and not os.path.isdir(dest_parent):
        sys.exit(f"error: dest directory does not exist: {dest_parent}")

    before = _count_skill_rows(dest)
    dst = sqlite3.connect(dest)
    try:
        dst.execute(
            "CREATE TABLE IF NOT EXISTS kv_store ("
            " feature_id TEXT NOT NULL,"
            " key TEXT NOT NULL,"
            " uuid TEXT NOT NULL,"
            " value TEXT,"
            " updated_at INTEGER,"
            " PRIMARY KEY (feature_id, key, uuid))"
        )
        for feature_id, key, uuid, value, updated_at in rows:
            if dry_run:
                continue
            dst.execute(
                "INSERT OR IGNORE INTO kv_store"
                " (feature_id, key, uuid, value, updated_at)"
                " VALUES (?, ?, ?, ?, ?)",
                (feature_id, key, uuid, value, updated_at),
            )
        if not dry_run:
            dst.commit()
    finally:
        dst.close()

    if dry_run:
        print(f"[dry-run] {len(rows)} row(s) would be copied to {dest}.")
        return 0

    after = _count_skill_rows(dest)
    inserted = after - before
    print(f"Done. Migrated {inserted} player skill record(s) into {dest}.")
    print(f"  skills/levels rows now present in dest: {after}")
    print("If the server was running, restart it, then verify with /skills.")
    return 0


def _count_skill_rows(path):
    """Number of skills/levels rows already present in a db (0 if absent)."""
    if not os.path.isfile(path):
        return 0
    conn = sqlite3.connect(path)
    try:
        return conn.execute(
            "SELECT COUNT(*) FROM kv_store WHERE feature_id=? AND key=?",
            (SKILLS_FEATURE, SKILLS_KEY),
        ).fetchone()[0]
    finally:
        conn.close()


def main():
    parser = argparse.ArgumentParser(
        description="Migrate player skill progress from the 1.4.1 LeetHelper DB "
        "to the 1.5.0 LeetSkills DB (run once, manually). Defaults resolve "
        "relative to the server root (plugins/LeetHelper/data.db and "
        "plugins/LeetSkills/data.db)."
    )
    parser.add_argument("--source", default="plugins/LeetHelper/data.db",
                        help="old LeetHelper DB (default: plugins/LeetHelper/data.db)")
    parser.add_argument("--dest", default="plugins/LeetSkills/data.db",
                        help="new LeetSkills DB (default: plugins/LeetSkills/data.db)")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="report without writing anything",
    )
    args = parser.parse_args()
    sys.exit(copy_skills(args.source, args.dest, args.dry_run))


if __name__ == "__main__":
    main()
