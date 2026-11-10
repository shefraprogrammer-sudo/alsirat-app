import { Capacitor, registerPlugin } from '@capacitor/core';
import { LocalNotifications } from '@capacitor/local-notifications';
import { Geolocation } from '@capacitor/geolocation';
import { Coordinates, CalculationMethod, PrayerTimes } from 'adhan';

const Adhan = registerPlugin('Adhan');
const native = Capacitor.isNativePlatform();
const KEY = 'alsirat.adhan.v1';
const PRAYERS = [['fajr','الفجر'],['dhuhr','الظهر'],['asr','العصر'],['maghrib','المغرب'],['isha','العشاء']];
const METHODS = {
  Egyptian: 'الهيئة المصرية العامة للمساحة',
  UmmAlQura: 'أم القرى',
  MuslimWorldLeague: 'رابطة العالم الإسلامي',
  Karachi: 'كراتشي',
  Dubai: 'دبي',
  Kuwait: 'الكويت',
  Qatar: 'قطر'
};
const DEFAULTS = {
  enabled: false, lat: 30.0444, lng: 31.2357, placeName: 'القاهرة (افتراضي)',
  method: 'Egyptian',
  prayers: { fajr: true, dhuhr: true, asr: true, maghrib: true, isha: true }
};

function load() {
  try { return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) || '{}') }; }
  catch (e) { return { ...DEFAULTS }; }
}
let s = load();
const save = () => { try { localStorage.setItem(KEY, JSON.stringify(s)); } catch (e) {} };

function buildTimes(days = 30) {
  const out = [], now = Date.now();
  const coords = new Coordinates(s.lat, s.lng);
  const params = CalculationMethod[s.method]();
  for (let d = 0; d < days; d++) {
    const date = new Date(); date.setDate(date.getDate() + d);
    const pt = new PrayerTimes(coords, date, params);
    for (const [k, ar] of PRAYERS) {
      if (!s.prayers[k]) continue;
      const t = pt[k].getTime();
      if (t > now) out.push({ key: k, name: ar, time: t });
    }
  }
  return out;
}

async function refresh() {
  if (!native) return;
  if (!s.enabled) { await Adhan.cancel(); return; }
  await Adhan.schedule({ times: buildTimes(30) });
}

async function locate() {
  try {
    await Geolocation.requestPermissions();
    const pos = await Geolocation.getCurrentPosition({ timeout: 15000, enableHighAccuracy: false });
    s.lat = pos.coords.latitude; s.lng = pos.coords.longitude; s.placeName = 'موقعك الحالي';
    save();
    return true;
  } catch (e) { return false; }
}

const fmt = (t) => new Date(t).toLocaleString('ar-EG', { weekday: 'short', hour: '2-digit', minute: '2-digit' });

function injectUI() {
  const css = document.createElement('style');
  css.textContent = `
  #adh-fab{position:fixed;left:14px;bottom:calc(14px + env(safe-area-inset-bottom));z-index:99998;width:52px;height:52px;border-radius:50%;border:0;background:#c9a24b;color:#0b1d26;font-size:24px;box-shadow:0 4px 14px rgba(0,0,0,.4)}
  #adh-ov{position:fixed;inset:0;z-index:99999;background:rgba(0,0,0,.6);display:none;align-items:flex-end;justify-content:center;direction:rtl;font-family:inherit}
  #adh-ov.on{display:flex}
  #adh-box{background:#0b1d26;color:#f1e9d2;width:100%;max-width:520px;max-height:88vh;overflow:auto;border-radius:18px 18px 0 0;padding:18px 18px calc(18px + env(safe-area-inset-bottom));border:1px solid #c9a24b55}
  #adh-box h3{margin:0 0 12px;color:#c9a24b}
  .adh-row{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:10px 0;border-bottom:1px solid #ffffff14}
  .adh-b{background:#c9a24b;color:#0b1d26;border:0;border-radius:10px;padding:8px 12px;font-weight:700;font-family:inherit}
  .adh-b.g{background:#ffffff1a;color:#f1e9d2}
  .adh-ok{color:#5fd38d}.adh-no{color:#ff7b7b}
  #adh-box select{background:#13303d;color:#f1e9d2;border:1px solid #c9a24b55;border-radius:8px;padding:6px;font-family:inherit}
  .adh-small{font-size:.85em;opacity:.75;margin-top:8px}`;
  document.head.appendChild(css);

  const fab = document.createElement('button');
  fab.id = 'adh-fab'; fab.textContent = '🕌'; fab.setAttribute('aria-label', 'الأذان');
  const ov = document.createElement('div'); ov.id = 'adh-ov';
  ov.innerHTML = '<div id="adh-box"></div>';
  document.body.append(fab, ov);
  fab.onclick = () => { ov.classList.add('on'); render(); };
  ov.onclick = (e) => { if (e.target === ov) ov.classList.remove('on'); };
  ov.addEventListener('click', onAction);
}

