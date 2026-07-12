# -*- coding: utf-8 -*-
"""
KupuProxy for exteraGram
MTProto proxy finder with self-update.
"""

from __future__ import annotations

import json
import os
import re
import socket
import threading
import time
import traceback
import weakref
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any, Dict, List, Optional, Tuple
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from base_plugin import BasePlugin, MenuItemData, MenuItemType, MethodHook
from android_utils import run_on_ui_thread
from client_utils import get_last_fragment
from ui.alert import AlertDialogBuilder
from ui.settings import Divider, Header, Input, Switch, Text

try:
    from hook_utils import get_private_field
except Exception:  # pragma: no cover
    get_private_field = None  # type: ignore

try:
    from ui.bulletin import BulletinHelper
except Exception:  # pragma: no cover
    BulletinHelper = None  # type: ignore

try:
    from java import jclass, dynamic_proxy
    from java.lang import Integer, Long
except Exception:  # pragma: no cover
    jclass = None  # type: ignore
    dynamic_proxy = None  # type: ignore
    Integer = None  # type: ignore
    Long = None  # type: ignore


# --- Plugin Metadata (AST-parsed, keep static) ---
__id__ = "kupu_proxy"
__name__ = "KupuProxy"
__description__ = (
    "Поиск и проверка MTProto-прокси (как KupuProxy Android).\n"
    "На экране «Прокси» — строка **KupuProxy** (TextCheckCell, как в Proxy Tools).\n"
    "Команды: `.kupu auto` · `.kupu chat` · `.kupu add` · `.kupu del` · `.kupu update`\n"
    "Источники: SoliSpirit, Yagami200, Kort, Argh94…\n"
    "Самообновление с GitHub."
)
__author__ = "@Kirillka645"
__version__ = "1.2.0"
__icon__ = "exteraPlugins/1"
__min_version__ = "11.9.1"
# 1.4.0 / 1.4.3.3 — ок; не требуем 1.4.3.10
__sdk_version__ = ">=1.4.0"


PLUGIN_ID = "kupu_proxy"
GITHUB_REPO = "Kirillka645/KupuProxy"
UPDATE_URL = f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest"
RAW_PLUGIN_MIRRORS = [
    f"https://raw.githubusercontent.com/{GITHUB_REPO}/main/exteragram/kupu_proxy.py",
    f"https://cdn.jsdelivr.net/gh/{GITHUB_REPO}@main/exteragram/kupu_proxy.py",
    f"https://raw.githack.com/{GITHUB_REPO}/main/exteragram/kupu_proxy.py",
    f"https://ghproxy.net/https://raw.githubusercontent.com/{GITHUB_REPO}/main/exteragram/kupu_proxy.py",
]

SOURCES: List[Dict[str, Any]] = [
    {
        "id": "solispirit",
        "name": "SoliSpirit",
        "urls": [
            "https://fastly.jsdelivr.net/gh/SoliSpirit/mtproto@master/all_proxies.txt",
            "https://raw.githack.com/SoliSpirit/mtproto/master/all_proxies.txt",
            "https://raw.githubusercontent.com/SoliSpirit/mtproto/master/all_proxies.txt",
        ],
    },
    {
        "id": "yagami",
        "name": "Yagami200",
        "urls": [
            "https://raw.githubusercontent.com/Yagami200/free-mtproto-proxies/main/all_proxies.txt",
            "https://raw.githubusercontent.com/Yagami200/free-mtproto-proxies/main/proxies.json",
            "https://cdn.jsdelivr.net/gh/Yagami200/free-mtproto-proxies@main/proxies.json",
            "https://cdn.jsdelivr.net/gh/Yagami200/free-mtproto-proxies@main/all_proxies.txt",
        ],
    },
    {
        "id": "kort",
        "name": "Kort All",
        "urls": [
            "https://cdn.jsdelivr.net/gh/kort0881/telegram-proxy-collector@main/proxy_all.txt",
            "https://raw.githack.com/kort0881/telegram-proxy-collector/main/proxy_all.txt",
            "https://raw.githubusercontent.com/kort0881/telegram-proxy-collector/main/proxy_all.txt",
        ],
    },
    {
        "id": "argh94",
        "name": "Argh94 Scraper",
        "urls": [
            "https://raw.githubusercontent.com/Argh94/telegram-proxy-scraper/main/proxy.txt",
            "https://cdn.jsdelivr.net/gh/Argh94/telegram-proxy-scraper@main/proxy.txt",
            "https://raw.githack.com/Argh94/telegram-proxy-scraper/main/proxy.txt",
        ],
    },
    {
        "id": "surfboard",
        "name": "Surfboard",
        "urls": [
            "https://cdn.jsdelivr.net/gh/Surfboardv2ray/TGProto@main/proxies.txt",
            "https://cdn.jsdelivr.net/gh/Surfboardv2ray/TGProto@main/proxies-tested.txt",
            "https://raw.githubusercontent.com/Surfboardv2ray/TGProto/main/proxies-tested.txt",
        ],
    },
    {
        "id": "aliila",
        "name": "ALIILAPRO",
        "urls": [
            "https://raw.githubusercontent.com/ALIILAPRO/MTProtoProxy/main/mtproto.txt",
            "https://cdn.jsdelivr.net/gh/ALIILAPRO/MTProtoProxy@main/mtproto.txt",
        ],
    },
]

LINK_RE = re.compile(
    r"(?:tg://proxy|https?://t\.me/proxy)\?[^\s<>'\"`)\]#,]+",
    re.IGNORECASE,
)
VERSION_RE = re.compile(r'^__version__\s*=\s*["\']([^"\']+)["\']', re.M)


def http_get(url: str, timeout: int = 18) -> str:
    req = Request(
        url,
        headers={
            "User-Agent": f"KupuProxy-exteraGram/{__version__}",
            "Accept": "*/*",
        },
    )
    with urlopen(req, timeout=timeout) as resp:  # noqa: S310
        data = resp.read()
    for enc in ("utf-8", "utf-8-sig", "latin-1"):
        try:
            return data.decode(enc)
        except Exception:
            continue
    return data.decode("utf-8", errors="ignore")


def parse_proxy_links(text: str) -> List[str]:
    found: List[str] = []
    seen = set()
    for m in LINK_RE.finditer(text or ""):
        raw = m.group(0).strip().rstrip("),];\"'")
        if raw.lower().startswith("https://t.me/proxy?"):
            raw = "tg://proxy?" + raw.split("?", 1)[1]
        elif raw.lower().startswith("http://t.me/proxy?"):
            raw = "tg://proxy?" + raw.split("?", 1)[1]
        key = raw
        if key not in seen:
            seen.add(key)
            found.append(raw)
    return found


def parse_proxy_url(url: str) -> Optional[Dict[str, Any]]:
    if not url or "proxy?" not in url:
        return None
    q = url.split("?", 1)[1]
    params: Dict[str, str] = {}
    for part in q.split("&"):
        if "=" not in part:
            continue
        k, v = part.split("=", 1)
        params[k] = v
    server = params.get("server")
    port = params.get("port")
    secret = params.get("secret")
    if not server or not port or not secret:
        return None
    try:
        port_i = int(port)
    except Exception:
        return None
    return {"server": server, "port": port_i, "secret": secret, "url": url}


