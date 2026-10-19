"""Where a trial's time went, from OpenCode's own session store (no watching needed).

Read-Trial.ps1 covers what the kit saw (agent.ps1 calls, checks). This covers the model side: time spent generating,
output tokens and what they were (thinking, file writes, text, tool calls), compactions, answers cut at the output limit,
tool-call errors (edit failures), repeated reads and user messages. Use it to tell setup problems from kit problems.

Usage: python -I qa/local-model/Read-OpenCode.py <trial folder> [--db <opencode.db>]
The database is copied to a temporary folder and opened read-only; only session tables are read.
"""
import argparse
import datetime
import json
import os
import shutil
import sqlite3
import sys
import tempfile


def norm(path):
    return os.path.normcase(os.path.normpath(os.path.abspath(path)))


def summarize(rows):
    msgs = [(kind, json.loads(data)) for kind, data in rows]
    assistant = [m for kind, m in msgs if kind == 'assistant']
    times = [m['time']['created'] for _, m in msgs if 'time' in m and 'created' in m['time']]
    stats = {
        'wall': (max(times) - min(times)) / 60000 if times else 0,
        'model': sum(((m['time'].get('completed') or m['time']['created']) - m['time']['created']) for m in assistant) / 60000,
        'turns': len(assistant),
        'users': sum(1 for kind, _ in msgs if kind == 'user'),
        'output': sum(m.get('tokens', {}).get('output', 0) for m in assistant),
        'big_turns': sum(1 for m in assistant if m.get('tokens', {}).get('output', 0) > 4000),
        'cut': sum(1 for m in assistant if m.get('finish') == 'length'),
        'compactions': sum(1 for kind, m in msgs if kind == 'compaction' and m.get('status') != 'failed'),
        'failed': sum(1 for kind, m in msgs if kind == 'compaction' and m.get('status') == 'failed'),
        'chars': {'thinking': 0, 'file writes': 0, 'text': 0, 'tool calls': 0},
        'tools': {}, 'errors': {}, 'repeats': 0,
    }
    seen = {}
    for m in assistant:
        for part in m.get('content', []):
            kind = part.get('type')
            if kind == 'reasoning':
                stats['chars']['thinking'] += len(part.get('text', ''))
            elif kind == 'text':
                stats['chars']['text'] += len(part.get('text', ''))
            elif kind == 'tool':
                name = part.get('name', '?')
                state = part.get('state', {})
                call = json.dumps(state.get('input', {}), sort_keys=True)
                stats['chars']['file writes' if name in ('write', 'edit', 'patch') else 'tool calls'] += len(call)
                stats['tools'][name] = stats['tools'].get(name, 0) + 1
                if state.get('status') == 'error':
                    stats['errors'][name] = stats['errors'].get(name, 0) + 1
                if name in ('read', 'shell', 'bash'):
                    seen[call] = seen.get(call, 0) + 1
    stats['repeats'] = sum(n - 1 for n in seen.values() if n > 1)
    return stats


def report(label, s):
    total = sum(s['chars'].values()) or 1
    mix = ', '.join('%s %d%%' % (k, round(100 * v / total)) for k, v in s['chars'].items())
    rate = s['output'] / (s['model'] * 60) if s['model'] else 0
    errors = ', '.join('%s %d of %d' % (k, v, s['tools'].get(k, 0)) for k, v in sorted(s['errors'].items())) or 'none'
    print('%s: %.0f min wall, %.0f min generating (%.0f%%), %d model turns, %d user messages'
          % (label, s['wall'], s['model'], 100 * s['model'] / s['wall'] if s['wall'] else 0, s['turns'], s['users']))
    print('  output: %dk tokens (%.0f tok/s while generating); %s' % (s['output'] / 1000, rate, mix))
    print('  compactions %d (failed %d); answers cut at the output limit %d; turns over 4k tokens %d; repeated identical reads %d'
          % (s['compactions'], s['failed'], s['cut'], s['big_turns'], s['repeats']))
    print('  tool errors: %s' % errors)


def log_span(trial):
    path = os.path.join(trial, 'reports', 'agent-log.jsonl')
    if not os.path.isfile(path):
        return None
    times = []
    with open(path, encoding='utf-8-sig') as f:
        for line in f:
            try:
                stamp = json.loads(line)['time'].replace('Z', '+00:00')
                times.append(datetime.datetime.fromisoformat(stamp).timestamp() * 1000)
            except (ValueError, KeyError):
                pass
    return (min(times), max(times)) if times else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('trial')
    parser.add_argument('--session-dir', help='print the project folder OpenCode recorded for this session id and exit')
    parser.add_argument('--db', default=os.path.join(os.path.expanduser('~'), '.local', 'share', 'opencode', 'opencode.db'))
    args = parser.parse_args()
    if not os.path.isfile(args.db):
        print('opencode: no session store at %s (pass --db)' % args.db)
        return 0
    trial = norm(args.trial)
    with tempfile.TemporaryDirectory() as tmp:
        # A copy with its write-ahead log: the live file may be in use, and nothing here should ever write to it.
        for suffix in ('', '-wal', '-shm'):
            if os.path.isfile(args.db + suffix):
                shutil.copyfile(args.db + suffix, os.path.join(tmp, 'opencode.db' + suffix))
        db = sqlite3.connect(os.path.join(tmp, 'opencode.db'))
        try:
            if args.session_dir:
                found = db.execute('select directory from session_v2 where id = ?', (args.session_dir,)).fetchone()
                print(found[0] if found else '')
                return 0
            rows = db.execute('select id, directory, time_created from session_v2 order by time_created').fetchall()
            sessions = [(sid, d) for sid, d, _ in rows if norm(d) == trial]
            if not sessions:
                # A renamed folder: take the sessions that started during the trial's own agent.ps1 log.
                span = log_span(args.trial)
                if span:
                    sessions = [(sid, d) for sid, d, t in rows if span[0] - 3 * 60000 <= t <= span[1]]
                    if sessions:
                        print('opencode: folder renamed; matched by time to %s' % ', '.join(sorted(set(os.path.basename(d) for _, d in sessions))))
            if not sessions:
                print('opencode: no session for %s' % args.trial)
                return 0
            all_rows = []
            for n, (sid, _) in enumerate(sessions, 1):
                rows = db.execute('select type, data from session_message where session_id = ? order by seq', (sid,)).fetchall()
                all_rows += rows
                if len(sessions) > 1:
                    report('opencode session %d of %d' % (n, len(sessions)), summarize(rows))
            report('opencode total' if len(sessions) > 1 else 'opencode', summarize(all_rows))
        finally:
            db.close()
    return 0


if __name__ == '__main__':
    sys.exit(main())
