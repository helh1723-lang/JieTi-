"""Build a platform-only verifier for a minified personal APK. No user data is read here."""
import argparse
import pathlib
import subprocess
import zipfile


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--sdk', default=os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT')))
    p.add_argument('--jdk', default=os.environ.get('JAVA_HOME'))
    a = p.parse_args()
    project = pathlib.Path(__file__).resolve().parents[1]
    root = project / '.runtime/performance-023/platform-check'
    root.mkdir(parents=True, exist_ok=True)
    classes, dex = root/'classes', root/'dex'
    classes.mkdir(exist_ok=True); dex.mkdir(exist_ok=True)
    jdk, sdk = pathlib.Path(a.jdk)/'bin', pathlib.Path(a.sdk)
    bt, android = sdk/'build-tools/35.0.0', sdk/'platforms/android-35/android.jar'

    def run(*cmd):
        subprocess.run([str(x) for x in cmd], check=True)

    run(jdk/'javac.exe', '-source', '8', '-target', '8', '-bootclasspath', android,
        '-encoding', 'UTF-8', '-d', classes, project/'scripts/android-retention/Check.java')
    run(jdk/'jar.exe', 'cf', root/'check.jar', '-C', classes, '.')
    run(bt/'d8.bat', '--lib', android, '--min-api', '31', '--output', dex, root/'check.jar')
    run(bt/'aapt2.exe', 'link', '-o', root/'base.apk', '--manifest',
        project/'scripts/android-retention/AndroidManifest.xml', '-I', android)
    with zipfile.ZipFile(root/'base.apk', 'a') as apk:
        apk.write(dex/'classes.dex', 'classes.dex')
    run(bt/'zipalign.exe', '-f', '4', root/'base.apk', root/'aligned.apk')
    # Android's standard local Debug key, same personal signing boundary as the tablet variant.
    run(bt/'apksigner.bat', 'sign', '--ks', pathlib.Path.home()/'.android/debug.keystore',
        '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass',
        'pass:android', '--out', root/'check.apk', root/'aligned.apk')
    print(root/'check.apk')


if __name__ == '__main__':
    main()