def tcp_ping(server: str, port: int, timeout: float = 2.0) -> int:
    sock = None
    try:
        t0 = time.time()
        sock = socket.create_connection((server, port), timeout=timeout)
        ms = int((time.time() - t0) * 1000)
        return ms if ms > 0 else 1
    except Exception:
        return -1
    finally:
        try:
            if sock:
                sock.close()
        except Exception:
            pass


def version_tuple(v: str) -> Tuple[int, ...]:
    v = (v or "0").lstrip("vV")
    parts = []
    for p in v.split("."):
        try:
            parts.append(int(re.sub(r"\D", "", p) or "0"))
        except Exception:
            parts.append(0)
    return tuple(parts)


def is_newer(current: str, latest: str) -> bool:
    a, b = version_tuple(current), version_tuple(latest)
    n = max(len(a), len(b))
    a += (0,) * (n - len(a))
    b += (0,) * (n - len(b))
    return b > a


class KupuProxyPlugin(BasePlugin):
    def __init__(self):
        super().__init__()
        self._results: List[Dict[str, Any]] = []
        self._scanning = False
        self._lock = threading.Lock()
        self._drawer_id = None
        self._chat_id = None
        self._hooks = []
        self._ignore_native_trigger = False  # True пока сами пишем флаги
        # extra rows at END of list (после proxy_tools и нативных):
        # 0 shadow, 1 TextCheckCell KupuProxy, 2 TextSettingsCell «Сделать всё», 3 shadow
        self._KUPU_EXTRA_ROWS = 4

    # region lifecycle

    def on_plugin_load(self):
        self.log(f"KupuProxy {__version__} loaded")
        try:
            self._drawer_id = self.add_menu_item(
                MenuItemData(
                    menu_type=MenuItemType.DRAWER_MENU,
                    text="KupuProxy",
                    subtext="Строка в меню прокси",
                    icon="msg_proxy",
                    on_click=self._on_drawer_click,
                    priority=50,
                )
            )
        except Exception as e:
            self.log(f"drawer menu failed: {e}")

        try:
            self._chat_id = self.add_menu_item(
                MenuItemData(
                    menu_type=MenuItemType.CHAT_ACTION_MENU,
                    text="KupuProxy",
                    subtext="Скан / auto",
                    icon="msg_proxy",
                    on_click=self._on_drawer_click,
                    priority=20,
                )
            )
        except Exception:
            pass

        try:
            self.add_on_send_message_hook(priority=5)
        except Exception as e:
            self.log(f"add_on_send_message_hook: {e}")

        # Как proxy_tools: вшиваем строки в ListAdapter (TextCheckCell)
        self._hook_proxy_list_ui()

        if self.get_setting("auto_update", True):
            threading.Thread(target=self._bg_check_update, daemon=True).start()

    def on_plugin_unload(self):
        self._scanning = False
        self.log("KupuProxy unloaded")

    # endregion

    # region ProxyListActivity rows (как proxy_tools.plugin)

    def _pf(self, obj, name: str):
        """get_private_field с fallbacks."""
        if get_private_field is not None:
            try:
                return get_private_field(obj, name)
            except Exception:
                pass
        try:
            return getattr(obj, name, None)
        except Exception:
            return None

    def _hook_proxy_list_ui(self):
        """
        Совместимость с proxy_tools:
        - +4 строки в getItemCount (стекуется: N+11+4)
        - наши = ПОСЛЕДНИЕ 4 pos
        - viewType/bind в after_hooked_method, чтобы ПЕРЕБИТЬ proxy_tools
          (он забирает все pos >= rowCount)
        - click listener переустанавливаем в createView и onResume
        """
        if jclass is None or MethodHook is None or Integer is None:
            self.log("hook_proxy_list_ui: java/MethodHook unavailable")
            return

        try:
            ActivityCls = jclass("java.lang.Class").forName(
                "org.telegram.ui.ProxyListActivity"
            )
            AdapterCls = jclass("java.lang.Class").forName(
                "org.telegram.ui.ProxyListActivity$ListAdapter"
            )
        except Exception as e:
            self.log(f"ProxyListActivity classes: {e}")
            return

        extra = int(self._KUPU_EXTRA_ROWS)
        plugin_ref = weakref.ref(self)

        def _outer_field(obj, name):
            if get_private_field is not None:
                try:
                    return get_private_field(obj, name)
                except Exception:
                    pass
            return getattr(obj, name, None)

        def _our_index(adapter, pos, total_hint=None):
            """Последние `extra` позиций — наши. Не вызываем getItemCount из getItemCount."""
            try:
                pos = int(pos)
            except Exception:
                return None
            total = total_hint
            if total is None:
                try:
                    # безопасно: getItemCount не зовёт getItemViewType
                    total = int(adapter.getItemCount())
                except Exception:
                    try:
                        act = _outer_field(adapter, "this$0")
                        total = int(_outer_field(act, "rowCount") or 0) + extra
                    except Exception:
                        return None
            try:
                total = int(total)
            except Exception:
                return None
            start = total - extra
            if pos < start or pos >= total:
                return None
            return pos - start

        def _type_for_idx(idx):
            if idx in (0, 3):
                return 0  # ShadowSectionCell
            if idx == 1:
                return 3  # TextCheckCell
            return 1  # TextSettingsCell

        class GetItemCountHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    prev = int(param.getResult())
                    param.setResult(Integer(prev + extra))
                    p = plugin_ref()
                    if p is not None:
                        # prev = всё до нас (native + proxy_tools)
                        p._kupu_start_pos = prev
                        p._kupu_total_pos = prev + extra
                except Exception:
                    pass

        class GetItemViewTypeHook(MethodHook):
            # AFTER — чтобы перебить proxy_tools (он пишет type для всех pos>=rowCount)
            def after_hooked_method(self, param):
                try:
                    p = plugin_ref()
                    adapter = param.thisObject
                    pos = int(param.args[0])
                    total = getattr(p, "_kupu_total_pos", None) if p else None
                    idx = _our_index(adapter, pos, total_hint=total)
                    if idx is None and p is not None:
                        start = getattr(p, "_kupu_start_pos", None)
                        if start is not None and pos >= start and pos < start + extra:
                            idx = pos - start
                    if idx is None:
                        return
                    param.setResult(Integer(_type_for_idx(idx)))
                except Exception:
                    pass

        class GetItemIdHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    if Long is None:
                        return
                    p = plugin_ref()
                    adapter = param.thisObject
                    pos = int(param.args[0])
                    total = getattr(p, "_kupu_total_pos", None) if p else None
                    idx = _our_index(adapter, pos, total_hint=total)
                    if idx is None and p is not None:
                        start = getattr(p, "_kupu_start_pos", None)
                        if start is not None and start <= pos < start + extra:
                            idx = pos - start
                    if idx is not None:
                        param.setResult(Long(91000 + idx))
                except Exception:
                    pass

        class BindHook(MethodHook):
            def after_hooked_method(self, param):
                plugin = plugin_ref()
                if not plugin:
                    return
                try:
                    adapter = param.thisObject
                    pos = int(param.args[1])
                    total = getattr(plugin, "_kupu_total_pos", None)
                    idx = _our_index(adapter, pos, total_hint=total)
                    if idx is None:
                        start = getattr(plugin, "_kupu_start_pos", None)
                        if start is not None and start <= pos < start + extra:
                            idx = pos - start
                    if idx is None:
                        return

                    cell = param.args[0].itemView
                    Theme = jclass("org.telegram.ui.ActionBar.Theme")
                    try:
                        cell.setBackgroundColor(
                            Theme.getColor(Theme.key_windowBackgroundWhite)
                        )
                    except Exception:
                        pass

                    if idx == 1:
                        on = bool(plugin.get_setting("kupu_switch_on", False))
                        ok = False
                        for args in (( "KupuProxy", on, True), ("KupuProxy", on, False)):
                            try:
                                cell.setTextAndCheck(*args)
                                ok = True
                                break
                            except Exception:
                                continue
                        if not ok:
                            try:
                                cell.setText("KupuProxy", True)
                            except Exception as e:
                                plugin.log(f"bind check: {e}")
                    elif idx == 2:
                        for t in ("Сделать всё", "Сделать все", "KupuProxy: auto"):
                            try:
                                cell.setText(t, False)
                                break
                            except Exception:
                                try:
                                    cell.setText(t, True)
                                    break
                                except Exception:
                                    continue
                    # idx 0,3 — shadow, текста нет
                except Exception as e:
                    try:
                        plugin.log(f"BindHook: {e}")
                    except Exception:
                        pass

        def _install_click(act):
            plugin = plugin_ref()
            if not plugin or act is None:
                return
            try:
                lv = _outer_field(act, "listView")
                if lv is None:
                    return
                old_listener = _outer_field(lv, "onItemClickListener")
                try:
                    if old_listener is not None and "KupuProxyClick" in str(
                        type(old_listener).__name__
                    ) + str(old_listener.getClass().getName()):
                        # уже наш — но proxy_tools мог обернуть снаружи; перепоставим
                        pass
                except Exception:
                    pass

                ListenerBase = jclass(
                    "org.telegram.ui.Components.RecyclerListView$OnItemClickListener"
                )

                class KupuProxyClick(dynamic_proxy(ListenerBase)):
                    def __init__(self, old, pref, activity):
                        super().__init__()
                        self.old = old
                        self.pref = pref
                        self.activity = activity

                    def onItemClick(self, view, pos):
                        p = self.pref()
                        if not p:
                            return
                        try:
                            pos = int(pos)
                        except Exception:
                            if self.old:
                                self.old.onItemClick(view, pos)
                            return

                        idx = None
                        start = getattr(p, "_kupu_start_pos", None)
                        if start is not None and start <= pos < start + extra:
                            idx = pos - start
                        else:
                            ad = _outer_field(self.activity, "listAdapter")
                            if ad is not None:
                                idx = _our_index(ad, pos)

                        if idx is None:
                            if self.old:
                                self.old.onItemClick(view, pos)
                            return

                        if idx == 1:
                            cur = bool(p.get_setting("kupu_switch_on", False))
                            nxt = not cur
                            p._save_kupu_switch(nxt)
                            try:
                                view.setChecked(nxt)
                            except Exception:
                                pass
                            if nxt:
                                p._bulletin("KupuProxy: ищу рабочие…")
                                p._one_tap_setup(None)
                            else:
                                p._bulletin("KupuProxy выкл")
                        elif idx == 2:
                            p._save_kupu_switch(True)
                            p._one_tap_setup(None)
                        # shadow idx 0,3 — ignore

                # всегда снаружи, чтобы клики не глотал proxy_tools
                # (у PT нет else → клик по «чужим» extra pos просто ignored)
                lv.setOnItemClickListener(
                    KupuProxyClick(old_listener, plugin_ref, act)
                )
            except Exception as e:
                if plugin:
                    plugin.log(f"_install_click: {e}")

        class CreateViewHook(MethodHook):
            def after_hooked_method(self, param):
                plugin = plugin_ref()
                act = param.thisObject
                if not plugin:
                    return
                try:
                    _install_click(act)
                    # proxy_tools тоже вешает listener в createView —
                    # повторим через post, чтобы наш listener был снаружи
                    lv = _outer_field(act, "listView")
                    if lv is not None and dynamic_proxy is not None:
                        try:
                            Runnable = jclass("java.lang.Runnable")

                            class RebindClick(dynamic_proxy(Runnable)):
                                def run(self):
                                    try:
                                        _install_click(act)
                                    except Exception:
                                        pass

                            r = RebindClick()
                            lv.post(r)
                            try:
                                lv.postDelayed(r, 300)
                            except Exception:
                                pass
                        except Exception as e:
                            plugin.log(f"post click: {e}")
                    plugin.log("ProxyListActivity createView: click OK")
                except Exception as e:
                    plugin.log(f"CreateViewHook: {e}\n{traceback.format_exc()}")

        class ResumeHook(MethodHook):
            def after_hooked_method(self, param):
                try:
                    _install_click(param.thisObject)
                except Exception:
                    pass

        for m in ActivityCls.getDeclaredMethods():
            try:
                if m.getName() == "createView":
                    self.hook_method(m, CreateViewHook())
                elif m.getName() == "onResume":
                    self.hook_method(m, ResumeHook())
            except Exception as e:
                self.log(f"hook activity {m.getName()}: {e}")

        for m in AdapterCls.getDeclaredMethods():
            try:
                name = m.getName()
                if name == "getItemCount":
                    self.hook_method(m, GetItemCountHook())
                elif name == "getItemViewType":
                    self.hook_method(m, GetItemViewTypeHook())
                elif name == "getItemId":
                    self.hook_method(m, GetItemIdHook())
                elif name == "onBindViewHolder" and len(m.getParameterTypes()) == 2:
                    self.hook_method(m, BindHook())
            except Exception as e:
                self.log(f"hook adapter {name}: {e}")

        self.log("ProxyListActivity hooks v1.2.0 (after-bind, end rows)")

    def _save_kupu_switch(self, enabled: bool):
        try:
            if hasattr(self, "set_setting"):
                self.set_setting("kupu_switch_on", enabled)
        except Exception:
            pass
        try:
            from org.telegram.messenger import ApplicationLoader
            from android.content import Context

            ctx = ApplicationLoader.applicationContext
            prefs = ctx.getSharedPreferences("kupu_proxy_plugin", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("kupu_switch_on", bool(enabled)).apply()
        except Exception:
            pass

    def _safe_refresh_proxy_list(self):
        """Перерисовать список прокси (как proxy_tools._safe_refresh)."""

        def go():
            try:
                frag = get_last_fragment()
                if frag is None:
                    return
                name = str(frag.getClass().getName())
                if "ProxyList" not in name and "ProxySettings" not in name:
                    # всё равно пробуем adapter
                    pass
                for attr in ("listAdapter", "adapter"):
                    ad = self._pf(frag, attr)
                    if ad is not None and hasattr(ad, "notifyDataSetChanged"):
                        ad.notifyDataSetChanged()
                        break
                for meth in ("updateRows",):
                    m = getattr(frag, meth, None)
                    if callable(m):
                        try:
                            m()
                        except Exception:
                            pass
            except Exception as e:
                self.log(f"refresh list: {e}")

        run_on_ui_thread(go)

    def _one_tap_setup(self, dialog_id: Optional[int]):
        """
        Одной кнопкой:
        1) скачать списки  2) проверить  3) добавить рабочие
        4) удалить мёртвые  5) включить прокси + автопереключение
        6) выбрать лучший как текущий
        """
        if self._scanning:
            self._bulletin("Уже выполняется…")
            return

        self._scanning = True
        self._bulletin("KupuProxy: делаю всё… скан → добавление → автопереключение")

        def work():
            try:
                # 1-2 scan
                proxies = self._fetch_all()
                if not proxies:
                    self._finish_one_tap(dialog_id, False, "Не удалось скачать списки")
                    return

                max_check = self._int_setting("max_check", 120, 20, 500)
                workers = self._int_setting("workers", 24, 4, 48)
                timeout = self._float_setting("timeout_sec", 2.0, 0.8, 5.0)
                stop_when = self._int_setting("stop_when", 20, 0, 100)

                import random

                random.shuffle(proxies)
                batch = proxies[:max_check]

                results: List[Dict[str, Any]] = []

                def check_one(url: str) -> Optional[Dict[str, Any]]:
                    info = parse_proxy_url(url)
                    if not info:
                        return None
                    ping = tcp_ping(info["server"], info["port"], timeout)
                    if ping < 0:
                        return None
                    return {
                        "url": info["url"] if str(info["url"]).startswith("tg://") else url,
                        "server": info["server"],
                        "port": info["port"],
                        "secret": info["secret"],
                        "ping": ping,
                    }

                with ThreadPoolExecutor(max_workers=workers) as ex:
                    futs = [ex.submit(check_one, u) for u in batch]
                    for fut in as_completed(futs):
                        try:
                            item = fut.result()
                        except Exception:
                            item = None
                        if item:
                            results.append(item)
                            results.sort(key=lambda x: x["ping"])
                            if stop_when and len(results) >= stop_when:
                                break

                with self._lock:
                    self._results = results

                if not results:
                    self._finish_one_tap(
                        dialog_id, False, "Рабочих прокси не найдено. Попробуйте снова."
                    )
                    return

                # 3 add all working
                added, skipped, err = self._add_all_to_telegram(results)

                # 4 delete dead from saved list
                try:
                    checked, deleted, kept = self._delete_dead_from_telegram()
                except Exception as e:
                    self.log(f"del in one-tap: {e}")
                    deleted = 0

                # 5-6 enable + rotation + best current
                best = results[0]
                self._enable_proxy_system(best)
                self._save_kupu_switch(True)
                try:
                    self._safe_refresh_proxy_list()
                except Exception:
                    pass

                summary = (
                    f"✅ KupuProxy готово!\n"
                    f"• рабочих: **{len(results)}**\n"
                    f"• добавлено: **{added}** (уже были: {skipped})\n"
                    f"• удалено мёртвых: **{deleted}**\n"
                    f"• текущий: `{best['server']}:{best['port']}` · {best['ping']} ms\n"
                    f"• прокси **вкл**, автопереключение **вкл**\n\n"
                    f"Если один упадёт — Telegram переключит на другой."
                )
                self._finish_one_tap(dialog_id, True, summary)
            except Exception as e:
                self.log(traceback.format_exc())
                self._finish_one_tap(dialog_id, False, f"Ошибка: {e}")
            finally:
                self._scanning = False

        threading.Thread(target=work, daemon=True).start()

    def _finish_one_tap(self, dialog_id: Optional[int], ok: bool, text: str):
        # в чат не пишем — только bulletin / диалог
        self._bulletin(text[:200], error=not ok)

        def dlg():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    return
                b = AlertDialogBuilder(act)
                b.set_title("KupuProxy" if ok else "KupuProxy — ошибка")
                b.set_message(text)
                b.set_positive_button("OK", None)
                b.show()
            except Exception as e:
                self.log(f"one-tap dialog: {e}")

        run_on_ui_thread(dlg)

    def _enable_proxy_system(self, best: Dict[str, Any]):
        """Включить нативные: прокси + автопереключение, выбрать лучший."""
        done = threading.Event()
        err_box: List[Exception] = []
        self._ignore_native_trigger = True

        def go():
            try:
                from org.telegram.messenger import SharedConfig
                from org.telegram.tgnet import ConnectionsManager

                proxy = self._make_proxy_info(
                    best["server"], int(best["port"]), best.get("secret") or ""
                )
                try:
                    SharedConfig.addProxy(proxy)
                except Exception:
                    pass
                SharedConfig.currentProxy = proxy

                for name in ("proxyEnabled", "useProxySettings"):
                    try:
                        setattr(SharedConfig, name, True)
                    except Exception:
                        pass

                for name in (
                    "proxyRotationEnabled",
                    "proxyAutoSwitch",
                    "useProxyRotation",
                ):
                    try:
                        setattr(SharedConfig, name, True)
                    except Exception:
                        pass

                try:
                    SharedConfig.saveProxyList()
                except Exception:
                    pass

                try:
                    from org.telegram.messenger import ApplicationLoader
                    from android.content import Context

                    ctx = ApplicationLoader.applicationContext
                    prefs = ctx.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                    ed = prefs.edit()
                    ed.putBoolean("proxy_enabled", True)
                    for key in (
                        "proxyRotationEnabled",
                        "proxy_rotation_enabled",
                        "proxyAutoSwitch",
                        "auto_proxy_switch",
                    ):
                        ed.putBoolean(key, True)
                    ed.apply()
                except Exception as e:
                    self.log(f"prefs rotation: {e}")

                try:
                    ConnectionsManager.setProxySettings(
                        True,
                        proxy.address,
                        proxy.port,
                        proxy.username,
                        proxy.password,
                        proxy.secret,
                    )
                except Exception as e:
                    self.log(f"setProxySettings: {e}")

                self._last_rotation = True
                self._notify_proxy_changed()
            except Exception as e:
                err_box.append(e)
            finally:
                done.set()

        try:
            run_on_ui_thread(go)
            done.wait(30)
            if err_box:
                raise err_box[0]
        finally:
            # отпустить guard после записи prefs
            def unlock():
                self._ignore_native_trigger = False

            try:
                run_on_ui_thread(unlock)
            except Exception:
                self._ignore_native_trigger = False

    # endregion

    # region settings

    def create_settings(self) -> List[Any]:
        return [
            Header(text="KupuProxy"),
            Text(
                text=f"Версия плагина: {__version__}",
                subtext="Самообновление с GitHub · Kirillka645/KupuProxy",
            ),
            Switch(
                key="auto_update",
                text="Проверять обновления при запуске",
                default=True,
            ),
            Switch(
                key="notify_results",
                text="Показывать bulletin о результатах",
                default=True,
            ),
            Switch(
                key="auto_on_rotation",
                text="Также автоскан при «Автопереключение»",
                default=False,
            ),
            Text(
                text="Настройки → Прокси → внизу списка «KupuProxy»",
                subtext="Нативная TextCheckCell (как Proxy Tools)",
            ),
            Divider(),
            Header(text="Скан"),
            Input(
                key="max_check",
                text="Макс. прокси на проверку",
                default="120",
            ),
            Input(
                key="workers",
                text="Параллельных проверок",
                default="24",
            ),
            Input(
                key="timeout_sec",
                text="TCP timeout (сек)",
                default="2.0",
            ),
            Input(
                key="stop_when",
                text="Стоп после N рабочих (0 = все)",
                default="20",
            ),
            Divider(),
            Header(text="Как пользоваться"),
            Text(
                text="Настройки → Прокси → KupuProxy / Сделать всё",
                subtext="Строки встроены в список (TextCheckCell)",
            ),
            Text(text="`.kupu auto` — то же из чата"),
            Text(text="`.kupu scan` / `.kupu chat` / `.kupu add` / `.kupu del`"),
            Text(text="`.kupu list` / `.kupu use 1` / `.kupu update`"),
            Divider(),
            Text(
                text="Источники",
                subtext="SoliSpirit · Yagami200 · Kort · Argh94 · Surfboard · ALIILAPRO",
            ),
        ]

    # endregion

    # region UI helpers

    def _bulletin(self, text: str, error: bool = False):
        def go():
            try:
                if BulletinHelper is not None:
                    if error:
                        BulletinHelper.show_error(text)
                    else:
                        BulletinHelper.show_info(text)
                else:
                    self.log(text)
            except Exception as e:
                self.log(f"bulletin: {e} | {text}")

        run_on_ui_thread(go)

    def _toast(self, text: str):
        def go():
            try:
                from android.widget import Toast
                from client_utils import get_last_fragment

                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if act:
                    Toast.makeText(act, text, Toast.LENGTH_SHORT).show()
            except Exception as e:
                self.log(f"toast: {e}")

        run_on_ui_thread(go)

    def _reply(self, dialog_id: int, text: str):
        """Ответ в тот же чат (чанками); иначе bulletin."""
        did = int(dialog_id or 0)
        if not did:
            did = self._current_dialog_id()
        chunks = self._chunk_text(text, 3500)
        ok_any = False
        for chunk in chunks:
            if self._send_text(did, chunk):
                ok_any = True
                # небольшая пауза между чанками
                try:
                    time.sleep(0.15)
                except Exception:
                    pass
            else:
                break
        if not ok_any:
            self.log(f"_reply fail dialog_id={did}")
            self._bulletin(text[:180], error=True)

    @staticmethod
    def _chunk_text(text: str, limit: int = 3500) -> List[str]:
        if len(text) <= limit:
            return [text]
        parts: List[str] = []
        cur: List[str] = []
        size = 0
        for line in text.split("\n"):
            add = len(line) + 1
            if cur and size + add > limit:
                parts.append("\n".join(cur))
                cur = [line]
                size = add
            else:
                cur.append(line)
                size += add
        if cur:
            parts.append("\n".join(cur))
        return parts or [text[:limit]]

    def _current_dialog_id(self) -> int:
        """dialog_id открытого чата (ChatActivity)."""
        try:
            frag = get_last_fragment()
            if frag is None:
                return 0
            for name in ("getDialogId", "getDialogid"):
                try:
                    m = getattr(frag, name, None)
                    if callable(m):
                        v = int(m())
                        if v:
                            return v
                except Exception:
                    pass
            for field in ("dialog_id", "dialogId", "chatId", "mergedDialogId"):
                try:
                    if get_private_field is not None:
                        v = get_private_field(frag, field)
                        if v is not None and int(v) != 0:
                            return int(v)
                except Exception:
                    pass
                try:
                    v = getattr(frag, field, None)
                    if v is not None and int(v) != 0:
                        return int(v)
                except Exception:
                    pass
        except Exception as e:
            self.log(f"_current_dialog_id: {e}")
        return 0

    def _extract_dialog_id(self, params: Any, account: int = 0) -> int:
        """Достать peer/dialog_id из SendMessageParams."""
        candidates: List[int] = []

        def _as_int(v) -> Optional[int]:
            if v is None:
                return None
            try:
                # Java Long
                if hasattr(v, "longValue"):
                    return int(v.longValue())
            except Exception:
                pass
            try:
                return int(v)
            except Exception:
                return None

        for attr in ("peer", "dialogId", "dialog_id", "did", "chatId"):
            try:
                v = _as_int(getattr(params, attr, None))
                if v:
                    candidates.append(v)
            except Exception:
                pass
        for meth in ("getPeer", "getDialogId"):
            try:
                m = getattr(params, meth, None)
                if callable(m):
                    v = _as_int(m())
                    if v:
                        candidates.append(v)
            except Exception:
                pass

        # fragment fallback
        try:
            v = self._current_dialog_id()
            if v:
                candidates.append(v)
        except Exception:
            pass

        for c in candidates:
            if c != 0:
                return c
        return 0

    def _send_text(self, dialog_id: int, text: str) -> bool:
        """
        Отправить текст в чат.
        SDK: send_message({"peer": dialog_id, "message": text, ...})
        (позиционные args в новых SDK не работают).
        """
        did = int(dialog_id or 0)
        if not did:
            did = self._current_dialog_id()
        if not did:
            self.log("send_text: dialog_id=0")
            return False
        msg = str(text or "")
        if not msg:
            return False

        # 1) client_utils — правильный API (как TelegaDetector / proxy_tools)
        try:
            from client_utils import send_message

            payload = {
                "peer": did,
                "message": msg,
                "searchLinks": True,
                "notify": True,
            }
            try:
                send_message(payload)
                return True
            except Exception as e1:
                self.log(f"send_message(dict): {e1}")
            # старые сигнатуры
            for args in ((did, msg), (msg, did)):
                try:
                    send_message(*args)
                    return True
                except TypeError:
                    continue
                except Exception as e2:
                    self.log(f"send_message{args[:1]}: {e2}")
        except Exception as e:
            self.log(f"send_message import: {e}")

        # 2) SendMessagesHelper (fallback)
        try:
            from org.telegram.messenger import SendMessagesHelper, UserConfig

            account = int(UserConfig.selectedAccount)
            helper = SendMessagesHelper.getInstance(account)
            if jclass is not None:
                try:
                    Params = jclass(
                        "org.telegram.messenger.SendMessagesHelper$SendMessageParams"
                    )
                    # of(...) сигнатуры разные — пробуем минимальные
                    for args in (
                        (msg, did, None, None, None, True, None),
                        (msg, did),
                    ):
                        try:
                            p = Params.of(*args)
                            helper.sendMessage(p)
                            return True
                        except Exception:
                            continue
                except Exception as e3:
                    self.log(f"SendMessageParams: {e3}")
        except Exception as e:
            self.log(f"SendMessagesHelper: {e}")

        return False

    def _on_drawer_click(self, context: Dict[str, Any]):
        self._show_main_dialog()

    def _show_main_dialog(self):
        def go():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    self._bulletin("Нет activity", error=True)
                    return

                builder = AlertDialogBuilder(act)
                builder.set_title("KupuProxy")
                n = len(self._results)
                builder.set_message(
                    f"v{__version__}\n"
                    f"Рабочих в кэше: {n}\n\n"
                    f"Настройки → Прокси → прокрути вниз:\n"
                    f"• **KupuProxy** (switch)\n"
                    f"• **Сделать всё**\n\n"
                    f"Или `.kupu auto` в чате."
                )
                builder.set_positive_button(
                    "⚡ Auto", lambda b, w: self._one_tap_setup(None)
                )
                builder.set_neutral_button("Скан", lambda b, w: self._start_scan(None))
                builder.set_negative_button("Топ", lambda b, w: self._show_top_dialog())
                builder.show()
            except Exception as e:
                self.log(traceback.format_exc())
                self._bulletin(f"UI error: {e}", error=True)

        run_on_ui_thread(go)

    def _show_top_dialog(self):
        def go():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    return
                with self._lock:
                    items = list(self._results[:15])
                if not items:
                    self._bulletin("Сначала сделайте .kupu scan", error=True)
                    return

                labels = [
                    f"{i+1}. {p['server']}:{p['port']}  ·  {p['ping']} ms"
                    for i, p in enumerate(items)
                ]
                builder = AlertDialogBuilder(act)
                builder.set_title("Рабочие прокси")
                # setItems if available
                try:
                    builder.set_items(
                        labels,
                        lambda b, which: self._apply_proxy(items[which]["url"]),
                    )
                except Exception:
                    builder.set_message("\n".join(labels[:10]))
                    builder.set_positive_button(
                        "Подключить #1",
                        lambda b, w: self._apply_proxy(items[0]["url"]),
                    )
                builder.set_negative_button("Закрыть", None)
                builder.show()
            except Exception as e:
                self.log(traceback.format_exc())
                self._bulletin(str(e), error=True)

        run_on_ui_thread(go)

    # endregion

    # region commands

    def on_send_message_hook(self, account: int, params: Any):
        from base_plugin import HookResult, HookStrategy

        try:
            msg = getattr(params, "message", None)
            if not isinstance(msg, str):
                return HookResult()
            text = msg.strip()
            if not text.lower().startswith(".kupu"):
                return HookResult()

            dialog_id = self._extract_dialog_id(params, account)
            self.log(f"cmd={text!r} dialog_id={dialog_id} account={account}")

            threading.Thread(
                target=self._handle_command,
                args=(text, int(dialog_id or 0), int(account or 0)),
                daemon=True,
            ).start()

            return HookResult(strategy=HookStrategy.CANCEL)
        except Exception as e:
            self.log(f"cmd hook: {e}")
            return HookResult()

    def _handle_command(self, text: str, dialog_id: int, account: int = 0):
        parts = text.strip().split()
        cmd = parts[1].lower() if len(parts) > 1 else "help"
        if not dialog_id:
            dialog_id = self._current_dialog_id()
            self.log(f"handle_command: resolved dialog_id={dialog_id}")

        # В чат пишем ТОЛЬКО по `.kupu chat`. Остальное — bulletin.
        if cmd in ("help", "h", "?"):
            self._bulletin(
                f"KupuProxy v{__version__}: .kupu scan / chat / add / del / auto / use N"
            )
        elif cmd in ("auto", "all", "go", "setup"):
            self._one_tap_setup(None)
        elif cmd == "scan":
            self._start_scan(None)
        elif cmd == "chat":
            self._cmd_chat(dialog_id)
        elif cmd == "add":
            self._cmd_add()
        elif cmd == "del":
            self._cmd_del()
        elif cmd in ("list", "top"):
            self._cmd_list()
        elif cmd == "use":
            n = int(parts[2]) if len(parts) > 2 and parts[2].isdigit() else 1
            self._cmd_use(n)
        elif cmd == "update":
            self._do_self_update(force=True, dialog_id=None)
        elif cmd == "menu":
            self._show_main_dialog()
        else:
            self._bulletin(f"Неизвестно: {cmd}. .kupu help", error=True)

    @staticmethod
    def _proxy_tme_url(p: Dict[str, Any]) -> str:
        """https://t.me/proxy?server=...&port=...&secret=..."""
        url = str(p.get("url") or "")
        if "t.me/proxy?" in url or "tg://proxy?" in url:
            q = url.split("?", 1)[1] if "?" in url else ""
            return f"https://t.me/proxy?{q}" if q else url
        server = p.get("server") or ""
        port = p.get("port") or ""
        secret = p.get("secret") or ""
        return f"https://t.me/proxy?server={server}&port={port}&secret={secret}"

    def _cmd_list(self):
        with self._lock:
            items = list(self._results[:20])
        if not items:
            self._bulletin("Пусто. Сначала .kupu scan", error=True)
            return
        lines = [
            f"{i+1}. {p['server']}:{p['port']} — {p['ping']} ms"
            for i, p in enumerate(items)
        ]
        self._bulletin(f"Рабочие: {len(items)}. .kupu chat — ссылки в чат")
        try:
            frag = get_last_fragment()
            act = frag.getParentActivity() if frag else None
            if act:
                b = AlertDialogBuilder(act)
                b.set_title(f"Рабочие ({len(items)})")
                b.set_message("\n".join(lines[:15]))
                b.set_positive_button("OK", None)
                b.show()
        except Exception:
            pass

    def _cmd_chat(self, dialog_id: int):
        """
        Единственная команда, которая пишет в чат.
        1.https://t.me/proxy?server=...&port=...&secret=... (36 ms)
        2....
        """
        did = int(dialog_id or 0) or self._current_dialog_id()
        if not did:
            self._bulletin(
                "Не вижу chat id. Открой чат и снова: .kupu chat",
                error=True,
            )
            return

        with self._lock:
            items = list(self._results)
        if not items:
            self._bulletin("Пусто. Сначала .kupu scan", error=True)
            return

        lines = []
        for i, p in enumerate(items):
            url = self._proxy_tme_url(p)
            ping = p.get("ping", "?")
            lines.append(f"{i + 1}.{url} ({ping} ms)")

        batch: List[str] = []
        sent = 0
        for line in lines:
            batch.append(line)
            if len(batch) >= 20:
                if not self._send_text(did, "\n".join(batch)):
                    self._bulletin("Не удалось отправить в чат", error=True)
                    return
                sent += 1
                batch = []
                try:
                    time.sleep(0.25)
                except Exception:
                    pass
        if batch:
            if not self._send_text(did, "\n".join(batch)):
                self._bulletin("Не удалось отправить в чат", error=True)
                return
            sent += 1

        self._bulletin(f"В чат: {len(items)} ссылок")
        self.log(f"_cmd_chat did={did} items={len(items)} msgs={sent}")

    def _cmd_add(self):
        with self._lock:
            items = list(self._results)
        if not items:
            self._bulletin("Пусто. Сначала .kupu scan", error=True)
            return

        def work():
            try:
                added, skipped, err = self._add_all_to_telegram(items)
                msg = f"Добавлено: {added}, уже были: {skipped}"
                if err:
                    msg += f", ошибки: {err}"
                self._bulletin(msg)
            except Exception as e:
                self.log(traceback.format_exc())
                self._bulletin(f"Ошибка add: {e}", error=True)

        self._bulletin(f"Добавляю {len(items)} в список прокси…")
        threading.Thread(target=work, daemon=True).start()

    def _cmd_del(self):
        def work():
            try:
                checked, deleted, kept = self._delete_dead_from_telegram()
                self._bulletin(
                    f"Проверено {checked}, удалено {deleted}, осталось {kept}"
                )
            except Exception as e:
                self.log(traceback.format_exc())
                self._bulletin(f"Ошибка del: {e}", error=True)

        self._bulletin("Удаляю мёртвые из списка прокси…")
        threading.Thread(target=work, daemon=True).start()

    def _cmd_use(self, n: int):
        with self._lock:
            items = list(self._results)
        if n < 1 or n > len(items):
            self._bulletin(f"Нет прокси №{n}. Всего: {len(items)}", error=True)
            return
        p = items[n - 1]
        self._apply_proxy(p["url"])
        self._bulletin(f"Подключаю #{n} {p['server']}:{p['port']} · {p['ping']} ms")

    # endregion

    # region scan

    def _start_scan(self, dialog_id: Optional[int] = None):
        # dialog_id игнорируется: в чат не пишем (только .kupu chat)
        if self._scanning:
            self._bulletin("Уже сканирую…")
            return
        self._scanning = True
        self._bulletin("KupuProxy: сканирование…")

        def work():
            try:
                proxies = self._fetch_all()
                if not proxies:
                    self._scanning = False
                    self._bulletin(
                        "Не удалось скачать списки (сеть / блокировка)",
                        error=True,
                    )
                    return

                max_check = self._int_setting("max_check", 120, 20, 500)
                workers = self._int_setting("workers", 24, 4, 48)
                timeout = self._float_setting("timeout_sec", 2.0, 0.8, 5.0)
                stop_when = self._int_setting("stop_when", 20, 0, 100)

                import random

                random.shuffle(proxies)
                batch = proxies[:max_check]

                results: List[Dict[str, Any]] = []
                checked = 0
                stop = False

                def check_one(url: str) -> Optional[Dict[str, Any]]:
                    info = parse_proxy_url(url)
                    if not info:
                        return None
                    ping = tcp_ping(info["server"], info["port"], timeout)
                    if ping < 0:
                        return None
                    return {
                        "url": info["url"] if info["url"].startswith("tg://") else url,
                        "server": info["server"],
                        "port": info["port"],
                        "secret": info["secret"],
                        "ping": ping,
                    }

                with ThreadPoolExecutor(max_workers=workers) as ex:
                    futs = {ex.submit(check_one, u): u for u in batch}
                    for fut in as_completed(futs):
                        if stop:
                            break
                        checked += 1
                        try:
                            item = fut.result()
                        except Exception:
                            item = None
                        if item:
                            results.append(item)
                            results.sort(key=lambda x: x["ping"])
                            if stop_when and len(results) >= stop_when:
                                stop = True

                with self._lock:
                    self._results = results

                self._bulletin(
                    f"KupuProxy: {len(results)} рабочих (проверено {checked}). "
                    f".kupu chat — ссылки в чат"
                    if results
                    else f"KupuProxy: рабочих нет (проверено {checked})",
                    error=not results,
                )
            except Exception as e:
                self.log(traceback.format_exc())
                self._bulletin(str(e), error=True)
            finally:
                self._scanning = False

        threading.Thread(target=work, daemon=True).start()

    def _fetch_all(self) -> List[str]:
        all_links: List[str] = []
        seen = set()
        for src in SOURCES:
            for url in src["urls"]:
                try:
                    body = http_get(url)
                    links = parse_proxy_links(body)
                    if not links:
                        continue
                    for L in links:
                        if L not in seen:
                            seen.add(L)
                            all_links.append(L)
                    self.log(f"{src['name']}: +{len(links)} from {url}")
                    break  # next source
                except Exception as e:
                    self.log(f"fetch fail {url}: {e}")
                    continue
        return all_links

    def _int_setting(self, key: str, default: int, lo: int, hi: int) -> int:
        try:
            v = int(str(self.get_setting(key, default)).strip())
            return max(lo, min(hi, v))
        except Exception:
            return default

    def _float_setting(self, key: str, default: float, lo: float, hi: float) -> float:
        try:
            v = float(str(self.get_setting(key, default)).replace(",", ".").strip())
            return max(lo, min(hi, v))
        except Exception:
            return default

    # endregion

    # region apply proxy

    def _apply_proxy(self, url: str):
        if not url.startswith("tg://"):
            if url.startswith("https://t.me/proxy?"):
                url = "tg://proxy?" + url.split("?", 1)[1]
            else:
                self._bulletin("Некорректная ссылка", error=True)
                return

        def go():
            try:
                from android.content import Intent
                from android.net import Uri

                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    self._bulletin("Нет activity", error=True)
                    return

                # Native Telegram proxy dialog
                intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                act.startActivity(intent)
                self._bulletin("Открываю прокси в Telegram…")
            except Exception as e:
                self.log(traceback.format_exc())
                # fallback SharedConfig
                try:
                    self._apply_shared_config(url)
                except Exception as e2:
                    self._bulletin(f"Не удалось: {e2}", error=True)

        run_on_ui_thread(go)

    def _apply_shared_config(self, url: str):
        info = parse_proxy_url(url)
        if not info:
            raise RuntimeError("parse failed")
        from org.telegram.messenger import SharedConfig
        from org.telegram.tgnet import ConnectionsManager
        from org.telegram.messenger import NotificationCenter

        proxy = self._make_proxy_info(info["server"], int(info["port"]), info["secret"])
        SharedConfig.addProxy(proxy)
        SharedConfig.currentProxy = proxy
        SharedConfig.saveProxyList()
        try:
            SharedConfig.proxyEnabled = True
        except Exception:
            pass
        try:
            ConnectionsManager.setProxySettings(
                True,
                proxy.address,
                proxy.port,
                proxy.username,
                proxy.password,
                proxy.secret,
            )
        except Exception as e:
            self.log(f"setProxySettings: {e}")
        self._notify_proxy_changed()
        self._bulletin(f"Прокси {info['server']}:{info['port']} включён")

    def _make_proxy_info(self, server: str, port: int, secret: str):
        from org.telegram.messenger import SharedConfig

        # ProxyInfo(address, port, username, password, secret)
        try:
            return SharedConfig.ProxyInfo(server, int(port), "", "", secret or "")
        except Exception:
            # older constructors
            p = SharedConfig.ProxyInfo()
            p.address = server
            p.port = int(port)
            p.username = ""
            p.password = ""
            p.secret = secret or ""
            return p

    def _proxy_key(self, address: str, port: int, secret: str = "") -> str:
        return f"{(address or '').lower()}:{(port or 0)}:{(secret or '')}"

    def _iter_proxy_list(self):
        from org.telegram.messenger import SharedConfig

        pl = SharedConfig.proxyList
        items = []
        try:
            # ArrayList
            n = pl.size()
            for i in range(n):
                items.append(pl.get(i))
        except Exception:
            try:
                for p in pl:
                    items.append(p)
            except Exception as e:
                self.log(f"proxyList iterate: {e}")
        return items

    def _existing_proxy_keys(self) -> set:
        keys = set()
        for p in self._iter_proxy_list():
            try:
                addr = getattr(p, "address", None) or ""
                port = int(getattr(p, "port", 0) or 0)
                secret = getattr(p, "secret", None) or ""
                keys.add(self._proxy_key(addr, port, secret))
            except Exception:
                continue
        return keys

    def _add_all_to_telegram(self, items: List[Dict[str, Any]]) -> Tuple[int, int, int]:
        """Returns (added, skipped, errors)."""
        from org.telegram.messenger import SharedConfig

        existing = self._existing_proxy_keys()
        added = 0
        skipped = 0
        errors = 0

        def do_add():
            nonlocal added, skipped, errors
            for it in items:
                try:
                    server = it["server"]
                    port = int(it["port"])
                    secret = it.get("secret") or ""
                    key = self._proxy_key(server, port, secret)
                    if key in existing:
                        skipped += 1
                        continue
                    proxy = self._make_proxy_info(server, port, secret)
                    SharedConfig.addProxy(proxy)
                    existing.add(key)
                    added += 1
                except Exception as e:
                    errors += 1
                    self.log(f"addProxy fail: {e}")
            try:
                SharedConfig.saveProxyList()
            except Exception as e:
                self.log(f"saveProxyList: {e}")
            self._notify_proxy_changed()

        # SharedConfig usually must be touched on UI thread
        done = threading.Event()
        err_box: List[Exception] = []

        def go():
            try:
                do_add()
            except Exception as e:
                err_box.append(e)
            finally:
                done.set()

        run_on_ui_thread(go)
        done.wait(60)
        if err_box:
            raise err_box[0]
        return added, skipped, errors

    def _delete_dead_from_telegram(self) -> Tuple[int, int, int]:
        """TCP-check all saved proxies; delete dead. Returns (checked, deleted, kept)."""
        from org.telegram.messenger import SharedConfig

        timeout = self._float_setting("timeout_sec", 2.0, 0.8, 5.0)
        proxies = self._iter_proxy_list()
        checked = 0
        dead = []

        for p in proxies:
            try:
                addr = getattr(p, "address", None) or ""
                port = int(getattr(p, "port", 0) or 0)
                if not addr or port <= 0:
                    continue
                checked += 1
                ping = tcp_ping(addr, port, timeout)
                if ping < 0:
                    dead.append(p)
            except Exception as e:
                self.log(f"check saved proxy: {e}")

        deleted = 0
        done = threading.Event()
        err_box: List[Exception] = []

        def go():
            nonlocal deleted
            try:
                for p in dead:
                    try:
                        SharedConfig.deleteProxy(p)
                        deleted += 1
                    except Exception:
                        # fallback remove + save
                        try:
                            SharedConfig.proxyList.remove(p)
                            deleted += 1
                        except Exception as e2:
                            self.log(f"deleteProxy: {e2}")
                try:
                    SharedConfig.saveProxyList()
                except Exception:
                    pass
                self._notify_proxy_changed()
            except Exception as e:
                err_box.append(e)
            finally:
                done.set()

        run_on_ui_thread(go)
        done.wait(60)
        if err_box:
            raise err_box[0]
        kept = max(0, checked - deleted)
        return checked, deleted, kept

    def _notify_proxy_changed(self):
        try:
            from org.telegram.messenger import NotificationCenter

            NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.proxySettingsChanged
            )
        except Exception as e:
            self.log(f"proxySettingsChanged: {e}")

    # endregion

    # region self-update

    def _bg_check_update(self):
        time.sleep(3)
        try:
            self._do_self_update(force=False, dialog_id=None)
        except Exception as e:
            self.log(f"auto update: {e}")

    def _do_self_update(self, force: bool = False, dialog_id: Optional[int] = None):
        # в чат не пишем — только bulletin
        try:
            remote_ver, source_url, body = self._fetch_remote_plugin()
            if not remote_ver:
                if force:
                    self._bulletin("Не удалось проверить обновление", error=True)
                return

            if not is_newer(__version__, remote_ver):
                if force:
                    self._bulletin(f"Уже актуальная версия v{__version__}")
                return

            path = os.path.abspath(__file__)
            candidates = [path]
            if path.endswith(".py"):
                candidates.append(path[:-3] + ".plugin")
            if path.endswith(".plugin"):
                candidates.append(path[:-7] + ".py")

            written = None
            for p in candidates:
                try:
                    if os.path.isfile(p) or p == path:
                        with open(p, "w", encoding="utf-8") as f:
                            f.write(body)
                        written = p
                        break
                except Exception as e:
                    self.log(f"write {p}: {e}")

            if not written:
                dl = "/storage/emulated/0/Download/kupu_proxy.plugin"
                with open(dl, "w", encoding="utf-8") as f:
                    f.write(body)
                written = dl

            self._bulletin(f"KupuProxy → v{remote_ver}. Перезапустите клиент.")
            self._show_update_done_dialog(remote_ver, written)
            self.log(f"updated from {source_url}")
        except Exception as e:
            self.log(traceback.format_exc())
            if force:
                self._bulletin(str(e), error=True)

    def _show_update_done_dialog(self, ver: str, path: str):
        def go():
            try:
                frag = get_last_fragment()
                act = frag.getParentActivity() if frag else None
                if not act:
                    return
                b = AlertDialogBuilder(act)
                b.set_title(f"Обновлено до v{ver}")
                b.set_message(
                    f"Плагин записан:\n{path}\n\n"
                    f"Перезапустите exteraGram, чтобы загрузить новую версию."
                )
                b.set_positive_button("OK", None)
                b.show()
            except Exception as e:
                self.log(f"update dialog: {e}")

        run_on_ui_thread(go)

    def _fetch_remote_plugin(self) -> Tuple[Optional[str], Optional[str], Optional[str]]:
        # 1) Try GitHub release asset named kupu_proxy.py / .plugin
        try:
            meta = http_get(UPDATE_URL, timeout=12)
            data = json.loads(meta)
            tag = (data.get("tag_name") or "").lstrip("v")
            # plugin version is independent of app version; look into assets or body
            assets = data.get("assets") or []
            for a in assets:
                name = a.get("name") or ""
                if name in ("kupu_proxy.py", "kupu_proxy.plugin", "KupuProxy.plugin"):
                    url = a.get("browser_download_url")
                    if url:
                        body = http_get(url, timeout=30)
                        m = VERSION_RE.search(body)
                        ver = m.group(1) if m else tag or None
                        return ver, url, body
        except Exception as e:
            self.log(f"release api: {e}")

        # 2) Raw mirrors of plugin source in repo
        for url in RAW_PLUGIN_MIRRORS:
            try:
                body = http_get(url, timeout=25)
                if "KupuProxyPlugin" not in body and "__id__" not in body:
                    continue
                m = VERSION_RE.search(body)
                if not m:
                    continue
                return m.group(1), url, body
            except Exception as e:
                self.log(f"raw fail {url}: {e}")
                continue
        return None, None, None

    # endregion
