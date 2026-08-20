# tools/migration

One-off manual migrations for LeetHelper. These are **run by hand**, not
executed by any plugin, and are intended to be run a single time per server.

## 1.4.1 single plugin -> 1.5.0 three-plugin split

The 1.4.1 plugin stored everything in `plugins/LeetHelper/data.db`. After 1.5.0
each plugin owns its own data folder, so player skill progress needs to move to
the new `plugins/LeetSkills/data.db`.

Migrate **only** skill levels (player progress) with:

```bash
python3 tools/migration/migrate_skills_1_4_1-1.5.0.py
```

Run it **from the server root** so the default paths resolve: it reads
`plugins/LeetHelper/data.db` (old) and writes `plugins/LeetSkills/data.db` (new).
Override only if needed: `--source` / `--dest` / `--dry-run`. The script:

- reads the source DB only — never modifies it;
- creates the destination DB/`kv_store` table if absent and leaves its other rows
  untouched;
- copies only the `skills`/`levels` rows, using `INSERT OR IGNORE` so re-running
  never duplicates or overwrites progress already in the destination;
- supports `--dry-run` to report what would be copied without writing.

You should run this **once**, with the server stopped, before first starting it
on 1.5.0. Then start the server and verify with `/skills`.
