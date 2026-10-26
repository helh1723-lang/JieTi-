import os
"""Same-data tablet navigation sample. UI XML stays in memory; no learning text is saved."""
import argparse
import collections
import csv
import io
import json
import re
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree as ET


def parse_frames(raw):
    summary = {}
    for key, pattern in {
        'total': r'Total frames rendered:\s*(\d+)',
        'janky': r'Janky frames:\s*(\d+)',
        'p95Ms': r'95th percentile:\s*(\d+)ms',
        'p99Ms': r'99th percentile:\s*(\d+)ms',
        'slowUi': r'Number Slow UI thread:\s*(\d+)',
        'gpuP95Ms': r'95th gpu percentile:\s*(\d+)ms',
    }.items():
        found = re.search(pattern, raw)
        summary[key] = int(found[1]) if found else None
    flags = collections.Counter()
    timings = []
    for block in raw.split('---PROFILEDATA---')[1::2]:
        for row in csv.DictReader(io.StringIO(block.strip())):
            try:
                flag = int(row['Flags'])
                flags[str(flag)] += 1
                start, end = int(row['IntendedVsync']), int(row['FrameCompleted'])
                if flag == 0 and 0 < start < end < 2**63-1:
                    timings.append((end-start)/1e6)
            except (KeyError, ValueError, TypeError):
                continue
    timings.sort()
    summary.update(validSamples=len(timings), flags=dict(flags),
                   over100Ms=sum(t > 100 for t in timings) if timings else None,
                   maxMs=round(max(timings), 2) if timings else None,
                   rowP95Ms=round(timings[min(len(timings)-1, int(len(timings)*.95))], 2) if timings else None,
                   rowTimingsStatus='available' if timings else 'unavailable - use gfxinfo aggregate; never zero jank')
    return summary


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--serial', required=True)
    p.add_argument('--port', default='5037')
    p.add_argument('--label', required=True)
    p.add_argument('--trace', action='store_true')
    p.add_argument('--rapid-only', action='store_true', help='Fixed coordinate taps; no hierarchy dump during the measured course switches')
    p.add_argument('--coach-tap', help='Observed x,y coordinates; supply all three taps to avoid idle-waiting preflight dumps')
    p.add_argument('--math-tap')
    p.add_argument('--signals-tap')
    a = p.parse_args()
    out = Path('docs/evidence/performance-023') / a.label
    out.mkdir(parents=True, exist_ok=True)
    prefix = [os.environ.get('ADB', 'adb'), '-P', a.port, '-s', a.serial]
    supplied = {}
    if a.coach_tap or a.math_tap or a.signals_tap:
        assert a.coach_tap and a.math_tap and a.signals_tap and a.rapid_only
        supplied = {title: tuple(map(int, value.split(','))) for title,value in [('学习规划',a.coach_tap),('数学',a.math_tap),('信号与系统',a.signals_tap)]}

    def adb(*args, timeout=45):
        return subprocess.check_output(prefix+list(args), timeout=timeout).decode('utf8', 'replace')

    def nodes():
        adb('shell', 'uiautomator', 'dump', '/sdcard/jieti-perf-ui.xml')
        xml = adb('shell', 'cat', '/sdcard/jieti-perf-ui.xml')
        adb('shell', 'rm', '-f', '/sdcard/jieti-perf-ui.xml')
        return list(ET.fromstring(xml).iter('node'))

    def find(text):
        return next((n for n in nodes() if n.get('text') == text or n.get('text') == '✓ '+text), None)

    def click(text):
        if text in supplied:
            x,y = supplied[text]
            adb('shell', 'input', 'tap', str(x), str(y))
            return
        n = find(text)
        if n is None:
            raise AssertionError('Navigation control not visible: '+text)
        l, t, r, b = map(int, re.findall(r'\d+', n.get('bounds')))
        adb('shell', 'input', 'tap', str((l+r)//2), str((t+b)//2))

    package_info = adb('shell', 'dumpsys', 'package', 'cn.jieti')
    report = {'label': a.label, 'device': adb('shell', 'getprop', 'ro.product.model').strip(),
              'android': adb('shell', 'getprop', 'ro.build.version.release').strip(),
              'method': 'real database and existing auto settings; aggregate gfxinfo; hierarchy waits are not rendering latency',
              'package': '\n'.join(x.strip() for x in package_info.splitlines() if 'versionName=' in x or 'versionCode=' in x),
              'debuggable': bool(re.search(r'pkgFlags=\[[^\]]*DEBUGGABLE', package_info)),
              'preflight': 'observed coordinates' if supplied else 'UI hierarchy',
              'windows': {}}
    adb('shell', 'am', 'force-stop', 'cn.jieti')
    launch = adb('shell', 'am', 'start', '-W', '-n', 'cn.jieti/.MainActivity')
    m = re.search(r'TotalTime:\s*(\d+)', launch)
    report['launchTotalMs'] = int(m[1]) if m else None
    time.sleep(2)
    click('学习规划')
    time.sleep(2)
    click('数学')
    time.sleep(2)
    coordinates = {title: supplied[title] for title in ['数学','信号与系统']} if supplied else {}
    if a.rapid_only and not supplied:
        for n in nodes():
            title = n.get('text', '').removeprefix('✓ ')
            if title in ['数学', '信号与系统']:
                l,t,r,b = map(int, re.findall(r'\d+', n.get('bounds')))
                coordinates[title] = ((l+r)//2, (t+b)//2)
        assert len(coordinates) == 2, 'Both subject controls must be visible'
    if a.trace:
        trace_start = adb('shell', 'perfetto', '--background', '-o', '/data/misc/perfetto-traces/jieti-coach.perfetto-trace', '-t', '25s', '-a', 'cn.jieti', 'sched', 'freq', 'idle', 'am', 'wm', 'gfx', 'view', 'binder_driver', 'dalvik', 'input')
        report['perfettoStart'] = trace_start.strip()
        trace_at = time.monotonic()

    def sample(label, action):
        adb('shell', 'dumpsys', 'gfxinfo', 'cn.jieti', 'reset')
        action()
        time.sleep(1)
        raw = adb('shell', 'dumpsys', 'gfxinfo', 'cn.jieti', 'framestats')
        (out / (label+'-gfx.txt')).write_text(raw, encoding='utf8')
        report['windows'][label] = parse_frames(raw)
        print(json.dumps({label: report['windows'][label]}, ensure_ascii=False), flush=True)

    def switches():
        # Identical operation count and settling interval in each build.
        for _ in range(3):
            click('信号与系统'); time.sleep(.5)
            click('数学'); time.sleep(.5)

    if a.rapid_only:
        def rapid_switches():
            for _ in range(3):
                for title in ['信号与系统', '数学']:
                    x,y = coordinates[title]
                    adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(.7)
        sample('rapidCourses', rapid_switches)
        report['method'] = 'real database and existing auto settings; 6 alternating course taps with 700ms settling, no UI hierarchy dump inside measured window; gfxinfo aggregate'
        if a.trace:
            time.sleep(max(0, 26-(time.monotonic()-trace_at)))
            adb('pull', '/data/misc/perfetto-traces/jieti-coach.perfetto-trace', str(out / 'coach.perfetto-trace'))
            adb('shell', 'rm', '-f', '/data/misc/perfetto-traces/jieti-coach.perfetto-trace')
        (out / 'results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
        print(json.dumps(report, ensure_ascii=False), flush=True)
        return

    sample('courses', switches)
    click('信号与系统'); time.sleep(1)
    # Narrow layouts expose the directory through a button; wide layouts show it directly.
    def chapters():
        for _ in range(3):
            for title in ['线性时不变系统的系统描述和系统响应', '信号与系统的基本概念']:
                if find(title) is None:
                    entry = next((n.get('text') for n in nodes() if n.get('text', '').startswith('课程目录 ·')), None)
                    if entry: click(entry)
                click(title); time.sleep(.5)
    try:
        sample('chapters', chapters)
    except AssertionError as e:
        report['chapterStatus'] = str(e)
    if a.trace:
        time.sleep(max(0, 26-(time.monotonic()-trace_at)))
        adb('pull', '/data/misc/perfetto-traces/jieti-coach.perfetto-trace', str(out / 'coach.perfetto-trace'))
        adb('shell', 'rm', '-f', '/data/misc/perfetto-traces/jieti-coach.perfetto-trace')
    adb('shell', 'dumpsys', 'gfxinfo', 'cn.jieti', 'reset')
    report['battery'] = '\n'.join(x.strip() for x in adb('shell', 'dumpsys', 'battery').splitlines() if any(k in x for k in ['level:', 'temperature:', 'status:']))
    (out / 'results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    main()
