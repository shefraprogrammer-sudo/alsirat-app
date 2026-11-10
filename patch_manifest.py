import re
p = 'android/app/src/main/AndroidManifest.xml'
s = open(p, encoding='utf-8').read()
if 'AdhanService' in s:
    print('manifest already patched'); raise SystemExit
perms = open('native/manifest-permissions.xml', encoding='utf-8').read()
comps = open('native/manifest-components.xml', encoding='utf-8').read()
s = re.sub(r'(<manifest[^>]*>)', lambda m: m.group(1) + '\n' + perms, s, count=1)
s = s.replace('</application>', comps + '\n    </application>', 1)
open(p, 'w', encoding='utf-8').write(s)
print('manifest patched')