async function render() {
  const box = document.getElementById('adh-box');
  let st = { notifications: false, batteryIgnored: false, manufacturer: '' };
  try { st = await Adhan.getStatus(); } catch (e) {}
  const next = s.enabled ? buildTimes(2)[0] : null;
  const tick = (ok) => ok ? '<span class="adh-ok">✔ مفعّل</span>' : '<span class="adh-no">✖ غير مفعّل</span>';
  const oem = ['xiaomi','redmi','poco','oppo','realme','vivo','huawei','honor','samsung','oneplus'].some(m => st.manufacturer.includes(m));

  box.innerHTML = `
    <h3>🕌 الأذان والتنبيهات</h3>
    <div class="adh-row"><b>تشغيل الأذان</b>
      <button class="adh-b" data-act="toggle">${s.enabled ? 'إيقاف' : 'تفعيل'}</button></div>
    ${next ? `<div class="adh-row"><span>الصلاة القادمة</span><b>${next.name} — ${fmt(next.time)}</b></div>` : ''}
    <div class="adh-row"><span>الإشعارات</span>${tick(st.notifications)}
      ${st.notifications ? '' : '<button class="adh-b" data-act="notif">السماح</button>'}</div>
    <div class="adh-row"><span>توفير البطارية</span>${st.batteryIgnored ? '<span class="adh-ok">✔ بدون قيود</span>' : '<span class="adh-no">✖ قد يمنع الأذان</span>'}
      ${st.batteryIgnored ? '' : '<button class="adh-b" data-act="battery">استثناء</button>'}</div>
    ${oem ? `<div class="adh-row"><span>التشغيل التلقائي (${st.manufacturer})</span><button class="adh-b g" data-act="autostart">فتح الإعدادات</button></div>` : ''}
    <div class="adh-row"><span>الموقع: ${s.placeName}</span><button class="adh-b g" data-act="locate">تحديث</button></div>
    <div class="adh-row"><span>طريقة الحساب</span>
      <select data-act="method">${Object.entries(METHODS).map(([k, v]) => `<option value="${k}" ${k === s.method ? 'selected' : ''}>${v}</option>`).join('')}</select></div>
    ${PRAYERS.map(([k, ar]) => `<div class="adh-row"><span>أذان ${ar}</span>
      <input type="checkbox" data-act="pray" data-k="${k}" ${s.prayers[k] ? 'checked' : ''}></div>`).join('')}
    <div class="adh-row"><span>تجربة صوت الأذان</span><button class="adh-b g" data-act="test">تشغيل</button></div>
    <div class="adh-small">لضمان عمل الأذان والتطبيق مغلق: فعّل الإشعارات، واستثنِ التطبيق من توفير البطارية، وفعّل التشغيل التلقائي على أجهزة شاومي/أوبو/فيفو/هواوي.</div>`;
  box.querySelector('select[data-act="method"]').onchange = async (e) => { s.method = e.target.value; save(); await refresh(); render(); };
}

async function onAction(e) {
  const el = e.target.closest('[data-act]'); if (!el || el.tagName === 'SELECT') return;
  const act = el.dataset.act;
  try {
    if (act === 'toggle') {
      if (!s.enabled) {
        await LocalNotifications.requestPermissions();
        await locate();
        s.enabled = true;
      } else s.enabled = false;
      save(); await refresh();
    } else if (act === 'notif') await LocalNotifications.requestPermissions();
    else if (act === 'battery') await Adhan.openBatterySettings();
    else if (act === 'autostart') await Adhan.openAutostart();
    else if (act === 'locate') { await locate(); await refresh(); }
    else if (act === 'test') await Adhan.test();
    else if (act === 'pray') { s.prayers[el.dataset.k] = el.checked; save(); await refresh(); return; }
    else return;
  } catch (err) { console.error(err); }
  render();
}

if (native) {
  const start = () => { injectUI(); refresh(); };
  document.readyState === 'loading' ? document.addEventListener('DOMContentLoaded', start) : start();
  document.addEventListener('visibilitychange', () => { if (!document.hidden) { refresh(); } });
}
